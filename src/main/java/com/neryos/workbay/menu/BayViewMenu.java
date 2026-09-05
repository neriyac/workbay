package com.neryos.workbay.menu;

import com.neryos.workbay.bus.BusConfig;

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

import net.minecraft.world.item.Items;

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
    /** Columns in a grouped machine grid. Three keeps both groups and the arrow inside the panel. */
    public static final int GROUP_COLUMNS = 3;

    /**
     * What a slot is <b>for the player of this screen</b>, which is the only thing a generic screen
     * can honestly say. It is read, not guessed: a simulated insert either works or it does not.
     *
     * <ul>
     * <li>{@link #IN} — a plain item can be put here.
     * <li>{@link #FUEL} — a plain item cannot, but a fuel can. A furnace's middle slot.
     * <li>{@link #OUT} — nothing offered can go in, so this slot is one to take from.
     * </ul>
     *
     * <p>The names are the player's view, not the machine's: a machine's own idea of "input" is not
     * exposed by any capability, and every Mekanism machine answers {@link #OUT} to all three
     * probes because its null side refuses every insert. That is why a machine with no {@link #IN}
     * slot at all is drawn as one plain grid instead — a grouping where everything lands in one
     * group is a grouping that says nothing.
     *
     * <p><b>ponytail: probes, not a recipe search.</b> A slot that takes none of them reads OUT even
     * when it would take a potion bottle. A full slot dodges that by being asked about its own
     * contents; an empty filtered one does not. Widen the probes if a machine reads wrong.
     *
     * <p>{@code isItemValid} was tried here and reverted: Mekanism's read-only null side answers it
     * truthfully, but an output slot can hold the item too, so it says yes to every slot and turns
     * "no signal" into a confident wrong answer. `theSlotReadingHoldsAcrossMachines` catches it.
     */
    public enum SlotRole {
        IN, FUEL, OUT;

        public static final StreamCodec<RegistryFriendlyByteBuf, SlotRole> STREAM_CODEC =
            StreamCodec.of((buffer, role) -> buffer.writeVarInt(role.ordinal()),
                buffer -> values()[Math.clamp(buffer.readVarInt(), 0, values().length - 1)]);
    }

    /**
     * Where every machine slot sits, and how tall that leaves the machine area. Pure arithmetic on
     * the roles, so the server and the client compute the same thing from the same list rather than
     * one of them being told.
     */
    public record MachineLayout(int[] xs, int[] ys, int height, boolean grouped) {

        public static MachineLayout of(List<SlotRole> roles) {
            int count = roles.size();
            int[] xs = new int[count];
            int[] ys = new int[count];
            // Grouped only when there is something on *both* sides of the arrow. A barrel is
            // twenty-seven input slots and no output; splitting it puts an arrow next to an empty
            // group, which says the machine has an output it does not have. A machine with no
            // input is the Mekanism-shaped case and reads the same way round.
            boolean grouped = roles.stream().anyMatch(role -> role != SlotRole.OUT)
                && roles.contains(SlotRole.OUT);
            if (!grouped) {
                for (int i = 0; i < count; i++) {
                    xs[i] = GRID_X + (i % COLUMNS) * SLOT_SIZE;
                    ys[i] = GRID_Y + (i / COLUMNS) * SLOT_SIZE;
                }
                return new MachineLayout(xs, ys, Math.max(1, (count + COLUMNS - 1) / COLUMNS)
                    * SLOT_SIZE, false);
            }
            int in = 0;
            int fuel = 0;
            int out = 0;
            for (int i = 0; i < count; i++) {
                switch (roles.get(i)) {
                    case IN -> {
                        xs[i] = GRID_X + (in % GROUP_COLUMNS) * SLOT_SIZE;
                        ys[i] = GRID_Y + (in / GROUP_COLUMNS) * SLOT_SIZE;
                        in++;
                    }
                    case OUT -> {
                        xs[i] = OUT_X + (out % GROUP_COLUMNS) * SLOT_SIZE;
                        ys[i] = GRID_Y + (out / GROUP_COLUMNS) * SLOT_SIZE;
                        out++;
                    }
                    case FUEL -> fuel++;
                }
            }
            // Fuel sits under the input group, which is where a machine puts it and where the eye
            // looks for it. Counted after the others so its row is known.
            int inRows = Math.max(1, (in + GROUP_COLUMNS - 1) / GROUP_COLUMNS);
            int placed = 0;
            for (int i = 0; i < count; i++) {
                if (roles.get(i) == SlotRole.FUEL) {
                    xs[i] = GRID_X + (placed % GROUP_COLUMNS) * SLOT_SIZE;
                    ys[i] = GRID_Y + (inRows + placed / GROUP_COLUMNS) * SLOT_SIZE + FUEL_GAP;
                    placed++;
                }
            }
            int leftRows = inRows + (fuel == 0 ? 0 : (fuel + GROUP_COLUMNS - 1) / GROUP_COLUMNS);
            int outRows = Math.max(1, (out + GROUP_COLUMNS - 1) / GROUP_COLUMNS);
            int height = Math.max(leftRows * SLOT_SIZE + (fuel == 0 ? 0 : FUEL_GAP),
                outRows * SLOT_SIZE);
            return new MachineLayout(xs, ys, height, true);
        }
    }

    /** Where the output group starts, and the air the fuel row gets under the input group. */
    public static final int OUT_X = 104;
    public static final int FUEL_GAP = 6;
    /** The arrow between the two groups, drawn only when the layout is grouped. */
    public static final int ARROW_X = 74;

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

    /**
     * The fluid-container column under the gauges: <b>the in slot, an arrow, the out slot beneath
     * it</b>. Stacked rather than side by side because that is the shape every machine mod in the
     * genre uses for "this goes in, that comes out" — Industrial Foregoing and Mekanism both — and
     * a player reads a column with an arrow down it without being told what it is. Two slots in a
     * row, by contrast, read as two places to put something.
     */
    public static final int EXCHANGE_ROW = 50;
    /**
     * The column sits to the <b>right of the gauges, level with them</b>, not on a row of its own.
     * A gauge is 34 tall and the column 50, so side by side they cost 50 where stacked they cost
     * 94 — and the panel is tall enough already. The gauge is the thing being changed and the slots
     * are what changes it, so they belong in the same eyeline anyway.
     */
    public static final int EXCHANGE_IN_X = 124;
    /** Both slots share a column; the out slot is this far below the in one. */
    public static final int EXCHANGE_OUT_DY = 32;
    /** The arrow between them, and the direction button beside them. Both 12x12 glyphs. */
    public static final int EXCHANGE_ARROW_DY = 19;
    public static final int EXCHANGE_MODE_X = 146;
    /** Level with the in slot, because what it says is what happens to whatever is in that slot. */
    public static final int EXCHANGE_MODE_DY = 0;
    public static final int EXCHANGE_IN = 0;
    public static final int EXCHANGE_OUT = 1;

    /** The one menu button this screen has: flip which way the exchange carries fluid. */
    public static final int MODE_BUTTON = 0;

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

    /** Where every machine slot sits. Computed from the roles, identically on both sides. */
    private final MachineLayout layout;

    private final List<SlotRole> roles;

    /** Which of those this screen may actually write to. Never the same question as the role. */
    private final List<Boolean> writable;

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

    /**
     * Which way the exchange carries, and <b>the player's choice rather than a guess about their
     * intent</b>. It used to be inferred — a container with fluid in it emptied, an empty one
     * filled — which is right until the container is half full, and then it is a coin toss the
     * player cannot call or override. {@link BusConfig.Mode} rather than an enum of this screen's
     * own: INSERT and EXTRACT already mean "into the machine" and "out of it" on every link row,
     * and the same two glyphs are already drawn for them.
     *
     * <p>Lives for as long as the screen does. Reopening starts at INSERT again, which is the
     * common direction — topping a machine up — and is one click from the other.
     */
    private BusConfig.Mode mode = BusConfig.Mode.INSERT;

    /** Tanks and energy, as last read on the server. Never a source of truth on the client. */
    private State state;

    private State sent = State.EMPTY;

    /** Client side: everything the screen needs rides the menu-open buffer, like SPEC.md §4's. */
    public BayViewMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readVarInt(),
            buffer.readOptional(net.minecraft.network.FriendlyByteBuf::readResourceLocation),
            buffer.readList(buf -> SlotRole.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf)),
            buffer.readList(net.minecraft.network.FriendlyByteBuf::readBoolean), null,
            State.STREAM_CODEC.decode(buffer));
    }

    public BayViewMenu(int containerId, Inventory inventory, @Nullable WorkbayBlockEntity workbay,
        int bay, Optional<ResourceLocation> machineId, List<SlotRole> slotRoles,
        List<Boolean> slotWritable, @Nullable IItemHandler machine, State state) {
        super(WBMenus.BAY_VIEW.get(), containerId);
        this.workbay = workbay;
        this.bay = bay;
        this.viewer = inventory.player;
        this.machineId = machineId;
        this.roles = List.copyOf(slotRoles.size() > MAX_SLOTS
            ? slotRoles.subList(0, MAX_SLOTS) : slotRoles);
        this.machineSlots = this.roles.size();
        this.layout = MachineLayout.of(this.roles);
        this.writable = List.copyOf(slotWritable.size() >= this.machineSlots
            ? slotWritable.subList(0, this.machineSlots)
            : java.util.Collections.nCopies(this.machineSlots, Boolean.FALSE));
        this.state = state;
        this.gauges = state.gauges();
        this.tanks = !state.tanks().isEmpty();
        // The client has no capability to reach, so it mirrors into a plain handler and lets the
        // ordinary slot sync fill it. Nothing on the client is ever the source of truth.
        this.machine = machine != null ? machine : new ItemStackHandler(this.machineSlots);

        for (int index = 0; index < this.machineSlots; index++) {
            boolean canWrite = this.writable.get(index);
            addSlot(new SlotItemHandler(this.machine, index,
                layout.xs()[index], layout.ys()[index]) {
                /**
                 * <b>The client has to refuse what the server will refuse.</b> Its mirror is a
                 * plain {@code ItemStackHandler}, whose {@code isItemValid} is always true, so it
                 * predicts a placement the server then declines — and nothing resyncs the slot,
                 * because the server's view never changed. The player is left looking at an item
                 * that is not there. The role is already on the client, and an OUT slot is one
                 * nothing goes into by definition, which is the case that produces every ghost on
                 * a Mekanism machine. OPEN_ISSUES #19.
                 */
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return canWrite && super.mayPlace(stack);
                }
            });
        }

        int inventoryY = inventoryY(layout.height(), gauges, this.tanks);
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
            int y = exchangeY(layout.height(), gauges);
            addSlot(new SlotItemHandler(exchange, EXCHANGE_IN, EXCHANGE_IN_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return FluidUtil.getFluidHandler(stack).isPresent();
                }
            });
            addSlot(new SlotItemHandler(exchange, EXCHANGE_OUT, EXCHANGE_IN_X,
                y + EXCHANGE_OUT_DY) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
    }


    /** Where the tank and energy gauges start: straight under the item grid. */
    public static int gaugesY(int machineHeight) {
        return GRID_Y + machineHeight + 6;
    }

    /**
     * The fluid-container row, straight under the gauges. Its two slots are what a player actually
     * uses to fill a hosted tank, so they sit beside the thing they change.
     */
    public static int exchangeY(int machineHeight, int gauges) {
        return gaugesY(machineHeight);
    }

    /**
     * How tall the gauges and the exchange column are together. Whichever is taller decides, since
     * they sit side by side.
     */
    public static int gaugesBlock(int gauges, boolean tanks) {
        return Math.max(gauges * GAUGE_PITCH, tanks ? EXCHANGE_ROW : 0);
    }

    /** The one line under the block saying what the slots are for, or why they refused. */
    public static final int HINT_ROW = 12;

    /** And the limits sentence under those, so gauges push it down rather than sit on top of it. */
    public static int limitsY(int machineHeight, int gauges, boolean tanks) {
        return gaugesY(machineHeight) + gaugesBlock(gauges, tanks) + (tanks ? HINT_ROW : 0);
    }

    /**
     * Where the player's own inventory starts, straight under the limits line. SPEC.md §5 requires
     * a permanent line naming what this screen cannot reach.
     *
     * <p><b>Whatever this reserves, the line must fit in.</b> It was once twenty pixels for a
     * sentence that wraps to four lines, and it ran straight through the Inventory label and the
     * player's own top row — seen in {@code runClient}, the same class of fault as the skim chip
     * that overran the link name. The sentence now lives in a tooltip and one line is drawn.
     */
    public static int inventoryY(int machineHeight, int gauges, boolean tanks) {
        return limitsY(machineHeight, gauges, tanks) + LIMITS_ROW;
    }

    /**
     * One drawn line, with the sentence in its tooltip. It used to be fifty-four pixels, because
     * the sentence wraps to <b>four</b> lines at this width — the largest block on a panel whose
     * point is that it is small. SPEC.md §5 asks for a permanent line, and permanent means always
     * present rather than always spelled out.
     *
     * <p>Twenty-two rather than the fourteen a nine-pixel line needs, because the Inventory label
     * is drawn ten pixels <em>above</em> {@code inventoryY}: anything under twenty-one puts this
     * line straight through it. Fourteen did exactly that, and it took a screenshot to see.
     */
    public static final int LIMITS_ROW = 22;

    public static int height(int machineHeight, int gauges, boolean tanks) {
        return inventoryY(machineHeight, gauges, tanks) + 58 + SLOT_SIZE + 7;
    }

    public int gauges() {
        return gauges;
    }

    public int inventoryY() {
        return inventoryY(layout.height(), gauges, tanks);
    }

    public int limitsY() {
        return limitsY(layout.height(), gauges, tanks);
    }

    public int exchangeY() {
        return exchangeY(layout.height(), gauges);
    }

    /** Whether the fluid-container row is on this panel at all. */
    public boolean hasTanks() {
        return tanks;
    }

    public int height() {
        return height(layout.height(), gauges, tanks);
    }

    public State state() {
        return state;
    }

    /** Server to client, and the client's only source for what is in the tanks. */
    public void applyState(State updated) {
        this.state = updated;
    }

    public MachineLayout layout() {
        return layout;
    }

    public List<SlotRole> roles() {
        return roles;
    }

    public int gaugesY() {
        return gaugesY(layout.height());
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
    public record Opening(Optional<ResourceLocation> machineId, List<SlotRole> roles,
        List<Boolean> writable, Live handler, State state) {

        public int slots() {
            return roles.size();
        }
    }

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
        REFUSED,
        /**
         * Nothing moved, but flipping the direction button would have moved something. Worked out
         * by simulating the other way round rather than by guessing from what the container holds,
         * so it fires exactly when the toggle is the answer and never otherwise.
         *
         * <p>Its own value because a direction the player sets is a direction the player can set
         * <em>wrong</em>, and "nothing to exchange" over a full bucket is the same fault as a dead
         * link reading IDLE. The button is the fix and the line has to say so.
         */
        WRONG_WAY;

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
    /** Clamped rather than trusted, exactly as {@link Hint#STREAM_CODEC} is. */
    public static final StreamCodec<RegistryFriendlyByteBuf, BusConfig.Mode> MODE_STREAM_CODEC =
        StreamCodec.of((buffer, mode) -> buffer.writeVarInt(mode.ordinal()),
            buffer -> BusConfig.Mode.values()[
                Math.clamp(buffer.readVarInt(), 0, BusConfig.Mode.values().length - 1)]);

    public record State(List<Tank> tanks, int energy, int energyCapacity, Hint hint,
        BusConfig.Mode mode) {

        public static final State EMPTY =
            new State(List.of(), 0, 0, Hint.NONE, BusConfig.Mode.INSERT);

        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
            StreamCodec.composite(
                Tank.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TANKS)), State::tanks,
                ByteBufCodecs.VAR_INT, State::energy,
                ByteBufCodecs.VAR_INT, State::energyCapacity,
                Hint.STREAM_CODEC, State::hint,
                MODE_STREAM_CODEC, State::mode,
                State::new);

        public State withHint(Hint updated) {
            return hint == updated ? this : new State(tanks, energy, energyCapacity, updated, mode);
        }

        public State withMode(BusConfig.Mode updated) {
            return mode == updated ? this : new State(tanks, energy, energyCapacity, hint, updated);
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
                || hint != other.hint || mode != other.mode
                || tanks.size() != other.tanks.size()) {
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
        return new Opening(record.bay(bay).hosted(), classify(now), writable(now), live, state);
    }

    /**
     * What each slot is, read off the handler rather than assumed. Three probes: a plain item, a
     * second plain item in case the first is filtered, and a fuel. A slot that takes a plain item
     * is one the player can put things in; one that takes only the fuel is a fuel slot; one that
     * takes none of them is one to take from.
     *
     * <p>Simulated inserts, never {@code isItemValid} — that is what a handler <em>says</em>, and
     * SPEC.md §9 is built on it being wrong. The same rule the buses bind on.
     */
    private static List<SlotRole> classify(@Nullable IItemHandler handler) {
        if (handler == null) {
            return List.of();
        }
        List<SlotRole> roles = new ArrayList<>();
        for (int slot = 0; slot < Math.min(handler.getSlots(), MAX_SLOTS); slot++) {
            roles.add(role(handler, slot));
        }
        return List.copyOf(roles);
    }

    /**
     * Which slots this screen can actually put something into, and what {@code mayPlace} is built
     * on at both ends. Separate from the role on purpose: a role is a guess about what a slot is
     * <em>for</em> and being wrong draws a box in the wrong place, while this decides whether the
     * client is allowed to predict a placement — being wrong here leaves an item drawn in a slot
     * that never received it. Both ask the same simulated insert; neither asks {@code isItemValid}.
     */
    private static List<Boolean> writable(@Nullable IItemHandler handler) {
        if (handler == null) {
            return List.of();
        }
        List<Boolean> can = new ArrayList<>();
        for (int slot = 0; slot < Math.min(handler.getSlots(), MAX_SLOTS); slot++) {
            ItemStack held = handler.getStackInSlot(slot);
            can.add(!held.isEmpty()
                ? takes(handler, slot, held.copyWithCount(1))
                : takes(handler, slot, new ItemStack(Items.IRON_INGOT))
                    || takes(handler, slot, new ItemStack(Items.COBBLESTONE))
                    || takes(handler, slot, new ItemStack(Items.REDSTONE))
                    || takes(handler, slot, new ItemStack(Items.COAL)));
        }
        return List.copyOf(can);
    }

    /**
     * <b>What is already in the slot is the best probe there is.</b> A slot that will not take back
     * a copy of its own contents cannot be one you put things into — that is true whatever the
     * machine, whatever its recipe, and it needs no guess about what the machine eats. It is what
     * separates a filtered input from an output, which a fixed probe list cannot: EnderIO gates an
     * output slot on {@code layout.canInsert} and a *filtered input* on a recipe-driven
     * {@code isItemValid}, and both refuse iron.
     *
     * <p>An empty slot has nothing to ask about, so it falls back to the probe items, and the
     * probes are also what tell a fuel slot from an input.
     */
    private static SlotRole role(IItemHandler handler, int slot) {
        ItemStack held = handler.getStackInSlot(slot);
        if (!held.isEmpty()) {
            return takes(handler, slot, held.copyWithCount(1)) ? SlotRole.IN : SlotRole.OUT;
        }
        if (takes(handler, slot, new ItemStack(Items.IRON_INGOT))
            || takes(handler, slot, new ItemStack(Items.COBBLESTONE))
            || takes(handler, slot, new ItemStack(Items.REDSTONE))) {
            return SlotRole.IN;
        }
        return takes(handler, slot, new ItemStack(Items.COAL)) ? SlotRole.FUEL : SlotRole.OUT;
    }

    private static boolean takes(IItemHandler handler, int slot, ItemStack one) {
        return handler.insertItem(slot, one, true).getCount() < one.getCount();
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
                opening.machineId(), opening.roles(), opening.writable(), opening.handler(),
                opening.state()),
            com.neryos.workbay.WorkbayLang.gui("bayview.title", bay + 1)),
            buffer -> {
                buffer.writeVarInt(bay);
                buffer.writeOptional(opening.machineId(),
                    (buf, value) -> buf.writeResourceLocation(value));
                buffer.writeCollection(opening.roles(),
                    (buf, role) -> SlotRole.STREAM_CODEC.encode(
                        (RegistryFriendlyByteBuf) buf, role));
                buffer.writeCollection(opening.writable(),
                    net.minecraft.network.FriendlyByteBuf::writeBoolean);
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
            state = live.read().withHint(hint).withMode(mode);
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
        boolean emptying = mode == BusConfig.Mode.INSERT;
        IFluidHandler tanks = reach(live, one, emptying);
        FluidActionResult simulated = tanks == null ? FluidActionResult.FAILURE
            : attempt(one, tanks, emptying, false);
        if (!simulated.isSuccess()) {
            // Nothing moved. Before saying so, ask whether the *other* direction would have — a
            // simulation, not a guess from what the container holds, so this fires exactly when
            // pressing the button is the fix. A player who has just set the direction wrong is the
            // one most likely to be looking at this line.
            IFluidHandler other = reach(live, one, !emptying);
            if (other != null && attempt(one, other, !emptying, false).isSuccess()) {
                return Hint.WRONG_WAY;
            }
            // Not checked before the attempt: a machine with a water tank and a spare empty one may
            // legitimately take the lava, and a screen that says "holds Water" while the lava goes
            // in is worse than one that says nothing.
            return emptying && mismatched(live, FluidUtil.getFluidContained(one)
                .orElse(FluidStack.EMPTY)) ? Hint.MIXED : Hint.REFUSED;
        }
        if (!exchange.insertItem(EXCHANGE_OUT, simulated.getResult(), true).isEmpty()) {
            return Hint.BLOCKED;
        }
        FluidActionResult done = attempt(one, tanks, emptying, true);
        if (!done.isSuccess()) {
            return Hint.REFUSED;
        }
        exchange.extractItem(EXCHANGE_IN, 1, false);
        // Whatever this refuses would be a leak, and the simulate above proved it will not.
        exchange.insertItem(EXCHANGE_OUT, done.getResult(), false);
        return Hint.NONE;
    }

    /**
     * The face this direction can actually use. <b>Chosen by simulating, never by asking.</b> Every
     * Mekanism machine's null side hands out a read-only handler that reports its tanks correctly
     * and then swallows the fill ({@code ProxyHandler: readOnly = side == null}), so a screen that
     * binds to the first handler it finds shows the tank, takes the bucket and moves nothing.
     */
    @Nullable
    private IFluidHandler reach(Live live, ItemStack one, boolean emptying) {
        if (emptying) {
            FluidStack offered = FluidUtil.getFluidContained(one).orElse(FluidStack.EMPTY);
            if (offered.isEmpty()) {
                return null;
            }
            return live.reach(Capabilities.FluidHandler.BLOCK,
                handler -> handler.fill(offered, IFluidHandler.FluidAction.SIMULATE) > 0);
        }
        return live.reach(Capabilities.FluidHandler.BLOCK,
            handler -> !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE)
                .isEmpty());
    }

    /** One direction of the exchange, simulated or committed. NeoForge owns the arithmetic. */
    private FluidActionResult attempt(ItemStack one, IFluidHandler tanks, boolean emptying,
        boolean commit) {
        return emptying
            ? FluidUtil.tryEmptyContainer(one, tanks, Integer.MAX_VALUE, viewer, commit)
            : FluidUtil.tryFillContainer(one, tanks, Integer.MAX_VALUE, viewer, commit);
    }

    /**
     * The direction button. Vanilla's channel rather than a payload of our own: the click carries
     * no argument, and {@code clickMenuButton} is already validated, container-id checked and
     * spectator-guarded by {@code ServerGamePacketListenerImpl}.
     */
    @Override
    public boolean clickMenuButton(Player who, int id) {
        if (id != MODE_BUTTON || !stillValid(who)) {
            return false;
        }
        mode = mode.flip();
        state = state.withMode(mode);
        return true;
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
            // The hint and the direction are the menu's, not the machine's; tick() withers them
            // straight back on. Reading them here would mean Live knowing about a button.
            return new State(List.copyOf(tanks), energy == null ? 0 : energy.getEnergyStored(),
                energy == null ? 0 : energy.getMaxEnergyStored(), Hint.NONE,
                BusConfig.Mode.INSERT);
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
