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
            case SET_FILTER -> editLink(linkId, link -> link.withFilter(filterItem(arg)));
            case SET_BAY_NAME -> editBay(serverPlayer, record,
                bay -> bay.withName(text.orElse("").strip()));
            case CYCLE_REDSTONE -> editBay(serverPlayer, record,
                bay -> bay.withRedstone(bay.redstone().step(back)));
            case SET_SKIM -> setSkim(serverPlayer, record, back);
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
     * An item's registry id, the way every vanilla packet carries one, or nothing for -1 and for an
     * id this server does not know. Never trusted into an array index.
     */
    private static Optional<ResourceLocation> filterItem(long networkId) {
        if (networkId < 0 || networkId > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        var item = BuiltInRegistries.ITEM.byId((int) networkId);
        return item == net.minecraft.world.item.Items.AIR
            ? Optional.empty()
            : Optional.ofNullable(BuiltInRegistries.ITEM.getKey(item));
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
        if (backshop == null || !backshop.getBlockState(
            BayGeometry.machinePos(record.bayColumn(), selectedBay)).isAir()) {
            return;
        }
        ItemStack held = serverPlayer.getMainHandItem();
        if (held.isEmpty()) {
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
        int step = back ? -AssayBlock.RATE_STEP : AssayBlock.RATE_STEP;
        int rate = Math.clamp(record.assay().rate() + step, 0, AssayBlock.MAX_RATE);
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

    // -------------------------------------------------------------- snapshot

    /**
     * Reads the whole world state the screens need, once. The hosted machines live in the Backshop,
     * so every read there is guarded by {@code isLoaded} — a status screen must never be the thing
     * that drags a chunk in synchronously on the server thread (SPEC.md §9).
     */
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
        for (BusConfig link : workbay.buses()) {
            links.add(new WorkbaySnapshot.Link(link, workbay.busStatus(link.id()),
                targetBlockOf(player, link),
                link.internal() ? Optional.of(currentTargetBay(record, link)) : Optional.empty()));
        }

        return new WorkbaySnapshot(record.code(), record.locked(), record.bayCapacity(), selected,
            workbay.energy().getEnergyStored(), workbay.energy().getMaxEnergyStored(),
            bays, links, record.upgrades(), record.assay().levy(), record.assay().rate(),
            record.deployedCount(),
            com.neryos.workbay.config.WorkbayConfig.SERVER.maxDeployedWorkbaysPerNetwork.get());
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

    /** What the Connector is stuck to, for the row's target text. Never loads a chunk to find out. */
    private static Optional<ResourceLocation> targetBlockOf(ServerPlayer player, BusConfig link) {
        ServerLevel level = player.server.getLevel(link.target().dimension());
        if (level == null || !level.isLoaded(link.target().pos())) {
            return Optional.empty();
        }
        return Optional.ofNullable(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(link.target().pos()).getBlock()));
    }
}
