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

    private int nextBayColumn = 0;

    /**
     * v1 never allocates a room region, but the counter is persisted from the first save so that v2
     * starts from a number no v1 world has already used. SPEC.md §16 forbids simplifying this away.
     */
    private int nextRoomRegion = 0;

    public static final SavedData.Factory<RoomRegistry> FACTORY =
        new SavedData.Factory<>(RoomRegistry::new, RoomRegistry::load, null);

    public static RoomRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
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

    public List<WorkbayRecord> ownedBy(UUID owner) {
        return byId.values().stream().filter(r -> r.owner().equals(owner)).toList();
    }

    public int size() {
        return byId.size();
    }

    public int nextRoomRegion() {
        return nextRoomRegion;
    }

    // ---------------------------------------------------------------- writing

    /** Mints a Workbay: a fresh id, a code nobody else has, and a bay column nobody else uses. */
    public WorkbayRecord create(UUID owner, String ownerName, RandomSource random) {
        UUID id = UUID.randomUUID();
        ChunkPos column = allocateBayColumn();
        WorkbayRecord record = new WorkbayRecord(id, mintCode(random), owner, ownerName, false,
            column, WorkbayRecord.Upgrades.NONE, Optional.empty(), List.of(), List.of());
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
