package com.neryos.workbay.bus;

import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.RedstoneMode;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Runs one Workbay's buses. SPEC.md §9.
 *
 * <p>Holds the endpoint caches, which is why it is one object per Workbay rather than a pile of
 * statics: a cache is registered with the level it points at and has to be dropped when the Workbay
 * that owns it goes away.
 */
public class BusRunner {

    /**
     * SPEC.md §9's wheel. One countdown from 1200, acting every fifth tick, giving 240 steps. Every
     * legal speed divides it, which is why speeds come from a fixed list rather than free entry.
     */
    public static final int WHEEL = 1200;
    public static final int STEP_TICKS = 5;

    private final BooleanSupplier alive;
    private final Map<UUID, BusEndpoint<IItemHandler>> targetItems = new HashMap<>();
    private final Map<UUID, BusEndpoint<IEnergyStorage>> targetEnergy = new HashMap<>();
    private final Map<Integer, BusEndpoint<IItemHandler>> machineItems = new HashMap<>();
    private final Map<Integer, BusEndpoint<IEnergyStorage>> machineEnergy = new HashMap<>();
    private final Map<UUID, BusStatus> statuses = new HashMap<>();

    private static final java.util.Set<Direction> EVERY_FACE =
        java.util.EnumSet.allOf(Direction.class);

    private int delay = WHEEL;

    /** SPEC.md §4's redstone gate. Edge detection lives here so the block entity stays a handle. */
    private boolean powered;
    private boolean pulseArmed;

    public BusRunner(BooleanSupplier alive) {
        this.alive = alive;
    }

    /**
     * @param offset derived from the Workbay's BlockPos so that Workbays stagger instead of every
     *               one in a base firing on the same tick
     */
    /**
     * The redstone signal at the Workbay, and the rising edge {@link RedstoneMode#PULSE} spends.
     * Called every tick whether or not any bus runs, or an edge that lands between two wheel steps
     * is never seen.
     */
    public void power(boolean nowPowered) {
        if (nowPowered && !powered) {
            pulseArmed = true;
        }
        powered = nowPowered;
    }

    public void tick(ServerLevel level, WorkbayRecord record, Iterable<BusConfig> buses, int offset) {
        if (--delay < 0) {
            delay = WHEEL - 1;
        }
        int phase = (delay + offset) % WHEEL;
        if (phase % STEP_TICKS != 0) {
            return;
        }
        int step = phase / STEP_TICKS;
        boolean spentPulse = false;

        ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null) {
            return;
        }
        for (BusConfig bus : buses) {
            if (!bus.enabled()) {
                statuses.put(bus.id(), BusStatus.DISABLED);
                continue;
            }
            if (step % Math.max(1, bus.speed() / STEP_TICKS) != 0) {
                continue;
            }
            RedstoneMode gate = record.bay(bus.bay()).redstone();
            if (!gate.allows(powered, pulseArmed)) {
                statuses.put(bus.id(), BusStatus.HELD_BY_REDSTONE);
                continue;
            }
            spentPulse |= gate == RedstoneMode.PULSE;
            statuses.put(bus.id(), run(level, backshop, record, bus));
        }
        // One operation per rising edge, spent only once something on PULSE actually got its turn.
        if (spentPulse) {
            pulseArmed = false;
        }
    }

    /**
     * Links whose Connector has gone, discovered while ticking. Reported rather than acted on here,
     * because a runner must not mutate the list it is iterating; the block entity sweeps them.
     */
    public java.util.Set<UUID> orphaned() {
        return statuses.entrySet().stream()
            .filter(e -> e.getValue() == BusStatus.CONNECTOR_GONE)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }

    public BusStatus status(UUID busId) {
        return statuses.getOrDefault(busId, BusStatus.IDLE);
    }

    /** Forgets one link's caches and status, so a removed link stops holding a level reference. */
    public void forget(UUID busId) {
        targetItems.remove(busId);
        targetEnergy.remove(busId);
        statuses.remove(busId);
    }

    /** Drops one bay's machine-end caches, so a changed face config is re-resolved rather than kept. */
    public void forgetBay(int bay) {
        machineItems.remove(bay);
        machineEnergy.remove(bay);
    }

    /** Drops every cache. Called when the Workbay is removed, so nothing keeps a level alive. */
    public void invalidate() {
        targetItems.clear();
        targetEnergy.clear();
        machineItems.clear();
        machineEnergy.clear();
    }

    private BusStatus run(ServerLevel level, ServerLevel backshop, WorkbayRecord record, BusConfig bus) {
        GlobalPos target = bus.target();
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null) {
            return BusStatus.TARGET_MISSING;
        }
        // A link is its Connector. If that chunk is loaded and the block is not there any more, the
        // Connector was broken while this Workbay was unloaded and could not be told. Only ever
        // asked of a chunk already loaded: getBlockState on an unloaded one loads it synchronously.
        ServerLevel connectorLevel = level.getServer().getLevel(bus.connector().dimension());
        if (connectorLevel != null && connectorLevel.isLoaded(bus.connector().pos())
            && !connectorLevel.getBlockState(bus.connector().pos()).is(WBBlocks.CONNECTOR.get())) {
            return BusStatus.CONNECTOR_GONE;
        }
        BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), bus.bay());

        // Which of the hosted machine's faces this link may use, from the bay's own face config.
        // Unconfigured means every face, so a bay nobody has opened the screen for behaves exactly
        // as it did before the config existed.
        java.util.Set<Direction> faces = record.bay(bus.bay()).faces()
            .usable(bus.resource(), bus.mode() == BusConfig.Mode.INSERT);
        // Configured, but not for this direction of travel. Reported here rather than left to fall
        // through as IDLE: a link that can never move anything is not resting, and telling the
        // player nothing is wrong while their face config silently kills the link is the worst
        // outcome the cube can have. Found in play, then reproduced by
        // aFaceConfigThatBlocksALinkIsReported.
        if (faces.isEmpty()) {
            return BusStatus.MACHINE_NO_FACE;
        }

        return switch (bus.resource()) {
            case ITEM -> runItems(bus, targetLevel, target.pos(), backshop, machinePos, faces);
            case ENERGY -> runEnergy(bus, targetLevel, target.pos(), backshop, machinePos, faces);
            // Fluids use the same shape and are not wired up yet. Reported, not idled: the row's
            // resource icon is one click away from the mode icon, so a link cycled to fluids by
            // accident spent a session looking like a link with nothing to do.
            case FLUID -> BusStatus.RESOURCE_NOT_CARRIED;
        };
    }

    private BusStatus runItems(BusConfig bus, ServerLevel targetLevel, BlockPos targetPos,
        ServerLevel backshop, BlockPos machinePos, java.util.Set<Direction> faces) {
        BusEndpoint<IItemHandler> targetEnd = targetItems.computeIfAbsent(bus.id(), id ->
            new BusEndpoint<>(Capabilities.ItemHandler.BLOCK, targetLevel, targetPos, alive,
                bus.targetFace().orElse(null)));
        BusEndpoint<IItemHandler> machineEnd = machineItems.computeIfAbsent(bus.bay(), b ->
            new BusEndpoint<>(Capabilities.ItemHandler.BLOCK, backshop, machinePos, alive,
                bus.machineFace().orElse(null)));

        if (!targetEnd.targetLoaded()) {
            return BusStatus.TARGET_NOT_LOADED;
        }
        boolean insert = bus.mode() == BusConfig.Mode.INSERT;
        BusEndpoint<IItemHandler> source = insert ? machineEnd : targetEnd;
        BusEndpoint<IItemHandler> sink = insert ? targetEnd : machineEnd;
        java.util.Set<Direction> sourceFaces = insert ? faces : EVERY_FACE;
        java.util.Set<Direction> sinkFaces = insert ? EVERY_FACE : faces;

        IItemHandler to = sink.resolve(h -> h.getSlots() > 0, sinkFaces);
        if (to == null) {
            return insert ? BusStatus.TARGET_NO_PORT : BusStatus.MACHINE_NO_PORT;
        }
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> allowed = allowed(bus);
        IItemHandler from = source.resolve(h -> hasAnything(h, allowed), sourceFaces);
        if (from == null) {
            // Nothing came out. Two very different reasons, and one message for both is how a
            // dead link spends a session looking like a resting one.
            boolean anyHandler = source.resolve(h -> h.getSlots() > 0, sourceFaces) != null;
            return anyHandler ? BusStatus.IDLE
                : insert ? BusStatus.MACHINE_NO_PORT : BusStatus.TARGET_NO_PORT;
        }
        return BusTransfer.moveItems(from, to, bus.rate(), allowed) > 0
            ? BusStatus.RUNNING : BusStatus.IDLE;
    }

    /**
     * The link's ghost item, as a predicate. One item is the whole filter today; SPEC.md §5's
     * filter items widen this to nine, eighteen or thirty-six entries with component matching, and
     * they widen exactly here.
     */
    private static java.util.function.Predicate<net.minecraft.world.item.ItemStack> allowed(
        BusConfig bus) {
        if (bus.filter().isEmpty()) {
            return stack -> true;
        }
        var wanted = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(bus.filter().get());
        return stack -> stack.is(wanted);
    }

    private BusStatus runEnergy(BusConfig bus, ServerLevel targetLevel, BlockPos targetPos,
        ServerLevel backshop, BlockPos machinePos, java.util.Set<Direction> faces) {
        BusEndpoint<IEnergyStorage> targetEnd = targetEnergy.computeIfAbsent(bus.id(), id ->
            new BusEndpoint<>(Capabilities.EnergyStorage.BLOCK, targetLevel, targetPos, alive,
                bus.targetFace().orElse(null)));
        BusEndpoint<IEnergyStorage> machineEnd = machineEnergy.computeIfAbsent(bus.bay(), b ->
            new BusEndpoint<>(Capabilities.EnergyStorage.BLOCK, backshop, machinePos, alive,
                bus.machineFace().orElse(null)));

        if (!targetEnd.targetLoaded()) {
            return BusStatus.TARGET_NOT_LOADED;
        }
        boolean insert = bus.mode() == BusConfig.Mode.INSERT;
        BusEndpoint<IEnergyStorage> source = insert ? machineEnd : targetEnd;
        BusEndpoint<IEnergyStorage> sink = insert ? targetEnd : machineEnd;
        java.util.Set<Direction> sourceFaces = insert ? faces : EVERY_FACE;
        java.util.Set<Direction> sinkFaces = insert ? EVERY_FACE : faces;

        IEnergyStorage to = sink.resolve(IEnergyStorage::canReceive, sinkFaces);
        if (to == null) {
            return insert ? BusStatus.TARGET_NO_PORT : BusStatus.MACHINE_NO_PORT;
        }
        IEnergyStorage from = source.resolve(IEnergyStorage::canExtract, sourceFaces);
        if (from == null) {
            boolean anyHandler = source.resolve(store -> true, sourceFaces) != null;
            return anyHandler ? BusStatus.IDLE
                : insert ? BusStatus.MACHINE_NO_PORT : BusStatus.TARGET_NO_PORT;
        }
        return BusTransfer.moveEnergy(from, to, bus.rate()) > 0 ? BusStatus.RUNNING : BusStatus.IDLE;
    }

    /**
     * A source only counts if something could actually come out of it. Binding to the first handler
     * that merely exists is how a bus ends up wired to a read-only face and moves nothing forever.
     */
    private static boolean hasAnything(IItemHandler handler,
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> allowed) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack sample = handler.extractItem(slot, 1, true);
            if (!sample.isEmpty() && allowed.test(sample)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kept strictly separate. Collapsing any two turns ordinary behaviour into a bug report, and
     * SPEC.md §4 requires the three failing ones to be visually distinct on the LINKS row.
     */
    public enum BusStatus {
        RUNNING, IDLE, DISABLED,
        /** Waiting on the bay's redstone mode. The player's own instruction, not a fault. */
        HELD_BY_REDSTONE,
        CONNECTOR_GONE, TARGET_MISSING, TARGET_NOT_LOADED, TARGET_NO_PORT,
        /** The hosted machine answers on none of the faces this link may use. */
        MACHINE_NO_PORT,
        /** Set to a resource this build does not move. Fluids, today. */
        RESOURCE_NOT_CARRIED,
        /** The bay's cube has faces set, but none for this link's direction of travel. */
        MACHINE_NO_FACE;

        /** True for a status the player has to do something about. Drives the problem count. */
        public boolean isProblem() {
            return this != RUNNING && this != IDLE && this != DISABLED && this != HELD_BY_REDSTONE;
        }
    }
}
