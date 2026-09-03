package com.neryos.workbay.content.workbay;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import com.neryos.workbay.world.WorkbayTickets;
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
    private static final String ENERGY_KEY = "Energy";

    @Nullable
    private UUID workbayId;

    private final BusRunner runner = new BusRunner(() -> !isRemoved());

    /** Whether this Workbay currently holds SPEC.md §12's mirroring ticket on its bay column. */
    private boolean mirroring;

    /**
     * How long the Assay has been working on the Levy it is currently making. Only counts while
     * there is a full batch to convert, so the 200 ticks are 200 ticks of work rather than a delay
     * that starts before the goods arrive.
     */
    private int assayTicks;

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

    /**
     * Links used to live only in this block entity's own NBT, which is not one of the components a
     * broken Workbay's loot table copies onto the dropped item — so breaking and re-placing one,
     * even onto the very same {@link WorkbayBinding}, silently lost every link. They live on the
     * {@link WorkbayRecord} now, the same as bays, upgrades and the lock, so they survive exactly as
     * well as everything else does.
     */
    public List<BusConfig> buses() {
        return record().map(WorkbayRecord::buses).orElse(List.of());
    }

    /**
     * Adds a new link, or replaces an existing one <em>in place</em>. The row list has no sort
     * comparator by default and relies on this list's own order for that — so a naive
     * remove-then-append here silently sent a link to the bottom of the screen every time its
     * enabled flag, name, filter or anything else about it changed. Found in play: toggling a
     * link's checkbox visibly jumped it to the end of the list.
     */
    public void addBus(BusConfig bus) {
        editBuses(record -> {
            List<BusConfig> updated = new ArrayList<>(record.buses());
            int existing = -1;
            for (int i = 0; i < updated.size(); i++) {
                if (updated.get(i).id().equals(bus.id())) {
                    existing = i;
                    break;
                }
            }
            if (existing >= 0) {
                updated.set(existing, bus);
                // The runner caches a resolved capability per link, and the position and face it
                // was resolved against are baked into that cache. Any edit can move either one --
                // retargeting an internal link at a different bay, pinning a target face -- so the
                // cache is dropped on every replacement rather than only on removal. Re-resolving
                // is a capability lookup on the next tick; getting it wrong is a link that quietly
                // keeps talking to the block it used to point at.
                runner.forget(bus.id());
            } else {
                updated.add(bus);
            }
            return updated;
        });
    }

    public void removeBus(UUID busId) {
        editBuses(record -> {
            List<BusConfig> updated = new ArrayList<>(record.buses());
            if (!updated.removeIf(bus -> bus.id().equals(busId))) {
                return null;
            }
            runner.forget(busId);
            return updated;
        });
    }

    public Optional<BusConfig> bus(UUID busId) {
        return buses().stream().filter(bus -> bus.id().equals(busId)).findFirst();
    }

    /** Every link anchored by the Connector at one position. Base is one; Multichannel allows three. */
    public List<BusConfig> linksAt(GlobalPos connector) {
        return buses().stream().filter(bus -> bus.connector().equals(connector)).toList();
    }

    /** Breaking a Connector takes its links with it. SPEC.md §0: the block <em>is</em> the link. */
    public void removeLinksAt(GlobalPos connector) {
        editBuses(record -> {
            List<BusConfig> going = record.buses().stream()
                .filter(bus -> bus.connector().equals(connector)).toList();
            if (going.isEmpty()) {
                return null;
            }
            going.forEach(bus -> runner.forget(bus.id()));
            List<BusConfig> updated = new ArrayList<>(record.buses());
            updated.removeAll(going);
            return updated;
        });
    }

    /**
     * Every mutation to the link list goes through here: read the current record, compute the new
     * list, write it back. {@code edit} returns {@code null} for "nothing changed" so a no-op edit
     * (removing a link that is already gone) does not touch the registry or fire {@code setChanged}.
     */
    private void editBuses(java.util.function.Function<WorkbayRecord, List<BusConfig>> edit) {
        if (!(level instanceof ServerLevel server) || workbayId == null) {
            return;
        }
        RoomRegistry registry = RoomRegistry.get(server.getServer());
        WorkbayRecord record = registry.byId(workbayId).orElse(null);
        if (record == null) {
            return;
        }
        List<BusConfig> updated = edit.apply(record);
        if (updated == null) {
            return;
        }
        registry.put(record.withBuses(updated));
        setChanged();
    }

    /** A bay's face config changed, so its cached machine-end handler has to be re-resolved. */
    public void forgetBay(int bay) {
        runner.forgetBay(bay);
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
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // Before the links, and whether or not there are any: a hosted machine has to tick even
        // with nothing pointed at it.
        workbay.mirror(server);
        // Every tick, not only on a wheel step: a rising edge between two steps still has to be
        // seen, or a fast clock on PULSE would be silently ignored.
        workbay.runner.power(server.hasNeighborSignal(pos));
        workbay.record().ifPresent(record -> {
            if (record.buses().isEmpty()) {
                return;
            }
            workbay.runner.tick(server, record, record.buses(),
                Math.floorMod(pos.hashCode(), BusRunner.WHEEL));
        });

        workbay.settleAssay(server);

        // A Connector broken while this Workbay was unloaded could not tell it, so the runner spots
        // the gap instead and the link is swept here, outside the iteration that found it.
        var orphaned = workbay.runner.orphaned();
        if (!orphaned.isEmpty()) {
            orphaned.forEach(workbay::removeBus);
        }
    }

    /**
     * Banks what the skim took and turns full batches into Levy. SPEC.md §3.
     *
     * <p>Here rather than in the runner because this is a write to the record, and the runner is
     * mid-iteration over a list that record owns. One write per tick, and only when something
     * actually changed — a Workbay with the dial at zero never touches the registry.
     */
    private void settleAssay(ServerLevel server) {
        int skimmed = runner.takeSkim();
        WorkbayRecord record = record().orElse(null);
        if (record == null) {
            return;
        }
        WorkbayRecord.Assay assay = record.assay();
        int held = assay.skimmed() + skimmed;
        int levy = assay.levy();
        if (held >= com.neryos.workbay.content.assay.AssayBlock.ITEMS_PER_LEVY
            && com.neryos.workbay.content.assay.AssayBlock.rackedIn(record)
            && ++assayTicks >= com.neryos.workbay.content.assay.AssayBlock.CONVERT_TICKS) {
            assayTicks = 0;
            held -= com.neryos.workbay.content.assay.AssayBlock.ITEMS_PER_LEVY;
            levy++;
        }
        if (held == assay.skimmed() && levy == assay.levy()) {
            return;
        }
        RoomRegistry.get(server.getServer())
            .put(record.withAssay(assay.withSkimmed(held).withLevy(levy)));
        setChanged();
    }

    /**
     * SPEC.md §12's mirroring: <b>the bay column is loaded exactly while the Workbay's own chunk
     * is.</b> Costs nothing a player is not already paying for, and it is what makes a hosted
     * machine reachable at all — without it the Backshop chunk is never loaded, every link reports
     * that the machine is unreachable, and the mod does not work outside a test that force-loads
     * the chunk for itself.
     *
     * <p>On the tick rather than in {@code onLoad}: forcing sync-loads a chunk in another
     * dimension, which is not something to do from inside a chunk load.
     */
    private void mirror(ServerLevel server) {
        if (mirroring) {
            return;
        }
        ServerLevel backshop = server.getServer().getLevel(WorkbayDimensions.BACKSHOP);
        WorkbayRecord record = record().orElse(null);
        if (backshop == null || record == null) {
            return;
        }
        WorkbayTickets.force(backshop, record.id(), record.bayColumn());
        mirroring = true;
    }

    /**
     * {@code setRemoved} is called both when the block is broken and when its chunk unloads, which
     * is exactly the pair of events mirroring is defined by. Keeping the column loaded past either
     * is the Anchor's job, and the Anchor is not built.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mirroring && level instanceof ServerLevel server) {
            mirroring = false;
            ServerLevel backshop = server.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            record().ifPresent(record -> {
                if (backshop != null) {
                    WorkbayTickets.release(backshop, record.id(), record.bayColumn());
                }
            });
        }
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
            WorkbayBinding.of(r, occupiedBays(), r.buses().size())));
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
