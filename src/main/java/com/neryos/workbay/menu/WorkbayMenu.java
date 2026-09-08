package com.neryos.workbay.menu;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.content.assay.AssayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.host.HostChecks;
import com.neryos.workbay.host.HostResult;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBMenus;
import com.neryos.workbay.network.SnapshotPacket;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.BayHosting;
import com.neryos.workbay.world.FaceConfig;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The Workbay's menu, behind all three screens. SPEC.md §4.
 *
 * <p><b>It has no slots at all.</b> The screen shows no player inventory: a machine enters a bay by
 * clicking the bay slot while holding it, not by dragging from a grid. That also means the vanilla
 * slot-sync path carries nothing, and everything the screens draw arrives as one
 * {@link WorkbaySnapshot}.
 *
 * <p>SPEC.md §4 has switching bays re-open a fresh menu. That was written for a menu with real
 * slots bound to a bay; with none, there is nothing to re-bind, so the selection is an action on
 * this menu instead — which also sidesteps the cursor recentring that
 * {@code shouldTriggerClientSideContainerClosingOnOpen} exists to prevent.
 */
public class WorkbayMenu extends AbstractContainerMenu {

    /** Rebuilt at most this often. It is a status screen, not an animation. */
    private static final int REFRESH_TICKS = 5;

    private final Player player;

    @Nullable
    private final WorkbayBlockEntity workbay;

    private WorkbaySnapshot snapshot;
    private WorkbaySnapshot sent = WorkbaySnapshot.EMPTY;
    private int selectedBay;
    private int cooldown;

    /** Client side: the snapshot arrives in the menu-open buffer, so frame 1 is already correct. */
    public WorkbayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, WorkbaySnapshot.STREAM_CODEC.decode(buffer));
    }

    public WorkbayMenu(int containerId, Inventory inventory, @Nullable WorkbayBlockEntity workbay,
        WorkbaySnapshot initial) {
        super(WBMenus.WORKBAY.get(), containerId);
        this.player = inventory.player;
        this.workbay = workbay;
        this.snapshot = initial;
        this.selectedBay = initial.selectedBay();
    }

    public WorkbaySnapshot snapshot() {
        return snapshot;
    }

    /** Applied optimistically on the client before the packet goes out, so clicks feel immediate. */
    public void applySnapshot(WorkbaySnapshot updated) {
        this.snapshot = updated;
        this.selectedBay = updated.selectedBay();
    }

    public int selectedBay() {
        return selectedBay;
    }

    public void setSelectedBayClientSide(int bay) {
        this.selectedBay = bay;
    }

    /**
     * A menu with no slots still has to refuse a stack the player somehow drags into it, and
     * returning anything but EMPTY here is how vanilla ends up looping forever.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return workbay == null
            || (!workbay.isRemoved() && player.distanceToSqr(
                workbay.getBlockPos().getX() + 0.5,
                workbay.getBlockPos().getY() + 0.5,
                workbay.getBlockPos().getZ() + 0.5) <= 64.0);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (workbay == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = REFRESH_TICKS;
        snapshot = build(workbay, serverPlayer, selectedBay);
        if (!Objects.equals(snapshot, sent)) {
            sent = snapshot;
            PacketDistributor.sendToPlayer(serverPlayer, new SnapshotPacket(containerId, snapshot));
        }
    }

    /** Forces the next tick to send, after an action the player is waiting to see the result of. */
    private void refreshNow() {
        cooldown = 0;
    }

    // --------------------------------------------------------------- actions

    /**
     * Every action from every screen. Guarded once, here, rather than in each handler: a spectator
     * or a player whose menu has moved on must not be able to eject somebody's machine.
     */
    public void act(WorkbayAction action, long arg, Optional<UUID> linkId) {
        act(action, arg, linkId, Optional.empty(), false);
    }

    /**
     * @param back the control was right-clicked, so every cycle here steps to the previous value
     *             instead of the next one. One flag rather than a mirrored action per control:
     *             the guards above are the reason these are actions at all, and they must not be
     *             written out twice.
     */
    public void act(WorkbayAction action, long arg, Optional<UUID> linkId,
        Optional<String> text, boolean back) {
        if (workbay == null || !(player instanceof ServerPlayer serverPlayer)
            || player.isSpectator() || !stillValid(player)) {
            return;
        }
        WorkbayRecord record = workbay.record().orElse(null);
        if (record == null) {
            return;
        }
        // The lock is checked once, for every action that changes something. Selecting a bay only
        // changes what this player is looking at.
        if (action != WorkbayAction.SELECT_BAY && action != WorkbayAction.TOGGLE_LOCK
            && refused(serverPlayer, record)) {
            return;
        }
        switch (action) {
            case SELECT_BAY -> selectedBay = (int) Math.clamp(arg, 0, BayGeometry.MAX_BAYS - 1);
            case RACK -> rack(serverPlayer, record);
            case EJECT -> eject(serverPlayer, record);
            case TOGGLE_LOCK -> toggleLock(serverPlayer, record);
            case CYCLE_FACE -> cycleFace(serverPlayer, record, (int) arg, back);
            case PAIR -> pair(serverPlayer, record);
            case INSTALL_UPGRADE -> install(serverPlayer, record, (int) arg);
            case PASTE_BAY -> pasteBay(serverPlayer, record, arg);
            case LINK_FLIP_MODE -> editLink(linkId, link -> link.withMode(link.mode().flip()));
            case LINK_CYCLE_RESOURCE ->
                editLink(linkId, link -> link.withResource(link.resource().step(back)));
            case LINK_TOGGLE_ENABLED -> editLink(linkId, link -> link.withEnabled(!link.enabled()));
            case LINK_REMOVE -> linkId.ifPresent(workbay::removeBus);
            case LINK_CYCLE_TARGET_FACE -> editLink(linkId,
                link -> link.withTargetFace(BusConfig.stepFace(link.targetFace(), back)));
            case SET_FILTER -> editLink(linkId, link -> link.withFilter(
                link.filter().with((int) (arg >>> 32), filterEntry(link, (int) arg))));
            case TOGGLE_FILTER_DENY -> editLink(linkId,
                link -> link.withFilter(link.filter().withDeny(!link.filter().deny())));
            case SET_BAY_NAME -> editBay(serverPlayer, record,
                bay -> bay.withName(text.orElse("").strip()));
            // Capped where every other player-typed name in this mod is capped. The packet already
            // limits the string to 64 bytes on the wire; this is the second half of that, because
            // a name is drawn on a fifty-six pixel column and stored in the registry forever.
            case SET_LINK_NAME -> editLink(linkId,
                link -> link.withName(text.orElse("").strip()));
            case CYCLE_REDSTONE -> editBay(serverPlayer, record,
                bay -> bay.withRedstone(bay.redstone().step(back)));
            case SET_SKIM -> setSkim(serverPlayer, record, back);
            // Bay View is withdrawn (OPEN_ISSUES #35) and no button sends this any more. The case
            // stays because the action is a network enum and dropping a constant renumbers the
            // rest; the menu itself is untouched and still tested, so bringing it back is one
            // button and this line.
            case OPEN_BAY_VIEW -> { }
            case ENTER_ROOM -> {
                // Closing first, for the same reason a bay visit does: the player is about to be
                // somewhere this menu's stillValid would refuse, and a screen left open over a
                // teleport is how you get a ghost.
                serverPlayer.closeContainer();
                com.neryos.workbay.world.RoomVisit.enter(serverPlayer, record, (int) arg);
            }
            case TOGGLE_ROOM_ANCHOR -> toggleRoomAnchor(serverPlayer, record, (int) arg);
            case CYCLE_ROOM_BIOME -> cycleRoomBiome(serverPlayer, record, (int) arg);
            case CYCLE_ROOM_COLOUR -> cycleRoomColour(serverPlayer, record, (int) arg, back);
            // The biome travels as its own id, never as a position in the list: the picker is
            // searchable, so the row a player clicked is a position in a *filtered* list and the
            // two ends would disagree the moment anybody typed. The colour below still packs an
            // ordinal, because eleven colours are ours and cannot be filtered or added to.
            case SET_ROOM_BIOME -> setRoomBiome(serverPlayer, record, (int) (arg & 0xFFFF),
                text.orElse(""));
            case SET_ROOM_COLOUR -> setRoomColour(serverPlayer, record, (int) (arg & 0xFFFF),
                (int) (arg >> 16));
            case INVITE_ROOM_GUEST -> inviteGuest(serverPlayer, record, (int) arg,
                text.orElse("").strip());
            case CYCLE_ROOM_GUEST -> editGuest(serverPlayer, record, (int) (arg & 0xFFFF),
                (int) (arg >> 16), true);
            case REMOVE_ROOM_GUEST -> editGuest(serverPlayer, record, (int) (arg & 0xFFFF),
                (int) (arg >> 16), false);
            case ENTER_BAY -> {
                // The screen where the player stands first, and the trip only if that cannot
                // happen: a host with the mixins off (SPEC.md §0), a client with them off (arg),
                // or a machine that opens no screen at all. Entering is the fallback, not the
                // way in, and it is deliberately still here — it is the only path that needs
                // nothing patched.
                if (arg == 1 && com.neryos.workbay.remote.RemoteConfig.remoteScreensEnabled()
                    && openRemote(serverPlayer, record)) {
                    return;
                }
                // Closing first: the player is about to be somewhere this menu's stillValid would
                // refuse anyway, and a screen left open over a teleport is how you get a ghost.
                serverPlayer.closeContainer();
                if (!com.neryos.workbay.world.BayVisit.enter(serverPlayer, record, selectedBay,
                    workbay == null ? null : net.minecraft.core.GlobalPos.of(
                        serverPlayer.level().dimension(), workbay.getBlockPos()))) {
                    serverPlayer.displayClientMessage(
                        com.neryos.workbay.WorkbayLang.message("bay_enter_failed"), true);
                }
            }
            case CREATE_INTERNAL_LINK -> createInternalLink(serverPlayer, record, (int) arg);
            case LINK_ASSIGN_BAY -> {
                int bay = (int) arg;
                if (bay >= 0 && bay < record.bayCapacity()) {
                    editLink(linkId, link -> link.withBay(bay));
                }
            }
            case LINK_CYCLE_TARGET_BAY -> editLink(linkId, link -> link.internal()
                ? link.withTarget(GlobalPos.of(WorkbayDimensions.BACKSHOP,
                    BayGeometry.machinePos(record.bayColumn(),
                        otherBay(record, link.bay(), currentTargetBay(record, link), back))))
                : link);
        }
        // This menu has no slots (SPEC.md §4), so the vanilla per-tick sync that normally covers
        // an open menu's slots never touches the player's own inventory while it is open. RACK
        // shrinks the held stack, EJECT and PAIR change it, and none of that reached the client on
        // its own — reported from play as a "shadow" item: the client kept showing what the item
        // looked like before the action, and acting on that stale copy is exactly how an item
        // sync bug turns into a duplication or loss bug. inventoryMenu.broadcastChanges() is a
        // normal AbstractContainerMenu, wired to the player's real inventory, and calling it here
        // forces the same slot diff-and-sync every other menu gets for free once a tick.
        serverPlayer.inventoryMenu.broadcastChanges();
        refreshNow();
    }

    /**
     * A registry id, the way every vanilla packet carries one: the network id plus one, so zero can
     * mean "clear this slot". Which registry is the link's own resource — an item link filters
     * items, a fluid link fluids — and an id this server does not know clears the slot rather than
     * being trusted into an array index.
     */
    private static Optional<ResourceLocation> filterEntry(BusConfig link, int plusOne) {
        if (plusOne <= 0) {
            return Optional.empty();
        }
        return switch (link.resource()) {
            case ITEM -> {
                var item = BuiltInRegistries.ITEM.byId(plusOne - 1);
                yield item == net.minecraft.world.item.Items.AIR
                    ? Optional.empty() : Optional.ofNullable(BuiltInRegistries.ITEM.getKey(item));
            }
            case FLUID -> {
                var fluid = BuiltInRegistries.FLUID.byId(plusOne - 1);
                yield fluid == net.minecraft.world.level.material.Fluids.EMPTY
                    ? Optional.empty() : Optional.ofNullable(BuiltInRegistries.FLUID.getKey(fluid));
            }
            // Nothing to match on, so nothing to put in a slot. SPEC.md §5. A chemical has no
            // item to drag from either, so it joins energy rather than getting a fifth entry kind.
            case ENERGY, CHEMICAL -> Optional.empty();
        };
    }

    /**
     * Opens the selected bay's machine where the player is standing. SPEC.md §0.
     *
     * <p>False for an empty bay and for a machine with no screen of its own, both of which fall
     * through to the trip into the bay rather than leaving the player with a button that did
     * nothing.
     */
    private boolean openRemote(ServerPlayer viewer, WorkbayRecord record) {
        ServerLevel backshop = viewer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null || selectedBay >= record.bayCapacity()
            || record.bay(selectedBay).hosted().isEmpty()) {
            return false;
        }
        // Closing the machine's screen comes back here, on the same bay: the button that opened it
        // lives on this screen, so Escape means "back", not "put everything away". Guarded on the
        // Workbay still being there, because the trip is not instant and a block can be broken.
        WorkbayBlockEntity origin = workbay;
        int bay = selectedBay;
        return com.neryos.workbay.remote.RemoteScreens.open(viewer, backshop,
            BayGeometry.machinePos(record.bayColumn(), selectedBay),
            () -> {
                if (origin != null && !origin.isRemoved()) {
                    open(viewer, origin, bay);
                }
            });
    }

    private void editLink(Optional<UUID> linkId, java.util.function.UnaryOperator<BusConfig> edit) {
        linkId.flatMap(workbay::bus).ifPresent(link -> workbay.addBus(edit.apply(link)));
    }

    /**
     * Racks the machine in the player's hand. The gate runs first and a rejection is an action-bar
     * message naming the category — the item stays in hand, which is the whole point of rejecting
     * at the registry level rather than after placement (SPEC.md §11).
     */
    /**
     * A locked Workbay only lets its owner change anything. One guard for every mutation: rack and
     * eject each carried a copy of this and {@code cycleFace} carried none, so anyone could rewrite
     * a locked Workbay's face config.
     */
    private boolean refused(ServerPlayer who, WorkbayRecord record) {
        if (!record.locked() || record.owner().equals(who.getUUID())) {
            return false;
        }
        who.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
        return true;
    }

    private void rack(ServerPlayer serverPlayer, WorkbayRecord record) {
        if (refused(serverPlayer, record)) {
            return;
        }
        if (selectedBay >= record.bayCapacity()) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("reject.no_bay"), true);
            return;
        }
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null) {
            return;
        }
        // OPEN_ISSUES #27. Every other refusal in this method says why; these two returned in
        // silence, so clicking the bay slot did nothing at all and the only way to find out what
        // was wrong was to read the Backshop with /execute in workbay:backshop. A button that
        // does nothing is the worst failure a screen has, because there is no next thing to try.
        if (!backshop.getBlockState(
            BayGeometry.machinePos(record.bayColumn(), selectedBay)).isAir()) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("reject.bay_occupied"), true);
            return;
        }
        ItemStack held = serverPlayer.getMainHandItem();
        if (held.isEmpty()) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("reject.empty_hand"), true);
            return;
        }
        HostResult verdict = HostChecks.evaluate(held);
        if (!verdict.allowed()) {
            serverPlayer.displayClientMessage(verdict.message(), true);
            return;
        }
        ItemStack one = held.copyWithCount(1);
        if (!BayHosting.rack(backshop, record.bayColumn(), selectedBay, one, serverPlayer,
            Direction.NORTH)) {
            // The placement was undone (SPEC.md §10 step 4: setPlacedBy threw). Say so rather
            // than leaving the click looking like it was ignored.
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("reject.rack_failed",
                    one.getHoverName()), true);
            return;
        }
        held.shrink(1);
        RoomRegistry.get(serverPlayer.server).put(record.withBay(record.bay(selectedBay)
            .withHosted(Optional.ofNullable(BuiltInRegistries.ITEM.getKey(one.getItem())))));
        workbay.setChanged();
    }

    private void eject(ServerPlayer serverPlayer, WorkbayRecord record) {
        if (refused(serverPlayer, record)) {
            return;
        }
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null) {
            return;
        }
        ItemStack machine = BayHosting.eject(backshop, record.bayColumn(), selectedBay, serverPlayer);
        if (machine.isEmpty()) {
            return;
        }
        serverPlayer.getInventory().placeItemBackInInventory(machine);
        RoomRegistry.get(serverPlayer.server).put(record.withBay(
            record.bay(selectedBay).withHosted(Optional.empty())));
        workbay.setChanged();
    }

    /** Only the owner may lock or unlock. Everything else on the screen stays readable. */
    private void toggleLock(ServerPlayer serverPlayer, WorkbayRecord record) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        RoomRegistry.get(serverPlayer.server).put(record.withLocked(!record.locked()));
    }

    private void cycleFace(ServerPlayer serverPlayer, WorkbayRecord record, int packed,
        boolean back) {
        if (refused(serverPlayer, record)) {
            return;
        }
        BusConfig.Resource resource = BusConfig.Resource.values()[
            Math.clamp(packed & 0xF, 0, BusConfig.Resource.values().length - 1)];
        Direction face = Direction.values()[
            Math.clamp((packed >> 4) & 0xF, 0, Direction.values().length - 1)];
        WorkbayRecord.Bay bay = record.bay(selectedBay);
        setFaces(serverPlayer, record, bay.faces().cycled(resource, face, back));
    }

    /**
     * Screen 1's paste. Faces only, which is everything a bay carries today; the redstone mode and
     * the filter join it when they exist, and the action already carries the room for them.
     */
    private void pasteBay(ServerPlayer serverPlayer, WorkbayRecord record, long bits) {
        if (refused(serverPlayer, record)) {
            return;
        }
        setFaces(serverPlayer, record, FaceConfig.fromBits(bits));
    }

    private void setFaces(ServerPlayer serverPlayer, WorkbayRecord record, FaceConfig faces) {
        editBay(serverPlayer, record, bay -> bay.withFaces(faces));
    }

    /** Every edit to one bay goes through here, so none of them can forget to drop its caches. */
    private void editBay(ServerPlayer serverPlayer, WorkbayRecord record,
        java.util.function.UnaryOperator<WorkbayRecord.Bay> edit) {
        RoomRegistry.get(serverPlayer.server)
            .put(record.withBay(edit.apply(record.bay(selectedBay))));
        // The link's cached endpoint may have been bound to a face that is no longer allowed.
        workbay.forgetBay(selectedBay);
    }

    /** Screen 1's `+ Pair`: stamps the held Connector with this Workbay and the selected bay. */
    private void pair(ServerPlayer serverPlayer, WorkbayRecord record) {
        ItemStack held = serverPlayer.getMainHandItem();
        if (!held.is(WBBlocks.CONNECTOR.get().asItem())) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("pair_needs_connector"), true);
            return;
        }
        WorkbayBlock.pair(held, record,
            GlobalPos.of(serverPlayer.level().dimension(), workbay.getBlockPos()), selectedBay);
        serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("connector_paired",
            net.minecraft.network.chat.Component.literal(record.code())
                .withStyle(net.minecraft.ChatFormatting.AQUA)), true);
    }

    /**
     * Bay to bay, inside this Workbay, no Connector. SPEC.md §4's dashed-arrow flow made real: the
     * closed decision that a link needs a physical anchor was about links that leave the block —
     * both ends of this one are bays in the same menu the player already has open, so there is
     * nothing in the world to find, break or audit that this screen does not already show.
     */
    private void createInternalLink(ServerPlayer serverPlayer, WorkbayRecord record, int wanted) {
        int capacity = record.bayCapacity();
        if (capacity < 2) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("internal_link_needs_second_bay"), true);
            return;
        }
        // The picker names the bay it wants. A bay that is out of range, or the source bay itself,
        // falls through to the next other bay rather than being refused: this action predates the
        // picker and is still sent with no useful arg from a keybind or an older client.
        boolean usable = wanted >= 0 && wanted < capacity && wanted != selectedBay;
        int targetBay = usable ? wanted : otherBay(record, selectedBay, selectedBay, false);
        GlobalPos anchor = GlobalPos.of(serverPlayer.level().dimension(), workbay.getBlockPos());
        GlobalPos target = GlobalPos.of(WorkbayDimensions.BACKSHOP,
            BayGeometry.machinePos(record.bayColumn(), targetBay));
        workbay.addBus(BusConfig.createInternal(UUID.randomUUID(), selectedBay, anchor, target));
    }

    /** Which bay an internal link's {@code target} currently points at, by position. */
    private static int currentTargetBay(WorkbayRecord record, BusConfig link) {
        for (int i = 0; i < BayGeometry.MAX_BAYS; i++) {
            if (BayGeometry.machinePos(record.bayColumn(), i).equals(link.target().pos())) {
                return i;
            }
        }
        return link.bay();
    }

    /**
     * The next bay after {@code current} that is not {@code sourceBay} itself, wrapping — or the
     * previous one when {@code back}, which is what a right-click on the row's target asks for.
     */
    private static int otherBay(WorkbayRecord record, int sourceBay, int current, boolean back) {
        int capacity = record.bayCapacity();
        for (int step = 1; step <= capacity; step++) {
            int candidate = Math.floorMod(current + (back ? -step : step), capacity);
            if (candidate != sourceBay) {
                return candidate;
            }
        }
        return current;
    }

    /**
     * The skim dial. One step of five per click, right-click stepping back the way every other
     * cycling control in this mod does — and clamped rather than wrapped, because a dial that goes
     * from 25% straight back to 0% on one more click is a dial that empties somebody's tax by
     * accident. Default zero: the mod never takes a cut the player did not ask for.
     */
    private void setSkim(ServerPlayer serverPlayer, WorkbayRecord record, boolean back) {
        int step = back ? -AssayBlock.rateStep() : AssayBlock.rateStep();
        int rate = Math.clamp(record.assay().rate() + step, 0, AssayBlock.maxRate());
        if (rate == record.assay().rate()) {
            return;
        }
        RoomRegistry.get(serverPlayer.server).put(record.withAssay(record.assay().withRate(rate)));
    }

    /**
     * Upgrades are consumed on install and there is no removal path (SPEC.md §1), so this takes the
     * item and never gives it back. Refusing at the cap rather than silently eating it matters.
     *
     * <p>It also spends Levy, and the cost <b>rises</b> with how many are already installed
     * (SPEC.md §1). That is why the cost is here and not in the recipe: a recipe costs the same the
     * tenth time as the first. The item is the materials and the Levy is the ladder.
     */
    private void install(ServerPlayer serverPlayer, WorkbayRecord record, int ordinal) {
        if (ordinal < 0 || ordinal >= WorkbayUpgrade.values().length) {
            return;
        }
        WorkbayUpgrade upgrade = WorkbayUpgrade.values()[ordinal];
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        // An Annex Plate with no Room Frame fitted is the "installs and does nothing" failure
        // SPEC.md §1 warns about: roomCapacity is zero without a Frame, so the plate would be
        // eaten for a room slot that cannot exist.
        if (upgrade == WorkbayUpgrade.ANNEX_PLATE && record.upgrades().roomTier() == 0) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("annex_needs_frame"), true);
            return;
        }
        int installed = record.upgrades().installed(upgrade);
        if (installed >= upgrade.max()) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("upgrade_maxed"), true);
            return;
        }
        int cost = upgrade.levyCost(installed);
        if (record.assay().levy() < cost) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("upgrade_needs_levy", cost,
                    record.assay().levy()), true);
            return;
        }
        int slot = serverPlayer.getInventory().findSlotMatchingItem(new ItemStack(upgrade.item()));
        if (slot < 0) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("upgrade_missing"), true);
            return;
        }
        serverPlayer.getInventory().removeItem(slot, 1);
        RoomRegistry.get(serverPlayer.server).put(record
            .withUpgrades(record.upgrades().plus(upgrade))
            .withAssay(record.assay().withLevy(record.assay().levy() - cost)));
    }

    /**
     * Switches one room's Anchor. Refused when the network has no Anchor upgrade, when the host has
     * force loading off, or when the network is already holding as many rooms as it may — the cap
     * is the number a server owner is actually paying, so it is checked on the click and not read
     * once at startup.
     */
    private void toggleRoomAnchor(ServerPlayer serverPlayer, WorkbayRecord record, int index) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server);
        List<com.neryos.workbay.world.RoomRecord> rooms = registry.roomsOf(record);
        if (index < 0 || index >= rooms.size()) {
            return;
        }
        com.neryos.workbay.world.RoomRecord room = rooms.get(index);
        boolean turningOn = !room.anchored();
        if (turningOn && !com.neryos.workbay.world.RoomAnchors.canAnchorAnother(registry, record, room)) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("anchor_capped",
                    com.neryos.workbay.config.WorkbayConfig.SERVER.maxAnchoredRoomsPerNetwork.get()),
                true);
            return;
        }
        com.neryos.workbay.world.RoomRecord updated = room.withAnchored(turningOn);
        registry.putRoom(updated);
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop != null) {
            com.neryos.workbay.world.RoomAnchors.apply(backshop, updated);
        }
    }

    /**
     * Invites a player into one room, by the name they are known by.
     *
     * <p><b>Only the owner may.</b> Same guard as the Anchor and the colour, and for a stronger
     * reason: this one hands somebody else the key. An invitation starts at
     * {@link com.neryos.workbay.world.RoomGuest#LOOK} because the safe end of a two-level ring is
     * the one an accidental click lands on.
     *
     * <p>The name is resolved against the players who are online and then against the profile
     * cache, which is what a whitelist command does. Somebody this server has never seen cannot be
     * invited, and the refusal says so rather than adding a guest with an id made up on the spot.
     */
    private void inviteGuest(ServerPlayer serverPlayer, WorkbayRecord record, int index,
        String name) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        if (name.isEmpty()) {
            return;
        }
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server);
        List<com.neryos.workbay.world.RoomRecord> rooms = registry.roomsOf(record);
        if (index < 0 || index >= rooms.size()) {
            return;
        }
        ServerPlayer online = serverPlayer.server.getPlayerList().getPlayerByName(name);
        java.util.Optional<com.mojang.authlib.GameProfile> profile = online != null
            ? java.util.Optional.of(online.getGameProfile())
            : serverPlayer.server.getProfileCache() == null
                ? java.util.Optional.empty()
                : serverPlayer.server.getProfileCache().get(name);
        if (profile.isEmpty()) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("guest_unknown", name), true);
            return;
        }
        if (profile.get().getId().equals(record.owner())) {
            serverPlayer.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("guest_is_owner"), true);
            return;
        }
        registry.putRoom(rooms.get(index).withGuest(profile.get().getId(), profile.get().getName(),
            com.neryos.workbay.world.RoomGuest.LOOK));
    }

    /**
     * Steps a guest's level, or takes the invitation away. One method for both because the two
     * differ by one line and share the whole of the lookup -- which room, which guest, and whether
     * the person clicking owns it.
     */
    private void editGuest(ServerPlayer serverPlayer, WorkbayRecord record, int index,
        int guest, boolean step) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server);
        List<com.neryos.workbay.world.RoomRecord> rooms = registry.roomsOf(record);
        if (index < 0 || index >= rooms.size()) {
            return;
        }
        com.neryos.workbay.world.RoomRecord room = rooms.get(index);
        if (guest < 0 || guest >= room.guests().size()) {
            return;
        }
        com.neryos.workbay.world.RoomRecord.Guest who = room.guests().get(guest);
        registry.putRoom(step
            ? room.withGuest(who.id(), who.name(), who.level().step())
            : room.withoutGuest(who.id()));
    }

    /**
     * Steps one room's biome and writes it over the room's chunks. Only a built room has chunks to
     * write, which is also why the button is only drawn on one — an unopened room has no record to
     * remember the choice on (SPEC.md §8 spends the region on first entry).
     */
    private void cycleRoomBiome(ServerPlayer serverPlayer, WorkbayRecord record, int index) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server);
        List<com.neryos.workbay.world.RoomRecord> rooms = registry.roomsOf(record);
        if (index < 0 || index >= rooms.size() || !rooms.get(index).built()) {
            return;
        }
        com.neryos.workbay.world.RoomRecord updated = rooms.get(index).withBiome(
            com.neryos.workbay.world.RoomBiomes.next(serverPlayer.server, rooms.get(index)));
        registry.putRoom(updated);
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop != null) {
            com.neryos.workbay.world.RoomBiomes.apply(backshop, updated);
        }
    }

    /**
     * Repaints one room. Only a built room has a shell to paint, which is also why the swatch is
     * only drawn on one.
     */
    private void cycleRoomColour(ServerPlayer serverPlayer, WorkbayRecord record, int index,
        boolean backwards) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return;
        }
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server);
        List<com.neryos.workbay.world.RoomRecord> rooms = registry.roomsOf(record);
        if (index < 0 || index >= rooms.size() || !rooms.get(index).built()) {
            return;
        }
        com.neryos.workbay.world.RoomRecord painted =
            rooms.get(index).withColour(rooms.get(index).colour().next(backwards));
        registry.putRoom(painted);
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop != null) {
            // The room's own tier, not the network's: repainting must never also grow it, or a
            // colour click would rebuild a shell somebody is standing in.
            com.neryos.workbay.world.RoomBuilder.ensure(backshop, painted, painted.builtTier());
        }
    }

    /**
     * One room's biome, named by its own id.
     *
     * <p><b>Checked against the tag on this side, always.</b> The id arrives from a client, and
     * {@code #workbay:room_biomes} is the whole of what a pack said a room may be — a client that
     * asks for {@code the_end} gets nothing, not a private End.
     */
    private void setRoomBiome(ServerPlayer serverPlayer, WorkbayRecord record, int index,
        String id) {
        com.neryos.workbay.world.RoomRecord room = editableRoom(serverPlayer, record, index);
        net.minecraft.resources.ResourceLocation where =
            net.minecraft.resources.ResourceLocation.tryParse(id);
        List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> all =
            com.neryos.workbay.world.RoomBiomes.choices(serverPlayer.server.registryAccess());
        var chosen = where == null ? null
            : all.stream().filter(key -> key.location().equals(where)).findFirst().orElse(null);
        if (room == null || chosen == null) {
            return;
        }
        com.neryos.workbay.world.RoomRecord updated = room.withBiome(chosen);
        com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server).putRoom(updated);
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop != null) {
            com.neryos.workbay.world.RoomBiomes.apply(backshop, updated);
        }
        refreshNow();
    }

    /** One room's shell colour, by {@code RoomColour} ordinal. */
    private void setRoomColour(ServerPlayer serverPlayer, WorkbayRecord record, int index,
        int choice) {
        com.neryos.workbay.world.RoomRecord room = editableRoom(serverPlayer, record, index);
        com.neryos.workbay.content.room.RoomColour[] all =
            com.neryos.workbay.content.room.RoomColour.values();
        if (room == null || choice < 0 || choice >= all.length) {
            return;
        }
        com.neryos.workbay.world.RoomRecord painted = room.withColour(all[choice]);
        com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server).putRoom(painted);
        ServerLevel backshop = serverPlayer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop != null) {
            // The room's own tier, not the network's: repainting must never also grow it.
            com.neryos.workbay.world.RoomBuilder.ensure(backshop, painted, painted.builtTier());
        }
        refreshNow();
    }

    /**
     * The room at {@code index} if this player may change it, or null. Only a built room has a
     * shell to paint or chunks to write a biome over.
     */
    @Nullable
    private com.neryos.workbay.world.RoomRecord editableRoom(ServerPlayer serverPlayer,
        WorkbayRecord record, int index) {
        if (!record.owner().equals(serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(com.neryos.workbay.WorkbayLang.message("locked"), true);
            return null;
        }
        List<com.neryos.workbay.world.RoomRecord> rooms =
            com.neryos.workbay.world.RoomRegistry.get(serverPlayer.server).roomsOf(record);
        if (index < 0 || index >= rooms.size() || !rooms.get(index).built()) {
            return null;
        }
        return rooms.get(index);
    }

    // -------------------------------------------------------------- snapshot

    /**
     * Reads the whole world state the screens need, once. The hosted machines live in the Backshop,
     * so every read there is guarded by {@code isLoaded} — a status screen must never be the thing
     * that drags a chunk in synchronously on the server thread (SPEC.md §9).
     */
    /**
     * Opens the Workbay screen on one bay. <b>The only place that knows how</b>: the block's
     * right-click and {@link com.neryos.workbay.world.BayVisit}'s return trip both come here, so a
     * player who entered a bay from this screen gets <em>this screen</em> back rather than an empty
     * hand — closing the machine's screen should undo the visit, not undo everything.
     */
    public static void open(ServerPlayer viewer, WorkbayBlockEntity workbay, int bay) {
        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, who) -> new WorkbayMenu(id, inventory, workbay,
                build(workbay, viewer, bay)),
            com.neryos.workbay.WorkbayLang.gui("title")),
            buffer -> WorkbaySnapshot.STREAM_CODEC.encode(buffer, build(workbay, viewer, bay)));
    }

    public static WorkbaySnapshot build(WorkbayBlockEntity workbay, ServerPlayer player, int selected) {
        WorkbayRecord record = workbay.record().orElse(null);
        if (record == null) {
            return WorkbaySnapshot.EMPTY;
        }
        ServerLevel backshop = player.server.getLevel(WorkbayDimensions.BACKSHOP);

        List<WorkbaySnapshot.Bay> bays = new ArrayList<>();
        for (int index = 0; index < BayGeometry.MAX_BAYS; index++) {
            bays.add(readBay(record, index, backshop, workbay));
        }

        List<WorkbaySnapshot.Link> links = new ArrayList<>();
        List<BusConfig> backfill = new ArrayList<>();
        for (BusConfig link : List.copyOf(workbay.buses())) {
            Optional<ResourceLocation> block = targetBlockOf(player, link);
            if (link.targetBlock().isEmpty() && isRealBlock(block)) {
                backfill.add(link.withTargetBlock(block));
            }
            links.add(new WorkbaySnapshot.Link(link, workbay.busStatus(link.id()), block,
                link.internal() ? Optional.of(currentTargetBay(record, link)) : Optional.empty(),
                roomOf(player, record, link)));
        }
        // After the loop, not inside it: addBus rewrites the record's list, and one write per link
        // that has something to learn is still nothing next to a write per poll.
        backfill.forEach(workbay::addBus);

        return new WorkbaySnapshot(record.code(), record.locked(), record.bayCapacity(), selected,
            workbay.energy().getEnergyStored(), workbay.energy().getMaxEnergyStored(),
            bays, links, record.upgrades(), record.assay().levy(), record.assay().rate(),
            record.assay().skimmed(), record.deployedCount(),
            com.neryos.workbay.config.WorkbayConfig.SERVER.maxDeployedWorkbaysPerNetwork.get(),
            com.neryos.workbay.remote.RemoteConfig.remoteScreensEnabled(),
            readRooms(player, record));
    }

    /**
     * One entry per room slot the upgrades entitle this network to, opened or not. An unopened slot
     * has no record yet — SPEC.md §8 spends the region on first entry — so it reads as zero size
     * and zero chunks, which is exactly what it costs.
     */
    private static List<WorkbaySnapshot.Room> readRooms(ServerPlayer player, WorkbayRecord record) {
        List<WorkbaySnapshot.Room> out = new ArrayList<>();
        com.neryos.workbay.world.RoomRegistry registry =
            com.neryos.workbay.world.RoomRegistry.get(player.server);
        List<com.neryos.workbay.world.RoomRecord> known = registry.roomsOf(record);
        for (int index = 0; index < record.roomCapacity(); index++) {
            com.neryos.workbay.world.RoomRecord room = index < known.size() ? known.get(index) : null;
            String name = room == null ? "" : room.name().orElse("");
            out.add(new WorkbaySnapshot.Room(index, name,
                room == null ? 0 : com.neryos.workbay.world.RoomGeometry.interior(room.builtTier()),
                room == null ? 0 : room.chunkCost(),
                room != null && room.built(),
                room != null && room.anchored(),
                room == null ? "" : room.effectiveBiome().location().toString(),
                room == null ? com.neryos.workbay.content.room.RoomColour.DEFAULT : room.colour(),
                room == null ? List.of() : room.guests()));
        }
        return out;
    }

    private static WorkbaySnapshot.Bay readBay(WorkbayRecord record, int index,
        @Nullable ServerLevel backshop, WorkbayBlockEntity workbay) {
        WorkbayRecord.Bay bay = record.bay(index);
        if (index >= record.bayCapacity()) {
            return new WorkbaySnapshot.Bay(index, Optional.empty(), 0, 0,
                WorkbaySnapshot.State.LOCKED, bay.faces(), bay.name(), bay.redstone());
        }
        if (bay.hosted().isEmpty()) {
            return new WorkbaySnapshot.Bay(index, Optional.empty(), 0, 0,
                WorkbaySnapshot.State.EMPTY, bay.faces(), bay.name(), bay.redstone());
        }
        // The Assay answers on no face by design, which is exactly the shape of INERT — "racked and
        // nothing can reach it". Amber on the one bay that is working correctly is the worst pip on
        // the screen, so it is read from the Levy the network is actually making instead.
        if (bay.hosted().filter(WBBlocks.ASSAY.getId()::equals).isPresent()) {
            return new WorkbaySnapshot.Bay(index, bay.hosted(), 0, 0,
                record.assay().rate() > 0 ? WorkbaySnapshot.State.RUNNING : WorkbaySnapshot.State.IDLE,
                bay.faces(), bay.name(), bay.redstone());
        }
        int energy = 0;
        int capacity = 0;
        WorkbaySnapshot.State state = WorkbaySnapshot.State.IDLE;
        BlockPos machine = BayGeometry.machinePos(record.bayColumn(), index);
        if (backshop != null && backshop.isLoaded(machine)) {
            IEnergyStorage store = null;
            boolean anyPort = false;
            for (Direction side : Direction.values()) {
                IEnergyStorage found = backshop.getCapability(Capabilities.EnergyStorage.BLOCK, machine, side);
                if (found != null && store == null) {
                    store = found;
                }
                anyPort |= found != null
                    || backshop.getCapability(Capabilities.ItemHandler.BLOCK, machine, side) != null;
            }
            if (store != null) {
                energy = store.getEnergyStored();
                capacity = store.getMaxEnergyStored();
            }
            BlockState here = backshop.getBlockState(machine);
            // Inert is the two-phase gate's non-blocking half: the machine is racked and staying
            // racked, but nothing can reach it, so the row says so instead of ejecting it.
            state = here.isAir() ? WorkbaySnapshot.State.EMPTY
                : !anyPort ? WorkbaySnapshot.State.INERT
                : runningAnyLink(workbay, index) ? WorkbaySnapshot.State.RUNNING
                : WorkbaySnapshot.State.IDLE;
        }
        return new WorkbaySnapshot.Bay(index, bay.hosted(), energy, capacity, state, bay.faces(),
            bay.name(), bay.redstone());
    }

    private static boolean runningAnyLink(WorkbayBlockEntity workbay, int bay) {
        return workbay.buses().stream().filter(link -> link.bay() == bay)
            .anyMatch(link -> workbay.busStatus(link.id()) == BusRunner.BusStatus.RUNNING);
    }

    /**
     * What the Connector is stuck to, for the row's target text and the flow map's icon. Never
     * loads a chunk to find out — and falls back to what the link remembered when it was made,
     * which is the ordinary case: a link's target is nearly always in a chunk nobody is standing
     * in, and before the fallback existed every such row read as two coordinates and every such
     * flow node drew an empty box.
     *
     * <p>A link made before {@link BusConfig#targetBlock} existed remembers <b>nothing</b>, so it
     * reads as two coordinates for the rest of its life however often anyone stands next to it —
     * and a flow map of number-boxes is a map of nowhere. {@code build} therefore writes the live
     * answer back the first time there is one. See {@link #isRealBlock}: air is not one.
     */
    private static Optional<ResourceLocation> targetBlockOf(ServerPlayer player, BusConfig link) {
        ServerLevel level = player.server.getLevel(link.target().dimension());
        if (level == null || !level.isLoaded(link.target().pos())) {
            return link.targetBlock();
        }
        return Optional.ofNullable(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(link.target().pos()).getBlock()));
    }

    /**
     * Which of this network's rooms a link's Connector stands in, by name.
     *
     * <p>Only this network's own rooms are consulted, and only the {@link com.neryos.workbay.bus.BusConfig#connector()}
     * end: a Connector is paired to exactly one Workbay and one bay, so a link into a room can only
     * ever be a link into a room of the network that owns it. Nothing here can name somebody
     * else's room, which is what makes this safe to send to a client.
     */
    private static Optional<Integer> roomOf(ServerPlayer player, WorkbayRecord record,
        BusConfig link) {
        if (record.rooms().isEmpty()
            || !link.connector().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            return Optional.empty();
        }
        java.util.List<com.neryos.workbay.world.RoomRecord> rooms =
            RoomRegistry.get(player.server).roomsOf(record);
        for (int index = 0; index < rooms.size(); index++) {
            com.neryos.workbay.world.RoomRecord room = rooms.get(index);
            if (room.built() && com.neryos.workbay.world.RoomGeometry.inside(
                link.connector().pos(), room.region(), room.builtTier())) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    /**
     * Air is what a target that has been <em>broken</em> reads as, and what an unloaded position
     * would read as if anything ever asked one. Stamping it would fix the wrong answer in place
     * for good, because the backfill only ever fires on an empty stamp.
     */
    private static boolean isRealBlock(Optional<ResourceLocation> block) {
        return block.isPresent()
            && !block.get().equals(ResourceLocation.withDefaultNamespace("air"));
    }
}
