package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.neryos.workbay.content.room.RoomColour;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Optional;
import java.util.UUID;

/**
 * One room. SPEC.md §8.
 *
 * <p>Kept in {@link RoomRegistry} and referenced by {@code WorkbayRecord#rooms()}, which holds only
 * the UUIDs and their order — the same split bays do not need, because a room outlives the Workbay
 * that made it and {@code /workbay recover} has to find one with no block in the world.
 *
 * <p>{@code builtTier} is what is <b>standing in the Backshop</b>, not what the network is entitled
 * to: the network's Room Frame says how big every room should be, and this says how big this one
 * currently is, which is the only way expanding in place knows which walls to clear.
 */
public record RoomRecord(UUID id, int region, Optional<String> name, int builtTier,
    boolean anchored, Optional<ResourceKey<Biome>> biome, RoomColour colour,
    /**
     * Who else may be in here, and how much of it they get. Empty on every room until somebody is
     * invited, which is the point: <b>an invitation is to one room, never to the dimension.</b>
     *
     * <p>A list rather than a map because the screen has to draw a name beside each level, and the
     * name is not derivable from a UUID without a profile lookup the client cannot make.
     */
    java.util.List<RoomRecord.Guest> guests) {

    /**
     * One invitation. The name is what it was when the invitation was made and is only ever drawn;
     * the id is what every check uses, so somebody who renames themselves keeps their access and
     * somebody who takes their old name does not inherit it.
     */
    public record Guest(UUID id, String name, RoomGuest level) {
        public static final Codec<Guest> CODEC = RecordCodecBuilder.create(g -> g.group(
            UUIDUtil.CODEC.fieldOf("Id").forGetter(Guest::id),
            Codec.STRING.fieldOf("Name").forGetter(Guest::name),
            RoomGuest.CODEC.fieldOf("Level").forGetter(Guest::level)
        ).apply(g, Guest::new));
    }

    public static final Codec<RoomRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("Id").forGetter(RoomRecord::id),
        Codec.INT.fieldOf("Region").forGetter(RoomRecord::region),
        Codec.STRING.optionalFieldOf("Name").forGetter(RoomRecord::name),
        Codec.INT.optionalFieldOf("BuiltTier", 0).forGetter(RoomRecord::builtTier),
        Codec.BOOL.optionalFieldOf("Anchored", false).forGetter(RoomRecord::anchored),
        ResourceKey.codec(Registries.BIOME).optionalFieldOf("Biome").forGetter(RoomRecord::biome),
        // Absent on every room saved before rooms had a colour, and those rooms are bedrock: the
        // default is the shade that reads closest to it, so nothing visibly changes under them
        // until somebody chooses.
        RoomColour.CODEC.optionalFieldOf("Colour", RoomColour.DEFAULT).forGetter(RoomRecord::colour),
        // Optional, so every room saved before guests existed loads with nobody invited -- which
        // is the correct answer for one and not a migration.
        Guest.CODEC.listOf().optionalFieldOf("Guests", java.util.List.of())
            .forGetter(RoomRecord::guests)
    ).apply(i, RoomRecord::new));

    /**
     * A fresh room in a region nobody else has. Unbuilt until somebody opens it: SPEC.md §8 spends
     * the shell on first entry, so a Frame installed and never used costs nothing.
     */
    public static RoomRecord fresh(UUID id, int region) {
        return new RoomRecord(id, region, Optional.empty(), 0, false, Optional.empty(),
            RoomColour.DEFAULT, java.util.List.of());
    }

    /** True once the shell exists in the Backshop. An unbuilt room lists as {@code Empty}. */
    public boolean built() {
        return builtTier > 0;
    }

    /**
     * The biome the room's chunks carry. Plains by default because it is the one biome where grass
     * grows, water does not freeze and nothing is on fire — the neutral answer.
     */
    public ResourceKey<Biome> effectiveBiome() {
        return biome.orElse(Biomes.PLAINS);
    }

    /** What an anchored room of this size holds loaded. Zero until it is built. */
    public int chunkCost() {
        return RoomGeometry.chunkCost(builtTier);
    }

    public RoomRecord withBuiltTier(int tier) {
        return new RoomRecord(id, region, name, tier, anchored, biome, colour, guests);
    }

    public RoomRecord withName(Optional<String> nowName) {
        return new RoomRecord(id, region, nowName, builtTier, anchored, biome, colour, guests);
    }

    public RoomRecord withAnchored(boolean nowAnchored) {
        return new RoomRecord(id, region, name, builtTier, nowAnchored, biome, colour, guests);
    }

    public RoomRecord withColour(RoomColour nowColour) {
        return new RoomRecord(id, region, name, builtTier, anchored, biome, nowColour, guests);
    }

    /** What this player may do in here, or empty for somebody who was never invited. */
    public Optional<RoomGuest> guestLevel(UUID player) {
        return guests.stream().filter(guest -> guest.id().equals(player))
            .map(Guest::level).findFirst();
    }

    /** Invites, or changes an existing invitation's level. One entry per player, always. */
    public RoomRecord withGuest(UUID player, String playerName, RoomGuest level) {
        java.util.List<Guest> updated = new java.util.ArrayList<>(
            guests.stream().filter(guest -> !guest.id().equals(player)).toList());
        updated.add(new Guest(player, playerName, level));
        return new RoomRecord(id, region, name, builtTier, anchored, biome, colour,
            java.util.List.copyOf(updated));
    }

    public RoomRecord withoutGuest(UUID player) {
        return new RoomRecord(id, region, name, builtTier, anchored, biome, colour,
            guests.stream().filter(guest -> !guest.id().equals(player)).toList());
    }

    public RoomRecord withBiome(ResourceKey<Biome> nowBiome) {
        return new RoomRecord(id, region, name, builtTier, anchored, Optional.of(nowBiome), colour,
            guests);
    }
}
