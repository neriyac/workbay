package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
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
    List<UUID> rooms) {

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
        UUIDUtil.CODEC.listOf().fieldOf("Rooms").forGetter(WorkbayRecord::rooms)
    ).apply(i, WorkbayRecord::new));

    public WorkbayRecord withUpgrades(Upgrades newUpgrades) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, newUpgrades,
            lastKnownPos, bays, rooms);
    }

    public WorkbayRecord withLastKnownPos(GlobalPos pos) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            Optional.of(pos), bays, rooms);
    }

    public WorkbayRecord withLocked(boolean nowLocked) {
        return new WorkbayRecord(id, code, owner, ownerName, nowLocked, bayColumn, upgrades,
            lastKnownPos, bays, rooms);
    }

    public WorkbayRecord withBays(List<Bay> newBays) {
        return new WorkbayRecord(id, code, owner, ownerName, locked, bayColumn, upgrades,
            lastKnownPos, List.copyOf(newBays), rooms);
    }

    /**
     * How many bays this Workbay may use: the base one, plus one per Expansion Plate, capped by the
     * server's {@code maxBaysPerWorkbay}. The cap is applied here rather than at install time so
     * lowering it never destroys an Expansion Plate somebody already paid Levy for.
     */
    public int bayCapacity() {
        return Math.min(1 + upgrades.expansionPlates(),
            com.neryos.workbay.config.WorkbayConfig.SERVER.maxBaysPerWorkbay.get());
    }

    /**
     * One bay. {@code hosted} is the block last known to be racked here, and it is deliberately
     * <em>not</em> cleared when that block's mod disappears — that is the {@code was:} record
     * SPEC.md §14 requires, and what {@code /workbay orphans} reports.
     */
    public record Bay(int index, Optional<ResourceLocation> hosted, FaceConfig faces) {
        public static final Codec<Bay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Index").forGetter(Bay::index),
            ResourceLocation.CODEC.optionalFieldOf("Hosted").forGetter(Bay::hosted),
            FaceConfig.CODEC.optionalFieldOf("Faces", FaceConfig.NONE).forGetter(Bay::faces)
        ).apply(i, Bay::new));

        public static Bay empty(int index) {
            return new Bay(index, Optional.empty(), FaceConfig.NONE);
        }

        public Bay withHosted(Optional<ResourceLocation> nowHosted) {
            return new Bay(index, nowHosted, faces);
        }

        public Bay withFaces(FaceConfig nowFaces) {
            return new Bay(index, hosted, nowFaces);
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
        int roomTier, int multichannel) {
        public static final Upgrades NONE = new Upgrades(0, 0, 0, 0, 0, 0);

        public static final Codec<Upgrades> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("ExpansionPlates", 0).forGetter(Upgrades::expansionPlates),
            Codec.INT.optionalFieldOf("Resonators", 0).forGetter(Upgrades::resonators),
            Codec.INT.optionalFieldOf("Anchors", 0).forGetter(Upgrades::anchors),
            Codec.INT.optionalFieldOf("AnnexPlates", 0).forGetter(Upgrades::annexPlates),
            Codec.INT.optionalFieldOf("RoomTier", 0).forGetter(Upgrades::roomTier),
            Codec.INT.optionalFieldOf("Multichannel", 0).forGetter(Upgrades::multichannel)
        ).apply(i, Upgrades::new));

        /** One more of the named upgrade. Upgrades are consumed on install (SPEC.md §1). */
        public Upgrades plus(com.neryos.workbay.content.workbay.WorkbayUpgrade upgrade) {
            return switch (upgrade) {
                case EXPANSION_PLATE -> new Upgrades(expansionPlates + 1, resonators, anchors,
                    annexPlates, roomTier, multichannel);
                case RESONATOR -> new Upgrades(expansionPlates, resonators + 1, anchors,
                    annexPlates, roomTier, multichannel);
                case MULTICHANNEL -> new Upgrades(expansionPlates, resonators, anchors,
                    annexPlates, roomTier, multichannel + 1);
            };
        }

        public int installed(com.neryos.workbay.content.workbay.WorkbayUpgrade upgrade) {
            return switch (upgrade) {
                case EXPANSION_PLATE -> expansionPlates;
                case RESONATOR -> resonators;
                case MULTICHANNEL -> multichannel;
            };
        }

        public boolean anchored() {
            return anchors > 0;
        }

        public boolean canCrossDimensions() {
            return resonators > 0;
        }
    }
}
