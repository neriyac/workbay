package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One Workbay, as the registry knows it. SPEC.md §14.
 *
 * <p>The Backshop's actual contents are the source of truth and this is an index: {@code SavedData}
 * writes at level-save time while a Workbay's chunk saves on its own schedule, so a hard crash can
 * leave the two disagreeing. Nothing here may be the only copy of anything, with one exception —
 * {@link Bay#hosted()} is deliberately kept after a mod is uninstalled, because vanilla drops the
 * unknown block and the Backshop is then simply empty. That record is the only thing left that can
 * say what used to be in the bay.
 *
 * <p>{@link #rooms()} is empty in v1 and must stay present: SPEC.md §16 reserves it so rooms are
 * additive in v2 rather than a persistence rewrite.
 */
public record WorkbayRecord(
    UUID id,
    String code,
    UUID owner,
    String ownerName,
    boolean locked,
    ChunkPos bayColumn,
    Upgrades upgrades,
    Optional<GlobalPos> lastKnownPos,
    List<Bay> bays,
    List<UUID> rooms,
    List<com.neryos.workbay.bus.BusConfig> buses,
    int deployedCount) {

    /**
     * <b>One Connector, one link.</b> OPEN_ISSUES #77's model, enforced where every path that
     * builds a record has to go through it rather than in the one place that happened to mint
     * links -- the fan-out that made four rows out of one Connector was written in a block's
     * {@code useWithoutItem}, and a rule that lives beside the mistake is a rule the next mistake
     * will not obey.
     *
     * <p>It is an <em>invariant on the list</em> and not a change of shape: {@link
     * com.neryos.workbay.bus.BusConfig} already carries the Connector's own {@code GlobalPos},
     * so "a Connector owns its placement" is what was already stored.
     *
     * <p><b>Internal links are exempt, and that is not a detail.</b> A bay-to-bay link anchors on
     * the Workbay's own position ({@code BusConfig#createInternal}) because there is no Connector
     * to anchor it to -- so folding by position without this exemption collapses every internal
     * link in a network into one.
     *
     * <p>An attached link beats a detached one, then list order wins. A world saved before this
     * loses the extras, with their filter, rate, speed and name; that is stated in #77 rather
     * than migrated, because the extras are rows the model says should never have existed.
     */
    public WorkbayRecord {
        buses = foldByConnector(buses);
    }

    private static List<com.neryos.workbay.bus.BusConfig> foldByConnector(
        List<com.neryos.workbay.bus.BusConfig> all) {
        java.util.Map<GlobalPos, Integer> seen = new java.util.HashMap<>();
        List<com.neryos.workbay.bus.BusConfig> kept = new java.util.ArrayList<>(all.size());
        boolean folded = false;
        for (com.neryos.workbay.bus.BusConfig bus : all) {
            if (bus.internal()) {
                kept.add(bus);
                continue;
            }
            Integer at = seen.get(bus.connector());
            if (at == null) {
                seen.put(bus.connector(), kept.size());
                kept.add(bus);
                continue;
            }
            folded = true;
            if (kept.get(at).detached() && !bus.detached()) {
                kept.set(at, bus);
            }
        }
        return folded ? List.copyOf(kept) : all;
    }

    public static final Codec<WorkbayRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("Id").forGetter(WorkbayRecord::id),
        Codec.STRING.fieldOf("Code").forGetter(WorkbayRecord::code),
        UUIDUtil.CODEC.fieldOf("Owner").forGetter(WorkbayRecord::owner),
        Codec.STRING.fieldOf("OwnerName").forGetter(WorkbayRecord::ownerName),
        Codec.BOOL.fieldOf("Locked").forGetter(WorkbayRecord::locked),
        Codec.LONG.xmap(ChunkPos::new, ChunkPos::toLong).fieldOf("BayColumn").forGetter(WorkbayRecord::bayColumn),
        Upgrades.CODEC.fieldOf("Upgrades").forGetter(WorkbayRecord::upgrades),
        GlobalPos.CODEC.optionalFieldOf("LastKnownPos").forGetter(WorkbayRecord::lastKnownPos),
        Bay.CODEC.listOf().fieldOf("Bays").forGetter(WorkbayRecord::bays),
        UUIDUtil.CODEC.listOf().fieldOf("Rooms").forGetter(WorkbayRecord::rooms),
        // Optional and empty by default: a record written before links moved into the registry
        // reads back with none, which is exactly what it had.
        com.neryos.workbay.bus.BusConfig.CODEC.listOf().optionalFieldOf("Buses", List.of())
            .forGetter(WorkbayRecord::buses),
        // How many live Workbay blocks are currently bound to this record. Read by placement to
        // decide whether an unbound item may reuse this network or must be refused (SPEC.md §14).
        Codec.INT.optionalFieldOf("DeployedCount", 0).forGetter(WorkbayRecord::deployedCount)
    ).apply(i, WorkbayRecord::new));

    public WorkbayRecord withUpgrades(Upgrades newUpgrades) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, newUpgrades,
            lastKnownPos, bays, rooms, buses, deployedCount);
    }

    public WorkbayRecord withLastKnownPos(GlobalPos pos) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            Optional.of(pos), bays, rooms, buses, deployedCount);
    }

    public WorkbayRecord withLocked(boolean nowLocked) {
        return new WorkbayRecord(id, code, owner, ownerName, nowLocked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, buses, deployedCount);
    }

    public WorkbayRecord withBays(List<Bay> newBays) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, List.copyOf(newBays), rooms, buses, deployedCount);
    }

    public WorkbayRecord withRooms(List<UUID> newRooms) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, bays, List.copyOf(newRooms), buses, deployedCount);
    }

    public WorkbayRecord withBuses(List<com.neryos.workbay.bus.BusConfig> newBuses) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, List.copyOf(newBuses), deployedCount);
    }

    /**
     * Incremented when a Workbay block genuinely binds to this record and decremented when that
     * block is genuinely broken (not merely unloaded — {@code WorkbayBlock#onRemove}, not
     * {@code BlockEntity#setRemoved}). What placement checks before letting an unbound item reuse
     * this network instead of refusing: {@code maxDeployedWorkbaysPerNetwork} bounds how many
     * physical front doors may stand open onto the same bays at once.
     */
    public WorkbayRecord withDeployedCount(int nowDeployedCount) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, buses, Math.max(0, nowDeployedCount));
    }



    /**
     * The base Workbay's bays, before any Expansion Plate. <b>Two, not one.</b>
     *
     * <p>It was two because the Assay ate one of them and a network with a single bay could never
     * earn the Levy to buy a second. The Assay is gone and the argument with it, and two is still
     * right for a plainer reason: one bay is a machine in a box, and the mod's whole claim is that
     * a rack replaces a floor. Two is the smallest number that is a rack.
     */
    public static final int BASE_BAYS = 2;

    /**
     * How many bays this Workbay may use: {@link #BASE_BAYS}, plus one per Expansion Plate, capped
     * by the server's {@code maxBaysPerWorkbay}. The cap is applied here rather than at install
     * time so lowering it never destroys an Expansion Plate somebody already spent.
     */
    /**
     * Rooms this network is entitled to: the Room Frame grants the first, each Annex Plate one
     * more. Zero without a Frame, so the ROOMS page is not there to be found before it means
     * anything. SPEC.md §1.
     */
    public int roomCapacity() {
        if (upgrades.roomTier() <= 0) {
            return 0;
        }
        // Capped here rather than at install time, the way bayCapacity is, so lowering
        // maxRoomsPerNetwork never destroys an Annex Plate somebody already spent. MAX_ROOMS is
        // still the hard ceiling: the region allocator reserves a footprint per slot.
        return Math.min(Math.min(1 + upgrades.annexPlates(),
            com.neryos.workbay.config.WorkbayConfig.SERVER.maxRoomsPerNetwork.get()),
            RoomGeometry.MAX_ROOMS);
    }

    public int bayCapacity() {
        return Math.min(BASE_BAYS + upgrades.expansionPlates(),
            com.neryos.workbay.config.WorkbayConfig.SERVER.maxBaysPerWorkbay.get());
    }

    /**
     * One bay. {@code hosted} is the block last known to be racked here, and it is deliberately
     * <em>not</em> cleared when that block's mod disappears — that is the {@code was:} record
     * SPEC.md §14 requires, and what {@code /workbay orphans} reports.
     */
    public record Bay(int index, Optional<ResourceLocation> hosted, FaceConfig faces,
        String name, RedstoneMode redstone) {

        // Both new fields are optional in the codec, so a Workbay written before they existed
        // reads back as an unnamed bay that always runs -- which is what it was.
        public static final Codec<Bay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Index").forGetter(Bay::index),
            ResourceLocation.CODEC.optionalFieldOf("Hosted").forGetter(Bay::hosted),
            FaceConfig.CODEC.optionalFieldOf("Faces", FaceConfig.NONE).forGetter(Bay::faces),
            Codec.STRING.optionalFieldOf("Name", "").forGetter(Bay::name),
            StringRepresentable.fromEnum(RedstoneMode::values)
                .optionalFieldOf("Redstone", RedstoneMode.ALWAYS).forGetter(Bay::redstone)
        ).apply(i, Bay::new));

        public static Bay empty(int index) {
            return new Bay(index, Optional.empty(), FaceConfig.NONE, "", RedstoneMode.ALWAYS);
        }

        public Bay withHosted(Optional<ResourceLocation> nowHosted) {
            return new Bay(index, nowHosted, faces, name, redstone);
        }

        public Bay withFaces(FaceConfig nowFaces) {
            return new Bay(index, hosted, nowFaces, name, redstone);
        }

        public Bay withName(String nowName) {
            return new Bay(index, hosted, faces, nowName, redstone);
        }

        public Bay withRedstone(RedstoneMode nowRedstone) {
            return new Bay(index, hosted, faces, name, nowRedstone);
        }
    }

    /** The bay at an index, minting an empty one rather than returning nothing for a bay in range. */
    public Bay bay(int index) {
        return bays.stream().filter(b -> b.index() == index).findFirst().orElseGet(() -> Bay.empty(index));
    }

    /** Replaces one bay, adding it if this Workbay had never written that index before. */
    public WorkbayRecord withBay(Bay bay) {
        List<Bay> updated = new java.util.ArrayList<>(bays.stream()
            .filter(b -> b.index() != bay.index()).toList());
        updated.add(bay);
        updated.sort(java.util.Comparator.comparingInt(Bay::index));
        return withBays(updated);
    }

    /**
     * Upgrades are consumed on install and recorded as counters — there is no upgrade inventory and
     * no removal path (SPEC.md §1). {@code roomTier} and {@code annexPlates} are v2's, written and
     * carried in v1 so that v2 adds behaviour rather than a migration.
     */
    public record Upgrades(int expansionPlates, int resonators, int anchors, int annexPlates,
        int roomTier, int multichannel, int impellers) {
        public static final Upgrades NONE = new Upgrades(0, 0, 0, 0, 0, 0, 0);

        /**
         * What one Impeller is worth, on both halves of what a link does.
         *
         * <p>It doubles how much moves in a step <b>and</b> halves the wait between steps, so a
         * level is worth four and the two-level ladder is worth sixteen. Split rather than all on
         * the rate because a link that moves a bigger pile once every twenty ticks still looks
         * stalled; the wait is the half a player watches.
         *
         * <p>The top of that ladder puts an item link at EnderIO's enhanced conduit and an energy
         * link just under its plain one, which is where a mod whose promise is space rather than
         * throughput (SPEC.md §0) belongs: fast enough to feed what you racked, never the reason
         * to build here.
         */
        public static int impellerStep() {
            return com.neryos.workbay.config.WorkbayConfig.SERVER.impellerStep.get();
        }

        public static final Codec<Upgrades> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("ExpansionPlates", 0).forGetter(Upgrades::expansionPlates),
            Codec.INT.optionalFieldOf("Resonators", 0).forGetter(Upgrades::resonators),
            Codec.INT.optionalFieldOf("Anchors", 0).forGetter(Upgrades::anchors),
            Codec.INT.optionalFieldOf("AnnexPlates", 0).forGetter(Upgrades::annexPlates),
            Codec.INT.optionalFieldOf("RoomTier", 0).forGetter(Upgrades::roomTier),
            Codec.INT.optionalFieldOf("Multichannel", 0).forGetter(Upgrades::multichannel),
            Codec.INT.optionalFieldOf("Impellers", 0).forGetter(Upgrades::impellers)
        ).apply(i, Upgrades::new));

        /** One more of the named upgrade. Upgrades are consumed on install (SPEC.md §1). */
        public Upgrades plus(com.neryos.workbay.content.workbay.WorkbayUpgrade upgrade) {
            return switch (upgrade) {
                case EXPANSION_PLATE -> new Upgrades(expansionPlates + 1, resonators, anchors,
                    annexPlates, roomTier, multichannel, impellers);
                case RESONATOR -> new Upgrades(expansionPlates, resonators + 1, anchors,
                    annexPlates, roomTier, multichannel, impellers);
                case MULTICHANNEL -> new Upgrades(expansionPlates, resonators, anchors,
                    annexPlates, roomTier, multichannel + 1, impellers);
                case IMPELLER -> new Upgrades(expansionPlates, resonators, anchors,
                    annexPlates, roomTier, multichannel, impellers + 1);
                case ANNEX_PLATE -> new Upgrades(expansionPlates, resonators, anchors,
                    annexPlates + 1, roomTier, multichannel, impellers);
                case ANCHOR -> new Upgrades(expansionPlates, resonators, anchors + 1,
                    annexPlates, roomTier, multichannel, impellers);
                // Highest wins, and never down: a smaller Frame fitted over a bigger room would
                // put bedrock through a factory somebody built. install() refuses it first; this
                // is the second half of the same rule, where the number actually changes.
                case ROOM_FRAME, WIDE_ROOM_FRAME, VAST_ROOM_FRAME ->
                    new Upgrades(expansionPlates, resonators, anchors, annexPlates,
                        Math.max(roomTier, upgrade.roomTier()), multichannel, impellers);
            };
        }

        public int installed(com.neryos.workbay.content.workbay.WorkbayUpgrade upgrade) {
            return switch (upgrade) {
                case EXPANSION_PLATE -> expansionPlates;
                case RESONATOR -> resonators;
                case MULTICHANNEL -> multichannel;
                case IMPELLER -> impellers;
                case ANNEX_PLATE -> annexPlates;
                case ANCHOR -> anchors;
                // 1 once the room is already at least this big, so the install path refuses it as
                // maxed rather than needing a rule of its own.
                case ROOM_FRAME, WIDE_ROOM_FRAME, VAST_ROOM_FRAME ->
                    roomTier >= upgrade.roomTier() ? 1 : 0;
            };
        }

        /**
         * What every link on this Workbay multiplies its rate by, and divides its wait by. One
         * with no Impeller fitted, and the same number for both halves.
         */
        public int impellerFactor() {
            int factor = 1;
            int step = impellerStep();
            for (int i = 0; i < impellers; i++) {
                factor *= step;
            }
            return factor;
        }

        public boolean anchored() {
            return anchors > 0;
        }

        public boolean canCrossDimensions() {
            return resonators > 0;
        }
    }
}
