package com.neryos.workbay.bus;

import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
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
            if (!bus.enabled() || !bus.linked()) {
                statuses.put(bus.id(), BusStatus.UNLINKED);
                continue;
            }
            if (step % Math.max(1, bus.speed() / STEP_TICKS) != 0) {
                continue;
            }
            statuses.put(bus.id(), run(level, backshop, record, bus));
        }
    }

    public BusStatus status(UUID busId) {
        return statuses.getOrDefault(busId, BusStatus.IDLE);
    }

    /** Drops every cache. Called when the Workbay is removed, so nothing keeps a level alive. */
    public void invalidate() {
        targetItems.clear();
        targetEnergy.clear();
        machineItems.clear();
        machineEnergy.clear();
    }

    private BusStatus run(ServerLevel level, ServerLevel backshop, WorkbayRecord record, BusConfig bus) {
        GlobalPos target = bus.target().orElseThrow();
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null) {
            return BusStatus.TARGET_MISSING;
        }
        BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), bus.bay());

        return switch (bus.resource()) {
            case ITEM -> runItems(bus, targetLevel, target.pos(), backshop, machinePos);
            case ENERGY -> runEnergy(bus, targetLevel, target.pos(), backshop, machinePos);
            // Fluids use the same shape and are not wired up yet; a bus set to one simply idles
            // rather than pretending to work.
            case FLUID -> BusStatus.IDLE;
        };
    }

    private BusStatus runItems(BusConfig bus, ServerLevel targetLevel, BlockPos targetPos,
        ServerLevel backshop, BlockPos machinePos) {
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
            from = machineEnd.resolve(BusRunner::hasAnything);
            to = targetEnd.resolve(h -> h.getSlots() > 0);
        } else {
            from = targetEnd.resolve(BusRunner::hasAnything);
            to = machineEnd.resolve(h -> h.getSlots() > 0);
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
        ServerLevel backshop, BlockPos machinePos) {
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
            from = machineEnd.resolve(IEnergyStorage::canExtract);
            to = targetEnd.resolve(IEnergyStorage::canReceive);
        } else {
            from = targetEnd.resolve(IEnergyStorage::canExtract);
            to = machineEnd.resolve(IEnergyStorage::canReceive);
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

    /** Kept strictly separate. Collapsing any two turns ordinary behaviour into a bug report. */
    public enum BusStatus {
        RUNNING, IDLE, UNLINKED, TARGET_MISSING, TARGET_NOT_LOADED, TARGET_NO_PORT
    }
}
