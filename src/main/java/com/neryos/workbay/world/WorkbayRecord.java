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
    int deployedCount,
    Assay assay) {

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
        Codec.INT.optionalFieldOf("DeployedCount", 0).forGetter(WorkbayRecord::deployedCount),
        // Nested rather than three more fields on the root: the codec group caps at sixteen and
        // these three only ever mean anything together. Absent in a record written before the
        // Assay existed, which reads back as no Levy, nothing skimmed and a rate of zero -- which
        // is exactly what that Workbay had.
        Assay.CODEC.optionalFieldOf("Assay", Assay.NONE).forGetter(WorkbayRecord::assay)
    ).apply(i, WorkbayRecord::new));

    public WorkbayRecord withUpgrades(Upgrades newUpgrades) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, newUpgrades,
            lastKnownPos, bays, rooms, buses, deployedCount, assay);
    }

    public WorkbayRecord withLastKnownPos(GlobalPos pos) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            Optional.of(pos), bays, rooms, buses, deployedCount, assay);
    }

    public WorkbayRecord withLocked(boolean nowLocked) {
        return new WorkbayRecord(id, code, owner, ownerName, nowLocked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, buses, deployedCount, assay);
    }

    public WorkbayRecord withBays(List<Bay> newBays) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, List.copyOf(newBays), rooms, buses, deployedCount, assay);
    }

    public WorkbayRecord withBuses(List<com.neryos.workbay.bus.BusConfig> newBuses) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, List.copyOf(newBuses), deployedCount, assay);
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
            lastKnownPos, bays, rooms, buses, Math.max(0, nowDeployedCount), assay);
    }

    public WorkbayRecord withAssay(Assay nowAssay) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, bays, rooms, buses, deployedCount, nowAssay);
    }

    /**
     * Everything the Assay is holding for this network: Levy banked, tagged items skimmed and not
     * yet converted, and the rate the player set. SPEC.md §3.
     *
     * <p>On the record and not in a block entity, because the Assay has no faces and therefore
     * nothing physical to hand a Levy item to. It is a balance, and the upgrades screen is where it
     * is read and spent. That also means it survives exactly as well as bays and upgrades do:
     * breaking the Workbay does not spend somebody's Levy.
     */
    public record Assay(int levy, int skimmed, int rate) {
        public static final Assay NONE = new Assay(0, 0, 0);

        public static final Codec<Assay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("Levy", 0).forGetter(Assay::levy),
            Codec.INT.optionalFieldOf("Skimmed", 0).forGetter(Assay::skimmed),
            Codec.INT.optionalFieldOf("Rate", 0).forGetter(Assay::rate)
        ).apply(i, Assay::new));

        public Assay withLevy(int nowLevy) {
            return new Assay(Math.max(0, nowLevy), skimmed, rate);
        }

        public Assay withSkimmed(int nowSkimmed) {
            return new Assay(levy, Math.max(0, nowSkimmed), rate);
        }

        public Assay withRate(int nowRate) {
            return new Assay(levy, skimmed, nowRate);
        }
    }

    /**
     * The base Workbay's bays, before any Expansion Plate. <b>Two, not one.</b>
     *
     * <p>One is a deadlock. The Assay occupies a bay (SPEC.md §3) and exposes no faces, so a link on
     * its own bay reaches nothing; Levy is only made by skimming goods moving through a link, so
     * making any Levy at all needs a second bay with traffic in it. Under the per-block model the
     * player crafted a second Workbay for that; SPEC.md §14's one-deployed-Workbay-per-network
     * closed that door and turned a soft ceiling into a hard stop with no way out.
     */
    public static final int BASE_BAYS = 2;

    /**
     * How many bays this Workbay may use: {@link #BASE_BAYS}, plus one per Expansion Plate, capped
     * by the server's {@code maxBaysPerWorkbay}. The cap is applied here rather than at install time
     * so lowering it never destroys an Expansion Plate somebody already paid Levy for.
     */
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
            };
        }

        public int installed(com.neryos.workbay.content.workbay.WorkbayUpgrade upgrade) {
            return switch (upgrade) {
                case EXPANSION_PLATE -> expansionPlates;
                case RESONATOR -> resonators;
                case MULTICHANNEL -> multichannel;
                case IMPELLER -> impellers;
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
