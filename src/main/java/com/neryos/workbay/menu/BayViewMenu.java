package com.neryos.workbay.menu;

import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBMenus;
import com.neryos.workbay.network.BayViewPacket;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Bay View. SPEC.md §5: our own screen over a hosted machine's item slots, read entirely through
 * the standard capability the buses already use, so it works for every mod with zero per-mod code
 * and zero kick risk.
 *
 * <p><b>This is the mod's largest duplication surface</b>, and everything unusual here is about
 * that. A machine in a bay can be ejected, its chunk can unload, and the block can stop existing
 * while a player is holding one of its stacks on the cursor — none of which can happen to the chest
 * a vanilla screen is bound to. The rule this class is written against is the one in ROADMAP §2:
 * <em>the item exists in exactly one place at the end of every tick</em>.
 *
 * <p>It shows <b>items, fluid tanks and energy</b> — the three things a standard capability will
 * tell us about without a line of per-mod code. Progress is deliberately still absent: there is no
 * generic contract for it, so a bar over it would be invented rather than read (SPEC.md §5).
 *
 * <p>Three decisions carry that:
 *
 * <ul>
 * <li><b>{@link Live} re-resolves the capability on every single call</b> and answers "nothing,
 *     and no" once the machine is gone. It never caches a handler, because a cached handler
 *     belonging to a removed block entity accepts writes that reach nobody — items into a void.
 * <li><b>A gone machine refuses rather than swallows.</b> {@code isItemValid} and {@code extractItem}
 *     both come back empty, so vanilla's {@code Slot#mayPlace} and {@code mayPickup} are false and
 *     every click path in {@code AbstractContainerMenu#doClick} declines to move anything. Silently
 *     accepting the click is what deletes the cursor stack.
 * <li><b>{@link #quickMoveStack} never uses {@code moveItemStackTo} on a machine slot.</b> That
 *     helper grows the stack it got from {@code getStackInSlot} in place and calls
 *     {@code setChanged}, which is correct for a vanilla container and silently does nothing for
 *     any handler that hands out a copy — the carried stack shrinks and the items are gone.
 * </ul>
 */
public class BayViewMenu extends AbstractContainerMenu {

    /** Six rows of nine. More than any machine has, and the most a screen fits. */
    public static final int MAX_SLOTS = 54;

    /**
     * The most tank gauges drawn. Same argument as {@link #MAX_SLOTS}: a foreign handler decides how
     * many tanks it reports and the panel does not grow without limit. Nothing shipping has more
     * than three.
     */
    public static final int MAX_TANKS = 6;

    private static final int SLOT_SIZE = 18;
    public static final int COLUMNS = 9;

    /**
     * One gauge row: a tall narrow gauge on the left, and two lines of text beside it - what the
     * tank holds, and how much. Tall because that is what a tank looks like in every machine mod
     * there is - EnderIO's tank widget is 16x47, Mekanism's standard gauge 16x58.
     *
     * <p><b>Sixteen pixels wide inside</b>, hence eighteen outside: a fluid's still texture is
     * sixteen wide, and the old fourteen-wide tube clipped two columns off every tile it drew.
     * Thirty-two inside is two whole tiles and enough height for quarter graduations to separate.
     */
    public static final int GAUGE_W = 18;
    public static final int GAUGE_H = 34;
    public static final int GAUGE_PITCH = 38;

    /** The fluid-container slots under the gauges, and the room the row takes. */
    public static final int EXCHANGE_ROW = 24;
    public static final int EXCHANGE_IN_X = 8;
    public static final int EXCHANGE_OUT_X = 30;
    public static final int EXCHANGE_IN = 0;
    public static final int EXCHANGE_OUT = 1;

    /** Where the machine grid starts, and where the player's own inventory starts under it. */
    public static final int GRID_X = 8;
    public static final int GRID_Y = 40;

    @Nullable
    private final WorkbayBlockEntity workbay;

    private final int bay;

    /** What was standing in the bay when this opened. It changing is how the menu knows to close. */
    private final Optional<ResourceLocation> machineId;

    private final int machineSlots;

    private final IItemHandler machine;

    private final Player viewer;

    /**
     * How many gauge rows the panel was laid out for, fixed when the screen opened.
     *
     * <p>Fixed, not read from {@link #state} each frame, because the layout below it — the limits
     * sentence, the player's own inventory, the panel's height — is decided once in this
     * constructor. A machine whose fluid handler fails to resolve for a tick must leave a gap, not
     * move every slot on the screen out from under the cursor.
     */
    private final int gauges;

    /** Whether the panel was laid out with the fluid-container row. Fixed for the same reason. */
    private final boolean tanks;

    /**
     * The two fluid-container slots, in and out.
     *
     * <p><b>The menu's own buffer, not the machine's.</b> A foreign machine has nowhere for us to
     * park a bucket, so these live for as long as the screen does and {@link #removed} hands back
     * whatever is standing in them — the same contract vanilla's crafting grid has. The alternative
     * is writing into the machine's item slots, which is a different feature and a worse one: a
     * furnace's fuel slot is not a place to keep a bucket that is halfway through being filled.
     */
    private final ItemStackHandler exchange = new ItemStackHandler(2);

    /** Tanks and energy, as last read on the server. Never a source of truth on the client. */
    private State state;

    private State sent = State.EMPTY;

    /** Client side: everything the screen needs rides the menu-open buffer, like SPEC.md §4's. */
    public BayViewMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readVarInt(),
            buffer.readOptional(net.minecraft.network.FriendlyByteBuf::readResourceLocation),
            buffer.readVarInt(), null, State.STREAM_CODEC.decode(buffer));
    }

    public BayViewMenu(int containerId, Inventory inventory, @Nullable WorkbayBlockEntity workbay,
        int bay, Optional<ResourceLocation> machineId, int machineSlots,
        @Nullable IItemHandler machine, State state) {
        super(WBMenus.BAY_VIEW.get(), containerId);
        this.workbay = workbay;
        this.bay = bay;
        this.viewer = inventory.player;
        this.machineId = machineId;
        this.machineSlots = Math.clamp(machineSlots, 0, MAX_SLOTS);
        this.state = state;
        this.gauges = state.gauges();
        this.tanks = !state.tanks().isEmpty();
        // The client has no capability to reach, so it mirrors into a plain handler and lets the
        // ordinary slot sync fill it. Nothing on the client is ever the source of truth.
        this.machine = machine != null ? machine : new ItemStackHandler(this.machineSlots);

        for (int index = 0; index < this.machineSlots; index++) {
            addSlot(new SlotItemHandler(this.machine, index,
                GRID_X + (index % COLUMNS) * SLOT_SIZE,
                GRID_Y + (index / COLUMNS) * SLOT_SIZE));
        }

        int inventoryY = inventoryY(machineSlots(), gauges, this.tanks);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                    GRID_X + column * SLOT_SIZE, inventoryY + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, GRID_X + column * SLOT_SIZE, inventoryY + 58));
        }
        // Last, so every index the rest of this class computes from machineSlots and 36 still
        // means what it meant. Only when there is a tank: two slots under a machine with none are
        // two slots that can never do anything.
        if (tanks) {
            int y = exchangeY(machineSlots(), gauges);
            addSlot(new SlotItemHandler(exchange, EXCHANGE_IN, EXCHANGE_IN_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return FluidUtil.getFluidHandler(stack).isPresent();
                }
            });
            addSlot(new SlotItemHandler(exchange, EXCHANGE_OUT, EXCHANGE_OUT_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
    }


    public int rows() {
        return rows(machineSlots);
    }

    private static int rows(int slots) {
        return Math.max(1, (slots + COLUMNS - 1) / COLUMNS);
    }

    /** Where the tank and energy gauges start: straight under the item grid. */
    public static int gaugesY(int slots) {
        return GRID_Y + rows(slots) * SLOT_SIZE + 6;
    }

    /**
     * The fluid-container row, straight under the gauges. Its two slots are what a player actually
     * uses to fill a hosted tank, so they sit beside the thing they change.
     */
    public static int exchangeY(int slots, int gauges) {
        return gaugesY(slots) + gauges * GAUGE_PITCH + 2;
    }

    /** And the limits sentence under those, so gauges push it down rather than sit on top of it. */
    public static int limitsY(int slots, int gauges, boolean tanks) {
        return gaugesY(slots) + gauges * GAUGE_PITCH + (tanks ? EXCHANGE_ROW : 0);
    }

    /**
     * Where the player's own inventory starts. The fifty-four pixels under the limits line are not
     * padding: SPEC.md §5 requires a permanent line naming what this screen cannot reach, and a
     * screen that mimics a machine's and silently lacks half its controls reads as a broken mod
     * rather than a limit.
     *
     * <p>Fifty-four and not twenty because that sentence wraps to <b>four</b> lines at this panel's
     * width, not the two it looks like in the source. Short, it ran straight through the Inventory
     * label and the first row of the player's own slots - seen in {@code runClient}, and the exact
     * same class of fault as the skim chip that overran the link name last session.
     */
    public static int inventoryY(int slots, int gauges, boolean tanks) {
        return limitsY(slots, gauges, tanks) + 54;
    }

    public static int height(int slots, int gauges, boolean tanks) {
        return inventoryY(slots, gauges, tanks) + 58 + SLOT_SIZE + 7;
    }

    public int gauges() {
        return gauges;
    }

    public int inventoryY() {
        return inventoryY(machineSlots, gauges, tanks);
    }

    public int limitsY() {
        return limitsY(machineSlots, gauges, tanks);
    }

    public int exchangeY() {
        return exchangeY(machineSlots, gauges);
    }

    /** Whether the fluid-container row is on this panel at all. */
    public boolean hasTanks() {
        return tanks;
    }

    public int height() {
        return height(machineSlots, gauges, tanks);
    }

    public State state() {
        return state;
    }

    /** Server to client, and the client's only source for what is in the tanks. */
    public void applyState(State updated) {
        this.state = updated;
    }

    public int machineSlots() {
        return machineSlots;
    }

    public int bay() {
        return bay;
    }

    public Optional<ResourceLocation> machineId() {
        return machineId;
    }

    /**
     * Everything a Bay View menu is built from, worked out once on the server.
     *
     * <p>A record rather than the menu itself because the menu's container id belongs to the
     * player's connection and is only handed out inside {@code openMenu}. It is public so the
     * gametests can build the same menu the same way without a client attached — the alternative
     * is a test-only constructor, and a test that assembles the thing differently from the game
     * is a test of the assembly, not of the mod.
     */
    public record Opening(Optional<ResourceLocation> machineId, int slots, Live handler,
        State state) {}

    /**
     * One tank, as the screen draws it. The capacity rides along because {@code getTankCapacity} is
     * a server-side call and the client has no handler to ask.
     */
    public record Tank(FluidStack contents, int capacity) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Tank> STREAM_CODEC =
            StreamCodec.composite(
                FluidStack.OPTIONAL_STREAM_CODEC, Tank::contents,
                ByteBufCodecs.VAR_INT, Tank::capacity,
                Tank::new);
    }

    /**
     * Why the fluid-container slots did nothing, when they did nothing.
     *
     * <p>On the record rather than in a tooltip, and worked out on the <b>server</b> rather than
     * guessed on the client. The client knows what the tanks hold but not what a face will accept,
     * and a screen that says "refused because it holds Lava" when the real reason was a read-only
     * face is worse than a screen that says nothing. Same argument as SPEC.md §5's limits line: the
     * player who needs it is the one who has not thought to hover anything.
     */
    public enum Hint {
        /** Nothing to say: idle, or it worked. */
        NONE,
        /** The tank holds a different fluid. The screen names it from {@link State#tanks}. */
        MIXED,
        /** The result has nowhere to go until the out slot is cleared. */
        BLOCKED,
        /**
         * Nothing moves either way. Full, empty, a face that refuses, or a fluid with no container
         * item at all — a Mekanism gas has no bucket, and an empty bucket held against one must
         * come back an empty bucket rather than swallow the tank.
         *
         * <p>One value for all four because the gauge beside the slots already says whether the
         * tank is full or empty, and a line repeating what the picture says is a line nobody reads.
         */
        REFUSED;

        /** Clamped rather than trusted: a byte off the wire must never index off the end. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Hint> STREAM_CODEC =
            StreamCodec.of((buffer, hint) -> buffer.writeVarInt(hint.ordinal()),
                buffer -> values()[Math.clamp(buffer.readVarInt(), 0, values().length - 1)]);
    }

    /**
     * Everything on this screen that is not an item slot: the tanks and the energy buffer.
     *
     * <p>Its own packet rather than {@code DataSlot}s because vanilla's data-slot channel is
     * <b>sixteen bits wide</b> — a tank holding 10,000 mB or a Mekanism cube holding 1,600,000 FE
     * does not fit in one, and splitting every figure into a hi and a lo slot is the kind of thing
     * that is wrong once and then wrong forever. A whole record that either matches or does not
     * cannot drift, which is the same argument {@link WorkbaySnapshot} is written on.
     */
    public record State(List<Tank> tanks, int energy, int energyCapacity, Hint hint) {

        public static final State EMPTY = new State(List.of(), 0, 0, Hint.NONE);

        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
            StreamCodec.composite(
                Tank.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TANKS)), State::tanks,
                ByteBufCodecs.VAR_INT, State::energy,
                ByteBufCodecs.VAR_INT, State::energyCapacity,
                Hint.STREAM_CODEC, State::hint,
                State::new);

        public State withHint(Hint updated) {
            return hint == updated ? this : new State(tanks, energy, energyCapacity, updated);
        }

        /** How many rows the panel has to find room for. */
        public int gauges() {
            return tanks.size() + (energyCapacity > 0 ? 1 : 0);
        }

        /**
         * <b>Not {@code equals}.</b> {@link FluidStack} has no value equality — like
         * {@code ItemStack} it is compared with a static helper — so a record's generated
         * {@code equals} would say "different" every single tick and resend an idle machine's state
         * forever.
         */
        public boolean sameAs(State other) {
            if (energy != other.energy || energyCapacity != other.energyCapacity
                || hint != other.hint || tanks.size() != other.tanks.size()) {
                return false;
            }
            for (int i = 0; i < tanks.size(); i++) {
                Tank mine = tanks.get(i);
                Tank theirs = other.tanks.get(i);
                if (mine.capacity() != theirs.capacity()
                    || !FluidStack.matches(mine.contents(), theirs.contents())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * What is reachable in one bay, or null if nothing is. Never loads a chunk to find out: a
     * screen must not be the thing that drags a Backshop chunk in on the server thread (SPEC.md §9).
     */
    @Nullable
    public static Opening opening(ServerPlayer viewer, WorkbayRecord record, int bay) {
        ServerLevel backshop = viewer.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null || bay < 0 || bay >= record.bayCapacity()) {
            return null;
        }
        BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), bay);
        if (!backshop.isLoaded(machinePos)) {
            return null;
        }
        Live live = new Live(backshop, machinePos);
        IItemHandler now = live.now();
        State state = live.read();
        // Slots or gauges: a tank with no item ports is still worth opening on, which is the whole
        // point of drawing fluids at all. "Nothing answers by hand" now means nothing at all does.
        if ((now == null || now.getSlots() <= 0) && state.gauges() == 0) {
            return null;
        }
        int slots = now == null ? 0 : Math.min(now.getSlots(), MAX_SLOTS);
        return new Opening(record.bay(bay).hosted(), slots, live, state);
    }

    /**
     * Opens Bay View on one bay. SPEC.md §5.
     *
     * @return false if there is nothing to open on, so the caller can say so out loud
     */
    public static boolean open(ServerPlayer viewer, WorkbayBlockEntity workbay,
        WorkbayRecord record, int bay) {
        Opening opening = opening(viewer, record, bay);
        if (opening == null) {
            return false;
        }
        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (containerId, inventory, who) -> new BayViewMenu(containerId, inventory, workbay, bay,
                opening.machineId(), opening.slots(), opening.handler(), opening.state()),
            com.neryos.workbay.WorkbayLang.gui("bayview.title", bay + 1)),
            buffer -> {
                buffer.writeVarInt(bay);
                buffer.writeOptional(opening.machineId(),
                    (buf, value) -> buf.writeResourceLocation(value));
                buffer.writeVarInt(opening.slots());
                State.STREAM_CODEC.encode(buffer, opening.state());
            });
        return true;
    }

    /**
     * Tanks and energy, resent only when they differ from what this player was last told — the same
     * shape as {@link WorkbayMenu}'s snapshot, and for the same reason: an idle machine costs one
     * comparison a tick and no bandwidth at all.
     *
     * <p>No cooldown. A tank is the one thing on this screen that moves while the player watches,
     * and the state is three ints and a fluid; the comparison is what makes it free, not a timer.
     */
    @Override
    public void broadcastChanges() {
        tick();
        super.broadcastChanges();
        if (!(viewer instanceof ServerPlayer serverPlayer) || !(machine instanceof Live)) {
            return;
        }
        if (!state.sameAs(sent)) {
            sent = state;
            PacketDistributor.sendToPlayer(serverPlayer, new BayViewPacket(containerId, state));
        }
    }

    /**
     * One server tick of this screen: run the fluid exchange, then re-read what the machine holds.
     *
     * <p>Separate from {@link #broadcastChanges} so a gametest can drive it without a connection.
     * Nothing on the client reaches this — {@code machine} is a plain handler there.
     */
    public void tick() {
        if (machine instanceof Live live) {
            // The exchange first, then the reading. Written the other way round - as
            // {@code live.read().withHint(exchange(live))} - Java evaluates the receiver before the
            // argument, so every gauge on the screen showed the tank as it was *before* this tick's
            // transfer. Cost a red test that said the tank was empty while the level said 1,000mB.
            Hint hint = exchange(live);
            state = live.read().withHint(hint);
        }
    }

    /**
     * <b>One</b> container out of the in slot, exchanged with the machine's tanks, and the result
     * into the out slot. Run once a tick from {@link #broadcastChanges}, which is what makes it the
     * pattern every other mod uses: put a filled container in and the tank takes it, put an empty
     * one in and the tank fills it. There is no direction to set and no button to press.
     *
     * <p><b>The arithmetic is NeoForge's, not ours.</b> {@link FluidUtil#tryEmptyContainer} and
     * {@link FluidUtil#tryFillContainer} are the two helpers, and between them they close every
     * duplication hole this feature has:
     *
     * <ul>
     * <li>Both start with {@code container.copyWithCount(1)}, so a stack of sixteen empty buckets
     *     is sixteen separate exchanges over sixteen ticks and never one transfer multiplied by
     *     sixteen. This method shrinks the in slot by exactly one to match.
     * <li>{@code tryFluidTransfer} fills the destination <em>first</em>, in simulation, and then
     *     drains only the amount the destination said it would take — so a container that
     *     advertises a capacity it does not honour moves what it really moved, not what it claimed.
     * <li>A fluid with no container item at all — a Mekanism gas, which has no bucket — fails the
     *     simulation and returns {@code FluidActionResult.FAILURE}, which lands here as
     *     {@link Hint#REFUSED}: the tank keeps its contents and the player keeps their bucket.
     * </ul>
     *
     * <p>Everything left is ours, and it is all ordering. The exchange is <b>simulated in full
     * before anything commits</b>, because the result container has to have somewhere to go: a
     * bucket emptied into a tank while the out slot is occupied is a bucket that stops existing.
     * And mixing is refused by the machine, not by us — a face that will not take the fluid never
     * gets asked to.
     *
     * @return what to say on the screen about why nothing happened
     */
    Hint exchange(Live live) {
        ItemStack input = exchange.getStackInSlot(EXCHANGE_IN);
        if (input.isEmpty()) {
            return Hint.NONE;
        }
        ItemStack one = input.copyWithCount(1);
        FluidStack offered = FluidUtil.getFluidContained(one).orElse(FluidStack.EMPTY);
        boolean emptying = !offered.isEmpty();
        // The face is chosen by simulating, never by asking. Every Mekanism machine's null side
        // hands out a read-only handler that reports its tanks correctly and then swallows the
        // fill (ProxyHandler: readOnly = side == null), so a screen that binds to the first handler
        // it finds shows the tank, takes the bucket and moves nothing.
        IFluidHandler tanks = emptying
            ? live.reach(Capabilities.FluidHandler.BLOCK,
                handler -> handler.fill(offered, IFluidHandler.FluidAction.SIMULATE) > 0)
            : live.reach(Capabilities.FluidHandler.BLOCK,
                handler -> !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE)
                    .isEmpty());
        // Why it refused, if it refuses — worked out once, because the refusal can come from
        // either the face search or the transfer itself and it means the same thing in both.
        // Not checked before the attempt: a machine with a water tank and a spare empty one may
        // legitimately take the lava, and a screen that says "holds Water" while the lava goes in
        // is worse than one that says nothing.
        Hint refusal = emptying && mismatched(live, offered) ? Hint.MIXED : Hint.REFUSED;
        if (tanks == null) {
            return refusal;
        }
        FluidActionResult simulated = emptying
            ? FluidUtil.tryEmptyContainer(one, tanks, Integer.MAX_VALUE, viewer, false)
            : FluidUtil.tryFillContainer(one, tanks, Integer.MAX_VALUE, viewer, false);
        if (!simulated.isSuccess()) {
            return refusal;
        }
        if (!exchange.insertItem(EXCHANGE_OUT, simulated.getResult(), true).isEmpty()) {
            return Hint.BLOCKED;
        }
        FluidActionResult done = emptying
            ? FluidUtil.tryEmptyContainer(one, tanks, Integer.MAX_VALUE, viewer, true)
            : FluidUtil.tryFillContainer(one, tanks, Integer.MAX_VALUE, viewer, true);
        if (!done.isSuccess()) {
            return Hint.REFUSED;
        }
        exchange.extractItem(EXCHANGE_IN, 1, false);
        // Whatever this refuses would be a leak, and the simulate above proved it will not.
        exchange.insertItem(EXCHANGE_OUT, done.getResult(), false);
        return Hint.NONE;
    }

    /**
     * Whether some tank is holding a fluid that is not the one being offered — the only refusal
     * worth naming, because it is the only one the gauge beside the slots does not already show.
     *
     * <p>Off the live handler rather than off {@link #state}, which is last tick's reading and is
     * wrong for exactly the tick that matters: the one where the exchange just changed what the
     * tank holds.
     */
    private boolean mismatched(Live live, FluidStack offered) {
        IFluidHandler tanks =
            live.reach(Capabilities.FluidHandler.BLOCK, handler -> handler.getTanks() > 0);
        if (tanks == null) {
            return false;
        }
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            FluidStack held = tanks.getFluidInTank(tank);
            if (!held.isEmpty() && !FluidStack.isSameFluidSameComponents(held, offered)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The fluid slots are the menu's own, so closing the screen has to give them back. Vanilla's
     * crafting grid contract, and the same reason: a bucket parked in a slot that stops existing is
     * a bucket that stops existing.
     */
    @Override
    public void removed(Player who) {
        super.removed(who);
        if (who.level().isClientSide()) {
            return;
        }
        for (int slot = 0; slot < exchange.getSlots(); slot++) {
            ItemStack stack = exchange.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                who.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    /**
     * The Workbay must still be there, the player still beside it, and — the one that matters —
     * the bay must still hold the machine this menu was opened on. Ejecting it closes the screen
     * on the next tick rather than leaving a grid of slots pointed at air.
     */
    @Override
    public boolean stillValid(Player who) {
        if (workbay == null) {
            return true;
        }
        if (workbay.isRemoved()) {
            return false;
        }
        BlockPos pos = workbay.getBlockPos();
        if (who.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return false;
        }
        return workbay.record()
            .map(record -> record.bay(bay).hosted().equals(machineId))
            .orElse(false);
    }

    /**
     * Shift-click, written against {@link IItemHandler} alone.
     *
     * <p>Vanilla's {@code moveItemStackTo} is safe for the player's own slots and <b>not</b> for a
     * foreign machine's: it grows the stack {@code getStackInSlot} handed back and calls
     * {@code setChanged}, so a handler that returns a copy — which several do — accepts the shrink
     * on the player's side and keeps nothing on its own. Everything crossing into the machine here
     * goes through {@code insertItem}, and everything leaving it through {@code extractItem}, with
     * whatever will not fit put back before this method returns.
     */
    /**
     * Every click, then whatever the machine would not actually take.
     *
     * <p>{@code Slot#set} returns void, so a write that a foreign handler refuses has no way to
     * hand the remainder back - and by then vanilla has taken the stack off the cursor.
     * {@link Live} parks those in a list instead of dropping them, and this is where they come
     * back. Without it, a read-only handler is an item shredder.
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type,
        Player who) {
        super.clicked(slotId, button, type, who);
        if (machine instanceof Live live) {
            live.drainRefunds(who);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        if (index < machineSlots) {
            return outOfMachine(who, index);
        }
        if (index >= machineSlots + 36) {
            return outOfExchange(index);
        }
        // A fluid container shift-clicked out of the player's own inventory goes to the slot that
        // does something with it, not into whichever machine slot happens to accept buckets.
        if (tanks && FluidUtil.getFluidHandler(slots.get(index).getItem()).isPresent()) {
            ItemStack moved = intoExchange(index);
            if (!moved.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return intoMachine(index);
    }

    /** Out of the fluid slots and into the player's own inventory. Both ends are ours, so vanilla's
     * mover is safe here in a way it is not over a foreign machine's handler. */
    private ItemStack outOfExchange(int index) {
        Slot from = slots.get(index);
        ItemStack held = from.getItem();
        if (held.isEmpty() || !moveItemStackTo(held, machineSlots, machineSlots + 36, true)) {
            return ItemStack.EMPTY;
        }
        from.setChanged();
        return ItemStack.EMPTY;
    }

    /** @return what actually went in, so the caller can fall through when nothing did */
    private ItemStack intoExchange(int index) {
        Slot from = slots.get(index);
        ItemStack held = from.getItem();
        ItemStack refused = exchange.insertItem(EXCHANGE_IN, held.copy(), false);
        int moved = held.getCount() - refused.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }
        from.remove(moved);
        from.setChanged();
        return held.copyWithCount(moved);
    }

    private ItemStack outOfMachine(Player who, int index) {
        int held = machine.getStackInSlot(index).getCount();
        if (held <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack available = machine.extractItem(index, held, true);
        if (available.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = machine.extractItem(index, available.getCount(), false);
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = taken.copy();
        moveItemStackTo(remainder, machineSlots, slots.size(), true);
        if (!remainder.isEmpty()) {
            // The player's inventory would not take all of it. Put it back where it came from, and
            // if the machine now refuses what it just handed over, give it to the player rather
            // than let it stop existing.
            ItemStack refused = ItemHandlerHelper.insertItemStacked(machine, remainder, false);
            if (!refused.isEmpty()) {
                who.getInventory().placeItemBackInInventory(refused);
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack intoMachine(int index) {
        Slot from = slots.get(index);
        ItemStack held = from.getItem();
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack refused = ItemHandlerHelper.insertItemStacked(machine, held.copy(), false);
        int moved = held.getCount() - refused.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }
        from.remove(moved);
        from.setChanged();
        return ItemStack.EMPTY;
    }

    /**
     * The hosted machine's item handler, resolved fresh on every call and never held.
     *
     * <p>The null side first, then the six faces. Null is "no particular side" and is what gives a
     * chest all twenty-seven of its slots and a furnace all three of its own rather than the one
     * that happens to face north — a player reaching in by hand is not a pipe, and the bay's own
     * face config is about what the buses may use, not about what the owner may touch. The faces
     * are the fallback for a machine that answers on nothing else.
     *
     * <p>SPEC.md §9's rule against passing a null side to a foreign handler is a <em>bus</em> rule:
     * a read-only null handler makes a bus report RUNNING while moving nothing, which is the silent
     * failure that rule exists to prevent. Here the handler simply refuses the click and the player
     * sees it refuse. Writes are different, and resolve by simulating — see
     * {@link #clickMenuButton} and {@link Live#reach}.
     */
    static final class Live implements IItemHandlerModifiable {
        private final ServerLevel level;
        private final BlockPos pos;

        /**
         * Items a write could not actually store. Drained back to the player by
         * {@link BayViewMenu#clicked}, because {@code Slot#set} has no way to hand a remainder
         * back and vanilla has already taken the stack off the cursor by the time it is called.
         */
        private final java.util.List<ItemStack> refunds = new java.util.ArrayList<>();

        Live(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        @Nullable
        IItemHandler now() {
            return reach(Capabilities.ItemHandler.BLOCK, handler -> true);
        }

        /**
         * Any capability on the hosted machine: the null side first, then the six faces, and the
         * first one {@code accepts} agrees to.
         *
         * <p>The predicate is the whole difference between a read and a write. A read passes
         * {@code handler -> true} and gets the null side, which is what shows a chest all its slots
         * and a machine all its tanks. A write passes a <em>simulation</em> of the write it is
         * about to make, so a handler that reports everything correctly and then silently declines
         * — every Mekanism machine's null side — is skipped in favour of a face that does not.
         */
        @Nullable
        <T> T reach(BlockCapability<T, @Nullable Direction> capability, Predicate<T> accepts) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            T handler = level.getCapability(capability, pos, null);
            if (handler != null && accepts.test(handler)) {
                return handler;
            }
            for (Direction side : Direction.values()) {
                handler = level.getCapability(capability, pos, side);
                if (handler != null && accepts.test(handler)) {
                    return handler;
                }
            }
            return null;
        }

        /**
         * The tanks and the energy buffer, read fresh. A machine that is gone reads as nothing at
         * all rather than as the last thing it said, which is what makes an ejected machine's
         * gauges go empty instead of lying.
         */
        State read() {
            List<Tank> tanks = new ArrayList<>();
            IFluidHandler fluids =
                reach(Capabilities.FluidHandler.BLOCK, handler -> handler.getTanks() > 0);
            if (fluids != null) {
                for (int tank = 0; tank < Math.min(fluids.getTanks(), MAX_TANKS); tank++) {
                    tanks.add(new Tank(fluids.getFluidInTank(tank).copy(),
                        fluids.getTankCapacity(tank)));
                }
            }
            IEnergyStorage energy =
                reach(Capabilities.EnergyStorage.BLOCK, handler -> handler.getMaxEnergyStored() > 0);
            return new State(List.copyOf(tanks), energy == null ? 0 : energy.getEnergyStored(),
                energy == null ? 0 : energy.getMaxEnergyStored(), Hint.NONE);
        }

        /** The live handler only if it still has this slot; null is "there is nothing to write to". */
        @Nullable
        private IItemHandler at(int slot) {
            IItemHandler handler = now();
            return handler != null && slot >= 0 && slot < handler.getSlots() ? handler : null;
        }

        @Override
        public int getSlots() {
            IItemHandler handler = now();
            return handler == null ? 0 : handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler handler = at(slot);
            return handler == null ? ItemStack.EMPTY : handler.getStackInSlot(slot);
        }

        /** Refusing by handing the whole stack back is what stops a dead machine eating it. */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler handler = at(slot);
            return handler == null ? stack : handler.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler handler = at(slot);
            return handler == null ? ItemStack.EMPTY : handler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler handler = at(slot);
            return handler == null ? 0 : handler.getSlotLimit(slot);
        }

        /**
         * False for a machine that is gone, which is the whole guard: {@code Slot#mayPlace} reads
         * this, and every insert path in {@code doClick} is behind it.
         */
        /**
         * <b>Simulated, not asked.</b> {@code isItemValid} is what a handler <em>says</em>, and at
         * least one major mod says the wrong thing: Mekanism's {@code ProxyItemHandler} answers
         * with the real slot validity even when the handler is read-only
         * ({@code return !readOnly || inventory.isItemValid(...)}), and every Mekanism machine's
         * null side is read-only by construction ({@code ProxyHandler: readOnly = side == null}).
         * Believing it cost sixteen redstone dust in {@code runClient}: {@code mayPlace} said yes,
         * {@code doClick} took the stack off the cursor, and the write went nowhere.
         *
         * <p>A simulated insert cannot lie - it is the same call the real write will make. This is
         * also the guard for a machine that is gone, which is what it was written for.
         */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler handler = at(slot);
            if (handler == null || stack.isEmpty() || !handler.isItemValid(slot, stack)) {
                return false;
            }
            return handler.insertItem(slot, stack.copy(), true).getCount() < stack.getCount();
        }

        /**
         * {@code SlotItemHandler#set} casts to this interface, so it has to exist - but there is no
         * safe way to overwrite a foreign machine's slot, so this never does.
         *
         * <p><b>The {@code IItemHandlerModifiable} fast path is gone on purpose.</b> It was the
         * whole bug: Mekanism's read-only null-side handler <em>is</em> an
         * {@code IItemHandlerModifiable}, and its {@code setStackInSlot} is
         * {@code if (!readOnly) { ... }} - a silent no-op. Delegating to it deleted the stack
         * vanilla had already taken off the cursor.
         *
         * <p>So: take out what is there, put in what was asked for, and count. Anything the handler
         * would not take is pushed to {@link #refunds} rather than dropped, because
         * {@code Slot#set} returns void and the caller has no remainder to give back. The menu
         * drains them into the player at the end of the click.
         */
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            IItemHandler handler = at(slot);
            ItemStack current = handler == null ? ItemStack.EMPTY
                : handler.getStackInSlot(slot).copy();
            if (ItemStack.matches(current, stack)) {
                return;
            }
            // Addition only, and only of what is not already there. Taking items out is
            // extractItem's job and vanilla uses it (Slot#remove) for every removal path, so a
            // write that shrinks or swaps a slot is one this class declines to perform - it never
            // removes, which is what makes it impossible for it to delete. Refusing the whole
            // stack back is also why the old content is never refunded here: on a swap vanilla has
            // already put it on the cursor, and refunding it too would be the dupe.
            int already = ItemStack.isSameItemSameComponents(current, stack) ? current.getCount() : 0;
            int adding = stack.getCount() - already;
            if (adding <= 0) {
                return;
            }
            ItemStack delta = stack.copyWithCount(adding);
            refund(handler == null ? delta : handler.insertItem(slot, delta, false));
        }

        private void refund(ItemStack stack) {
            if (!stack.isEmpty()) {
                refunds.add(stack.copy());
            }
        }

        /** Into the player's own inventory, or at their feet if it is full. Never nowhere. */
        void drainRefunds(Player who) {
            if (refunds.isEmpty()) {
                return;
            }
            java.util.List<ItemStack> owed = java.util.List.copyOf(refunds);
            refunds.clear();
            for (ItemStack stack : owed) {
                who.getInventory().placeItemBackInInventory(stack);
            }
        }
    }
}
