package com.neryos.workbay.bus;

import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.BayGeometry;
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

    private int delay = WHEEL;

    public BusRunner(BooleanSupplier alive) {
        this.alive = alive;
    }

    /**
     * @param offset derived from the Workbay's BlockPos so that Workbays stagger instead of every
     *               one in a base firing on the same tick
     */
    public void tick(ServerLevel level, WorkbayRecord record, Iterable<BusConfig> buses, int offset) {
        if (--delay < 0) {
            delay = WHEEL - 1;
        }
        int phase = (delay + offset) % WHEEL;
        if (phase % STEP_TICKS != 0) {
            return;
        }
        int step = phase / STEP_TICKS;

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
            statuses.put(bus.id(), run(level, backshop, record, bus));
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

        return switch (bus.resource()) {
            case ITEM -> runItems(bus, targetLevel, target.pos(), backshop, machinePos, faces);
            case ENERGY -> runEnergy(bus, targetLevel, target.pos(), backshop, machinePos, faces);
            // Fluids use the same shape and are not wired up yet; a bus set to one simply idles
            // rather than pretending to work.
            case FLUID -> BusStatus.IDLE;
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
        IItemHandler from;
        IItemHandler to;
        if (bus.mode() == BusConfig.Mode.INSERT) {
            from = machineEnd.resolve(BusRunner::hasAnything, faces);
            to = targetEnd.resolve(h -> h.getSlots() > 0);
        } else {
            from = targetEnd.resolve(BusRunner::hasAnything);
            to = machineEnd.resolve(h -> h.getSlots() > 0, faces);
        }
        if (to == null) {
            return BusStatus.TARGET_NO_PORT;
        }
        if (from == null) {
            return BusStatus.IDLE;
        }
        return BusTransfer.moveItems(from, to, bus.rate()) > 0 ? BusStatus.RUNNING : BusStatus.IDLE;
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
        IEnergyStorage from;
        IEnergyStorage to;
        if (bus.mode() == BusConfig.Mode.INSERT) {
            from = machineEnd.resolve(IEnergyStorage::canExtract, faces);
            to = targetEnd.resolve(IEnergyStorage::canReceive);
        } else {
            from = targetEnd.resolve(IEnergyStorage::canExtract);
            to = machineEnd.resolve(IEnergyStorage::canReceive, faces);
        }
        if (to == null) {
            return BusStatus.TARGET_NO_PORT;
        }
        if (from == null) {
            return BusStatus.IDLE;
        }
        return BusTransfer.moveEnergy(from, to, bus.rate()) > 0 ? BusStatus.RUNNING : BusStatus.IDLE;
    }

    /**
     * A source only counts if something could actually come out of it. Binding to the first handler
     * that merely exists is how a bus ends up wired to a read-only face and moves nothing forever.
     */
    private static boolean hasAnything(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.extractItem(slot, 1, true).isEmpty()) {
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
        RUNNING, IDLE, DISABLED, CONNECTOR_GONE, TARGET_MISSING, TARGET_NOT_LOADED, TARGET_NO_PORT;

        /** True for a status the player has to do something about. Drives the problem count. */
        public boolean isProblem() {
            return this == CONNECTOR_GONE || this == TARGET_MISSING || this == TARGET_NOT_LOADED
                || this == TARGET_NO_PORT;
        }
    }
}
