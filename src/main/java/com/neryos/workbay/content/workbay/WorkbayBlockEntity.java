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
    private static final String PIPS_KEY = "Pips";

    /**
     * One pip per link, in the LINKS list's own order, as far as the front of the block has room
     * for. SPEC.md §7 gives the frame the summary — "is anything wrong?" — and these give the
     * detail beside it: which of the first eight links is the one to look at.
     *
     * <p>Deliberately coarser than {@link BusRunner.BusStatus}. RUNNING and IDLE collapse into one
     * colour because a link on the wheel alternates between them every few ticks, and a pip that
     * changes colour on the wheel would be a packet per tick and a blinking block. What is left
     * changes only when the player's world does.
     */
    public enum Pip {
        /** No link in this slot, or one the player has switched off. Says nothing on purpose. */
        NONE,
        /** The link is fine: moving, or with nothing to move. */
        OK,
        /** It cannot reach, and the answer is a setting: no port, no face, chunk not loaded. */
        ATTENTION,
        /** Something the player built is gone: the Connector, or the target block. */
        BROKEN;

        /** Cached, and public so a test can name what a byte on the wire means. */
        public static final Pip[] VALUES = values();
    }

    /** How many links the front of the block can show. tools/make-art.py draws the sockets. */
    public static final int PIPS = 8;

    @Nullable
    private UUID workbayId;

    private final BusRunner runner = new BusRunner(() -> !isRemoved());

    /**
     * The Backshop chunks this Workbay currently holds a mirroring ticket on: its bay column,
     * plus every room of its network that has a Connector standing in it. SPEC.md §12.
     *
     * <p>A set rather than a flag because the second half changes while the block lives — placing
     * a Connector in a room is what puts that room on the list and breaking it is what takes it
     * off, and neither is a moment this block hears about.
     */
    private final java.util.Set<net.minecraft.world.level.ChunkPos> mirrored =
        new java.util.HashSet<>();

    /**
     * How long the Assay has been working on the Levy it is currently making. Only counts while
     * there is a full batch to convert, so the 200 ticks are 200 ticks of work rather than a delay
     * that starts before the goods arrive.
     */
    private int assayTicks;

    /** Ticks of {@link WorkbayState#RUNNING} left to show since the last move. Not saved: a Workbay
     * that just loaded has moved nothing yet, and one tick of {@code idle} is the truth. */
    private int runningHold;

    /** {@link Pip} ordinals, one per link. Derived on the server, sent to the client, never saved
     * to disk — a Workbay that just loaded recomputes it on its first tick. */
    private byte[] pips = new byte[0];

    /** Four seconds. Long enough to bridge a link on the slowest wheel step, short enough that a
     * base that has actually stopped says so before the player has walked the length of it. */
    private static final int RUNNING_HOLD = 80;

    /**
     * SPEC.md §9's buffer, accepted on any face and never handing energy back out of the block.
     * The three-layer spend model and round-robin sharing to bays are not built yet, so this is a
     * real buffer the screen reads rather than a number invented for a progress bar.
     */
    private final EnergyStorage energy = new EnergyStorage(BUFFER_FE, MAX_FE_PER_TICK, 0) {
        private long tick = Long.MIN_VALUE;
        private int taken;

        /**
         * The screen prints <b>Up to 10,000 FE/t</b> and {@link EnergyStorage}'s own ceiling is per
         * <em>call</em>: three pushes in one tick put in thirty thousand, measured. Six cables on
         * six faces are six pushes, so the printed rate was out by however many things happened to
         * be pushing.
         *
         * <p>Keyed on the level's game time rather than reset from {@link #serverTick}, so a
         * Workbay in a chunk that is loaded but not ticking cannot be left holding a spent budget
         * forever. {@code deserializeNBT} writes the field directly and so is not throttled, which
         * is what lets a full buffer come back off disk in one go.
         */
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            long now = level == null ? 0 : level.getGameTime();
            if (now != tick) {
                tick = now;
                taken = 0;
            }
            int got = super.receiveEnergy(Math.min(toReceive, MAX_FE_PER_TICK - taken), simulate);
            if (!simulate) {
                taken += got;
            }
            return got;
        }
    };

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
        // The game's own profiler, so `/perf start` breaks this tick down by name instead of
        // reporting one lump called workbay:workbay. Free when nothing is recording -- the
        // inactive filler's push/pop are empty methods, the same bet vanilla makes everywhere.
        net.minecraft.util.profiling.ProfilerFiller profiler = server.getProfiler();
        // Before the links, and whether or not there are any: a hosted machine has to tick even
        // with nothing pointed at it.
        profiler.push("mirror");
        workbay.mirror(server);
        // Every tick, not only on a wheel step: a rising edge between two steps still has to be
        // seen, or a fast clock on PULSE would be silently ignored.
        profiler.popPush("buses");
        workbay.runner.power(server.hasNeighborSignal(pos));
        workbay.record().ifPresent(record -> {
            if (record.buses().isEmpty()) {
                return;
            }
            // One runner per network per tick. Links live on the record, not on the block, so every
            // Workbay standing on a record used to run every link on it -- two blocks moved a
            // rate-1 link twice a second and the Assay skimmed twice, which is a number the panel
            // prints being wrong by however many Workbays happen to be loaded. OPEN_ISSUES #40 and
            // #54, and the reason a printed rate can be a promise at all.
            if (!com.neryos.workbay.world.RoomRegistry.get(server.getServer())
                .takeBusTurn(record.id(), server.getGameTime())) {
                return;
            }
            workbay.runner.tick(server, record, record.buses(),
                Math.floorMod(pos.hashCode(), BusRunner.WHEEL));
        });

        profiler.popPush("assay");
        workbay.settleAssay(server);
        profiler.popPush("state");
        workbay.refreshLitState(server, pos, state);
        // After the lit state, not before: refreshLitState may have replaced the block, and a
        // sendBlockUpdated with the stale state would tell the client to draw the old variant.
        profiler.popPush("pips");
        workbay.refreshPips(server, pos, server.getBlockState(pos));

        // A Connector broken while this Workbay was unloaded could not tell it, so the runner spots
        // the gap instead and the link is swept here, outside the iteration that found it.
        profiler.popPush("sweep");
        var orphaned = workbay.runner.orphaned();
        if (!orphaned.isEmpty()) {
            orphaned.forEach(workbay::removeBus);
        }
        profiler.pop();
    }

    /**
     * SPEC.md §7: the block answers "is something wrong?" from across the room. The property has
     * existed since the block was written and <b>nothing ever wrote it</b> — every Workbay in every
     * world has been {@code state=idle} since the mod started, which is why three models pointing
     * at one picture never looked like a fault.
     *
     * <p>{@code RUNNING} is held for {@link #RUNNING_HOLD} ticks after the last move rather than
     * read live. A link on the wheel moves something every few seconds and reports IDLE in between,
     * so the live reading would flicker the blockstate — and a blockstate write is a lighting
     * recalculation and a packet to every player tracking the chunk. §7 says "moved something
     * recently", and this is what recently means.
     */
    private void refreshLitState(ServerLevel server, BlockPos pos, BlockState state) {
        if (!state.hasProperty(WorkbayBlock.STATE)) {
            return;
        }
        if (runner.anyRunning()) {
            runningHold = RUNNING_HOLD;
        } else if (runningHold > 0) {
            runningHold--;
        }
        WorkbayState want = runner.anyProblem() ? WorkbayState.STUCK
            : runningHold > 0 ? WorkbayState.RUNNING
            : WorkbayState.IDLE;
        boolean powered = server.hasNeighborSignal(pos);
        if (state.getValue(WorkbayBlock.STATE) == want
            && state.getValue(WorkbayBlock.POWERED) == powered) {
            return;
        }
        server.setBlock(pos, state.setValue(WorkbayBlock.STATE, want)
            .setValue(WorkbayBlock.POWERED, powered), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    /** What the front of the block is showing. Client-side after the update tag lands. */
    public byte[] pips() {
        return pips;
    }

    /**
     * The per-link half of SPEC.md §7's "is something wrong?", and the reason the block entity
     * syncs anything at all. Recomputed every tick and sent only on a difference: the four
     * readings are all stable, so a base at rest sends nothing.
     */
    private void refreshPips(ServerLevel server, BlockPos pos, BlockState state) {
        var buses = record().map(WorkbayRecord::buses).orElse(java.util.List.of());
        byte[] want = new byte[Math.min(buses.size(), PIPS)];
        for (int i = 0; i < want.length; i++) {
            want[i] = (byte) pipFor(buses.get(i)).ordinal();
        }
        if (java.util.Arrays.equals(pips, want)) {
            return;
        }
        pips = want;
        setChanged();
        server.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    private Pip pipFor(com.neryos.workbay.bus.BusConfig bus) {
        if (!bus.enabled()) {
            return Pip.NONE;
        }
        return switch (runner.status(bus.id())) {
            case RUNNING, IDLE, DISABLED, HELD_BY_REDSTONE -> Pip.OK;
            case CONNECTOR_GONE, TARGET_MISSING -> Pip.BROKEN;
            // Listed rather than defaulted, so a new BusStatus is a compile error here as well as
            // in the two screens. A default would have quietly called it ATTENTION.
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE,
                NEEDS_RESONATOR -> Pip.ATTENTION;
        };
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
        int batch = com.neryos.workbay.content.assay.AssayBlock.itemsPerLevy();
        if (held >= batch
            && com.neryos.workbay.content.assay.AssayBlock.rackedIn(record)
            && ++assayTicks >= com.neryos.workbay.content.assay.AssayBlock.convertTicks()) {
            assayTicks = 0;
            held -= batch;
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
     * <p><b>And a room with a Connector in it, on the same terms.</b> A room is a stage in a chain,
     * not only a place to stand: a barrel in one feeds the next bay along, and a stage that stops
     * when nobody is looking at it is not a stage. The same decision applied twice costs a server
     * nothing it was not already paying — a hosted machine costs what it would cost on the floor,
     * and so does a barrel in a room. Compact Machines does not do this, which is why their rooms
     * need a chunkloader and this mod's do not; the Anchor is left with one clear job, which is
     * running the room while <em>nobody is online at all</em>.
     *
     * <p>A room with no Connector in it is not mirrored. Nothing in it can do anything, so loading
     * it would be a ticket spent on scenery.
     *
     * <p>On the tick rather than in {@code onLoad}: forcing sync-loads a chunk in another
     * dimension, which is not something to do from inside a chunk load.
     */
    private void mirror(ServerLevel server) {
        ServerLevel backshop = server.getServer().getLevel(WorkbayDimensions.BACKSHOP);
        WorkbayRecord record = record().orElse(null);
        if (backshop == null || record == null) {
            return;
        }
        // The steady state of every Workbay that owns no room: one ticket, already held, nothing
        // to allocate. Without it this method builds a set per tick to answer a question that
        // cannot have changed.
        if (record.rooms().isEmpty() && mirrored.size() == 1
            && mirrored.contains(record.bayColumn())) {
            return;
        }
        java.util.Set<net.minecraft.world.level.ChunkPos> wanted = new java.util.HashSet<>();
        wanted.add(record.bayColumn());
        if (!record.rooms().isEmpty()) {
            java.util.List<com.neryos.workbay.world.RoomRecord> rooms =
                RoomRegistry.get(server.getServer()).roomsOf(record);
            for (BusConfig bus : record.buses()) {
                if (!bus.connector().dimension().equals(WorkbayDimensions.BACKSHOP)) {
                    continue;
                }
                for (com.neryos.workbay.world.RoomRecord room : rooms) {
                    if (room.built() && com.neryos.workbay.world.RoomGeometry.inside(
                        bus.connector().pos(), room.region(), room.builtTier())) {
                        wanted.addAll(com.neryos.workbay.world.RoomGeometry.chunks(
                            room.region(), room.builtTier()));
                        break;
                    }
                }
            }
        }
        if (wanted.equals(mirrored)) {
            return;
        }
        for (net.minecraft.world.level.ChunkPos chunk : wanted) {
            if (mirrored.add(chunk)) {
                WorkbayTickets.force(backshop, record.id(), chunk);
            }
        }
        mirrored.removeIf(chunk -> {
            if (wanted.contains(chunk)) {
                return false;
            }
            WorkbayTickets.release(backshop, record.id(), chunk);
            return true;
        });
    }

    /**
     * {@code setRemoved} is called both when the block is broken and when its chunk unloads, which
     * is exactly the pair of events mirroring is defined by. Keeping the column loaded past either
     * is the Anchor's job, and the Anchor is not built.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!mirrored.isEmpty() && level instanceof ServerLevel server) {
            ServerLevel backshop = server.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            record().ifPresent(record -> {
                if (backshop != null) {
                    mirrored.forEach(chunk ->
                        WorkbayTickets.release(backshop, record.id(), chunk));
                }
            });
            mirrored.clear();
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

    /**
     * The whole saved tag plus the pips. The pips are the only thing the client needs and the only
     * thing not on disk; everything else rides along because it is already written and leaving it
     * out would make the client's copy differ from the server's for no gain.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.putByteArray(PIPS_KEY, pips);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
        getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
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
        // Not receiveEnergy: that is clamped to MAX_FE_PER_TICK, so a Workbay holding a full
        // hundred thousand came back off disk with ten and the screen printed the loss as the
        // truth. Measured: stored 100000, saved 100000, reloaded 10000. Same IntTag on disk either
        // way, so nothing already saved has to be migrated.
        if (tag.contains(ENERGY_KEY)) {
            energy.deserializeNBT(registries, tag.get(ENERGY_KEY));
        }
        // Absent on disk, present on the update tag. getByteArray answers with an empty array for
        // a key that is not there, which is exactly "no links to show".
        pips = tag.getByteArray(PIPS_KEY);
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
