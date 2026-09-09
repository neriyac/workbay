package com.neryos.workbay.world;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every Workbay in the world, and where its bays live. SPEC.md §14.
 *
 * <p>Kept on the <b>overworld</b>, the only dimension never fully unloaded, so it is readable
 * whatever else is loaded. The third argument to {@link SavedData.Factory} is null on purpose:
 * NeoForge has no mod DataFixers, so {@link #DATA_VERSION} and the branch in {@link #load} are the
 * entire migration mechanism and there is no rescue if a schema change is got wrong.
 */
public class RoomRegistry extends SavedData {
    private static final Logger LOG = LogUtils.getLogger();

    /** First key of every structure this mod persists. Branch on it in load(), no exceptions. */
    public static final int DATA_VERSION = 1;
    public static final String VERSION_KEY = "DataVersion";

    private static final String FILE = "workbay_registry";

    /**
     * Compact Machines' alphabet with the ambiguous letters removed — no C, E, I, O or U, so no
     * code can be misread off a screenshot or misheard down a voice call. Recovering a lost
     * Workbay by typing its code to an operator is the point of having one.
     */
    private static final String ALPHABET = "0123456789ABDFGHJKLMNPQRSTVWXYZ";
    private static final int CODE_LENGTH = 12;

    /**
     * Chunks between bay columns. SPEC.md §8 allocates them adjacently, but a NeoForge forced-chunk
     * ticket covers radius 2, so an anchored Workbay would hold twenty-four of its neighbours'
     * columns loaded as well (OPEN_ISSUES #17). The Backshop is empty and infinite; spacing costs
     * nothing and makes an Anchor pay for exactly one column.
     */
    private static final int COLUMN_SPACING = 8;

    private final Map<UUID, WorkbayRecord> byId = new HashMap<>();
    private final Map<String, UUID> byCode = new HashMap<>();

    /**
     * Every room in the world, by its own UUID. Kept here and not on the Workbay record because a
     * room outlives the block that made it: SPEC.md §14 orphans a room and never deletes it, and
     * {@code /workbay recover} has to find one with nothing standing in the world.
     */
    private final Map<UUID, RoomRecord> rooms = new HashMap<>();

    /**
     * Which tick each network's buses were last run on. <b>Not saved</b>: it is a claim on the
     * current tick and nothing more.
     *
     * <p>Every Workbay on a record ticks <em>every</em> bus on it, so two blocks standing on one
     * network moved every link twice — a link the panel printed as one
     * item every twenty ticks delivered two a second, measured in a live world (OPEN_ISSUES #54,
     * and #40 is the fault). The rate and the wheel were both right; the link was simply stepped by
     * two runners. One entry per network, replaced every tick.
     */
    private final Map<UUID, Long> busTurn = new HashMap<>();

    /** What each network's links last reported, shared by every Workbay on it (#39). */
    private final Map<UUID, Map<UUID, com.neryos.workbay.bus.BusRunner.BusStatus>>
        busStatuses = new HashMap<>();

    private int nextBayColumn = 0;

    /**
     * Monotonic and never reused, so no two rooms can ever be handed the same 512-block region —
     * including after one is orphaned, which is why it is a counter and not a free list.
     */
    private int nextRoomRegion = 0;

    public static final SavedData.Factory<RoomRegistry> FACTORY =
        new SavedData.Factory<>(RoomRegistry::new, RoomRegistry::load, null);

    public static RoomRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    /**
     * True for exactly one Workbay per network per tick — whichever asks first.
     *
     * <p>An election rather than a fixed block, because a fixed one stops the whole network the
     * moment its chunk unloads. Block entities tick in a stable order, so the same Workbay wins
     * every tick while it is loaded and another takes over on its own when it is not.
     */
    /**
     * One link-status map per network, shared by every Workbay standing on it.
     *
     * <p>Buses are run by one elected Workbay per tick, so the blocks that lose the turn used to
     * hold no statuses at all and read <em>Idle</em> for ever — a second front door that always
     * said nothing was wrong. §7's question is asked of the network, so the answer lives with the
     * network. Transient: it is derived from a tick and rebuilt on the next one. OPEN_ISSUES #39.
     */
    public java.util.Map<UUID, com.neryos.workbay.bus.BusRunner.BusStatus> busStatuses(
        UUID network) {
        return busStatuses.computeIfAbsent(network, key -> new java.util.HashMap<>());
    }

    public boolean takeBusTurn(UUID network, long gameTime) {
        Long ranOn = busTurn.put(network, gameTime);
        return ranOn == null || ranOn != gameTime;
    }

    // ---------------------------------------------------------------- reading

    public Optional<WorkbayRecord> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Lookup is forgiving about dashes and case, because a player types this one by hand. */
    public Optional<WorkbayRecord> byCode(String code) {
        UUID id = byCode.get(normalise(code));
        return id == null ? Optional.empty() : byId(id);
    }

    /**
     * Which Workbay owns a bay column. The Backshop side of the mod only ever knows a position, so
     * this is how a Port or a bay maps back to the Workbay it belongs to.
     */
    public Optional<WorkbayRecord> byBayColumn(ChunkPos column) {
        long key = column.toLong();
        return byId.values().stream().filter(r -> r.bayColumn().toLong() == key).findFirst();
    }

    public List<WorkbayRecord> ownedBy(UUID owner) {
        return byId.values().stream().filter(r -> r.owner().equals(owner)).toList();
    }

    /** Every Workbay this server knows about, in no particular order. */
    public java.util.Collection<WorkbayRecord> all() {
        return java.util.List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public int nextRoomRegion() {
        return nextRoomRegion;
    }

    public Optional<RoomRecord> room(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    /** The rooms of one Workbay, in the order its record lists them. Skips any that went missing. */
    public List<RoomRecord> roomsOf(WorkbayRecord record) {
        return record.rooms().stream().map(rooms::get).filter(java.util.Objects::nonNull).toList();
    }

    /** Which room a Backshop position falls inside, or empty. How a shell block finds its room. */
    public Optional<RoomRecord> roomAt(net.minecraft.core.BlockPos pos) {
        return rooms.values().stream()
            .filter(r -> r.built() && RoomGeometry.inside(pos, r.region(), r.builtTier()))
            .findFirst();
    }

    /**
     * Whose room this is: the owner of the network whose record lists it.
     *
     * <p>Derived rather than stored on the room. A room is created by
     * {@link RoomVisit} out of a Workbay's own record and can only ever be reached through one, so
     * a second copy of the owner here could only ever be a copy that had gone wrong — and the one
     * case where it would differ is the one this must get right: an <b>orphaned</b> room, listed by
     * nobody, which correctly has no owner and therefore nobody who may stand in it.
     *
     * <p>Iterates {@code byId} directly and not {@link #all()}, which copies: this is asked once
     * per tick for every player in the Backshop.
     */
    public Optional<UUID> ownerOf(RoomRecord room) {
        for (WorkbayRecord record : byId.values()) {
            if (record.rooms().contains(room.id())) {
                return Optional.of(record.owner());
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- writing

    /** Mints a Workbay: a fresh id, a code nobody else has, and a bay column nobody else uses. */
    public WorkbayRecord create(UUID owner, String ownerName, RandomSource random) {
        UUID id = UUID.randomUUID();
        ChunkPos column = allocateBayColumn();
        WorkbayRecord record = new WorkbayRecord(id, mintCode(random), owner, ownerName, false,
            column, WorkbayRecord.Upgrades.NONE, Optional.empty(), List.of(), List.of(), List.of(),
            0);
        byId.put(id, record);
        byCode.put(normalise(record.code()), id);
        setDirty();
        return record;
    }

    /** Replaces a record in place. Every mutation goes through here so setDirty is never missed. */
    public void put(WorkbayRecord record) {
        byId.put(record.id(), record);
        byCode.put(normalise(record.code()), record.id());
        setDirty();
    }

    /**
     * Mints a room and hands it a region nobody else has. The shell is <b>not</b> built here:
     * SPEC.md §8 spends that on first entry, so a Room Frame installed and never used costs
     * nothing but a counter.
     */
    public RoomRecord createRoom() {
        RoomRecord room = RoomRecord.fresh(UUID.randomUUID(), nextRoomRegion++);
        rooms.put(room.id(), room);
        setDirty();
        return room;
    }

    /** Replaces a room in place. Every room mutation goes through here so setDirty is never missed. */
    public void putRoom(RoomRecord room) {
        rooms.put(room.id(), room);
        setDirty();
    }

    private ChunkPos allocateBayColumn() {
        int index = nextBayColumn++;
        setDirty();
        // Positive quadrant, well away from the room regions at far negative x (SPEC.md §8).
        return new ChunkPos((index & 0xFFF) * COLUMN_SPACING, (index >> 12) * COLUMN_SPACING);
    }

    private String mintCode(RandomSource random) {
        for (int attempt = 0; attempt < 64; attempt++) {
            StringBuilder raw = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                raw.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
            if (!byCode.containsKey(normalise(code))) {
                return code;
            }
        }
        // 31^12 codes; sixty-four collisions in a row is not luck, it is a broken RandomSource.
        throw new IllegalStateException("could not mint a unique Workbay code in 64 attempts");
    }

    public static String normalise(String code) {
        return code.replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putInt("NextBayColumn", nextBayColumn);
        tag.putInt("NextRoomRegion", nextRoomRegion);
        tag.put("Rooms", RoomRecord.CODEC.listOf()
            .encodeStart(NbtOps.INSTANCE, List.copyOf(rooms.values()))
            .getOrThrow(e -> new IllegalStateException("could not write the room registry: " + e)));
        tag.put("Workbays", WorkbayRecord.CODEC.listOf()
            .encodeStart(NbtOps.INSTANCE, List.copyOf(byId.values()))
            .getOrThrow(e -> new IllegalStateException("could not write the Workbay registry: " + e)));
        return tag;
    }

    public static RoomRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomRegistry registry = new RoomRegistry();
        int version = tag.getInt(VERSION_KEY);
        // There is no DataFixer to fall back on, so an unknown version has to be loud and has to
        // refuse rather than silently drop somebody's factory.
        if (version != DATA_VERSION) {
            throw new IllegalStateException("workbay_registry is version " + version + ", this build "
                + "reads version " + DATA_VERSION + ". Refusing to load rather than lose data.");
        }
        registry.nextBayColumn = tag.getInt("NextBayColumn");
        registry.nextRoomRegion = tag.getInt("NextRoomRegion");

        Tag roomList = tag.get("Rooms");
        if (roomList != null) {
            RoomRecord.CODEC.listOf()
                .parse(NbtOps.INSTANCE, roomList)
                .resultOrPartial(e -> LOG.error("dropped a malformed room record: {}", e))
                .orElse(List.of())
                .forEach(room -> registry.rooms.put(room.id(), room));
        }

        Tag list = tag.get("Workbays");
        if (list != null) {
            List<WorkbayRecord> records = WorkbayRecord.CODEC.listOf()
                .parse(NbtOps.INSTANCE, list)
                .resultOrPartial(e -> LOG.error("dropped a malformed Workbay record: {}", e))
                .orElse(List.of());
            for (WorkbayRecord record : records) {
                registry.byId.put(record.id(), record);
                registry.byCode.put(normalise(record.code()), record.id());
            }
        }
        return registry;
    }

    /** Exposed for the gametests: a round trip without touching the level's data storage. */
    public static RoomRegistry roundTrip(RoomRegistry source, HolderLookup.Provider registries) {
        return load(source.save(new CompoundTag(), registries), registries);
    }
}
