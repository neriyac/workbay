package com.neryos.workbay.content.workbay;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Workbay. In v1 it is a handle: the id of a {@link WorkbayRecord} in the {@link RoomRegistry},
 * which is where the bays, the code and the upgrade counters actually live.
 *
 * <p>Keeping the state in the registry rather than here is deliberate. A Workbay can be broken while
 * its machines keep running (SPEC.md §14), so the record has to outlive the block entity, and the
 * block entity must never be the only copy of anything except the id.
 */
public class WorkbayBlockEntity extends BlockEntity {
    private static final String ID_KEY = "WorkbayId";
    private static final String BUSES_KEY = "Buses";
    private static final String ENERGY_KEY = "Energy";

    @Nullable
    private UUID workbayId;

    private final List<BusConfig> buses = new ArrayList<>();
    private final BusRunner runner = new BusRunner(() -> !isRemoved());

    /**
     * SPEC.md §9's buffer, accepted on any face and never handing energy back out of the block.
     * The three-layer spend model and round-robin sharing to bays are not built yet, so this is a
     * real buffer the screen reads rather than a number invented for a progress bar.
     */
    private final EnergyStorage energy = new EnergyStorage(BUFFER_FE, MAX_FE_PER_TICK, 0);

    public static final int BUFFER_FE = 100_000;
    public static final int MAX_FE_PER_TICK = 10_000;

    public WorkbayBlockEntity(BlockPos pos, BlockState state) {
        super(WBBlockEntities.WORKBAY.get(), pos, state);
    }

    public Optional<UUID> workbayId() {
        return Optional.ofNullable(workbayId);
    }

    public void bindTo(UUID id) {
        this.workbayId = id;
        setChanged();
    }

    /** The record this block is a handle for, or empty if it has not been bound yet. */
    public Optional<WorkbayRecord> record() {
        if (workbayId == null || !(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return RoomRegistry.get(server.getServer()).byId(workbayId);
    }

    /** How many bays currently hold something. Also what a comparator reads (SPEC.md §7). */
    public int occupiedBays() {
        return record().map(r -> (int) r.bays().stream().filter(b -> b.hosted().isPresent()).count())
            .orElse(0);
    }

    /**
     * Keeps the registry's idea of where this Workbay is up to date, so {@code /workbay list} can
     * point an operator at it and a re-placed item can be told apart from a duplicated one.
     */
    public void rememberPosition() {
        if (!(level instanceof ServerLevel server) || workbayId == null) {
            return;
        }
        RoomRegistry registry = RoomRegistry.get(server.getServer());
        registry.byId(workbayId).ifPresent(record ->
            registry.put(record.withLastKnownPos(GlobalPos.of(server.dimension(), worldPosition))));
    }

    public List<BusConfig> buses() {
        return List.copyOf(buses);
    }

    public void addBus(BusConfig bus) {
        buses.removeIf(existing -> existing.id().equals(bus.id()));
        buses.add(bus);
        setChanged();
    }

    public void removeBus(UUID busId) {
        if (buses.removeIf(bus -> bus.id().equals(busId))) {
            runner.forget(busId);
            setChanged();
        }
    }

    public Optional<BusConfig> bus(UUID busId) {
        return buses.stream().filter(bus -> bus.id().equals(busId)).findFirst();
    }

    /** Every link anchored by the Connector at one position. Base is one; Multichannel allows three. */
    public List<BusConfig> linksAt(GlobalPos connector) {
        return buses.stream().filter(bus -> bus.connector().equals(connector)).toList();
    }

    /** Breaking a Connector takes its links with it. SPEC.md §0: the block <em>is</em> the link. */
    public void removeLinksAt(GlobalPos connector) {
        List<BusConfig> going = linksAt(connector);
        if (going.isEmpty()) {
            return;
        }
        going.forEach(bus -> runner.forget(bus.id()));
        buses.removeAll(going);
        setChanged();
    }

    public EnergyStorage energy() {
        return energy;
    }

    public BusRunner.BusStatus busStatus(UUID busId) {
        return runner.status(busId);
    }

    /**
     * SPEC.md §9's tick wheel, offset by the Workbay's own position so that a base full of them
     * staggers instead of every one firing on the same tick.
     */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
        BlockState state, WorkbayBlockEntity workbay) {
        if (!(level instanceof ServerLevel server) || workbay.buses.isEmpty()) {
            return;
        }
        workbay.record().ifPresent(record ->
            workbay.runner.tick(server, record, workbay.buses, Math.floorMod(pos.hashCode(), BusRunner.WHEEL)));

        // A Connector broken while this Workbay was unloaded could not tell it, so the runner spots
        // the gap instead and the link is swept here, outside the iteration that found it.
        var orphaned = workbay.runner.orphaned();
        if (!orphaned.isEmpty()) {
            orphaned.forEach(workbay::removeBus);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Every endpoint cache holds a ServerLevel reference. Dropping them here is what stops a
        // removed Workbay keeping another dimension's level object alive.
        runner.invalidate();
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(RoomRegistry.VERSION_KEY, RoomRegistry.DATA_VERSION);
        if (workbayId != null) {
            tag.put(ID_KEY, UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, workbayId)
                .getOrThrow(e -> new IllegalStateException("could not write a Workbay id: " + e)));
        }
        if (!buses.isEmpty()) {
            tag.put(BUSES_KEY, BusConfig.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(buses))
                .getOrThrow(e -> new IllegalStateException("could not write a Workbay's buses: " + e)));
        }
        tag.putInt(ENERGY_KEY, energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // SPEC.md §14: DataVersion is the first key of every structure this mod persists, and
        // branching on it here is the entire migration mechanism. A tag with no version is one
        // written before the block entity ever saved, which is simply an unbound Workbay.
        int version = tag.getInt(RoomRegistry.VERSION_KEY);
        if (version != 0 && version != RoomRegistry.DATA_VERSION) {
            throw new IllegalStateException("a Workbay block entity is version " + version
                + ", this build reads version " + RoomRegistry.DATA_VERSION);
        }
        workbayId = tag.contains(ID_KEY)
            ? UUIDUtil.CODEC.parse(NbtOps.INSTANCE, tag.get(ID_KEY)).result().orElse(null)
            : null;

        buses.clear();
        Tag stored = tag.get(BUSES_KEY);
        if (stored != null) {
            buses.addAll(BusConfig.CODEC.listOf().parse(NbtOps.INSTANCE, stored)
                .result().orElse(List.of()));
        }
        energy.receiveEnergy(tag.getInt(ENERGY_KEY), false);
    }

    // ------------------------------------------------------- item round trip

    /**
     * What the dropped item carries. Read by the block's loot table through
     * {@code copy_components}, so breaking a Workbay and placing it again lands on the same bays.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        record().ifPresent(r -> components.set(WBDataComponents.BINDING.get(),
            WorkbayBinding.of(r, occupiedBays(), buses.size())));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        WorkbayBinding binding = input.get(WBDataComponents.BINDING.get());
        if (binding != null) {
            workbayId = binding.id();
        }
    }

    /**
     * Without this the id is in {@code block_entity_data} as well as in the component, and a
     * creative ctrl-pick would clone a bound Workbay. SPEC.md §14: pick-block must never hand out a
     * second block pointing at somebody's factory.
     */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(ID_KEY);
    }
}
