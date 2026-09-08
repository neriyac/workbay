package com.neryos.workbay.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where a room is, and how big. SPEC.md §8, and nothing else in the mod may work this out for
 * itself.
 *
 * <p>Pure arithmetic, like {@link BayGeometry} and for the same reason: every coordinate here is
 * baked into saved worlds the moment anyone builds in a room, and there are no mod DataFixers to
 * move a chest afterwards.
 *
 * <p><b>A room's size is its footprint.</b> A chunk ticket is a column, so the vertical axis costs
 * nothing and all three tiers are the same height; only the square grows, and each square is an
 * exact number of chunks from a chunk-aligned corner — 1, 4 and 9. That number is the room's whole
 * price and the room screen prints it.
 */
public final class RoomGeometry {
    private RoomGeometry() {}

    /** Rooms per network at most: one Room Frame plus three Annex Plates. SPEC.md §1. */
    public static final int MAX_ROOMS = 4;

    /** Interior side, by Room Frame tier. Index 0 is "no Frame installed", which is no room. */
    private static final int[] INTERIOR = {0, 14, 30, 46};

    /** The highest tier there is, and the footprint every region reserves. */
    public static final int MAX_TIER = INTERIOR.length - 1;

    /** Interior height, the same at every tier because height costs no chunks. */
    public static final int HEIGHT = 32;

    /** The shell's floor. The interior is y 1..HEIGHT and the ceiling is the one above it. */
    public static final int FLOOR_Y = 0;

    /**
     * Blocks between one region's origin and the next. Compact Machines' answer to sound bleed,
     * and it costs nothing: the Backshop is empty and infinite.
     */
    private static final int REGION_SPACING = 512;

    /** Far negative x, so a room can never meet a bay column in the positive quadrant. */
    private static final int REGION_ORIGIN_X = -1_048_576;

    /** Regions per row before the allocator steps in z. */
    private static final int REGIONS_PER_ROW = 512;

    /**
     * The low corner of region {@code i}'s reserved space, and the low corner of the shell built
     * in it at <b>every</b> tier.
     *
     * <p>Corner-anchored on purpose: a larger Room Frame only ever moves the far walls outward, so
     * the entry pad, the Exit block and every coordinate the player built at stay where they were.
     * Centring the room would preserve blocks too, and would move the one landmark a lost player is
     * told to walk to.
     */
    public static BlockPos origin(int region) {
        return new BlockPos(REGION_ORIGIN_X + (region % REGIONS_PER_ROW) * REGION_SPACING, FLOOR_Y,
            (region / REGIONS_PER_ROW) * REGION_SPACING);
    }

    /** Interior side for a Room Frame tier, or 0 for a network with no Frame installed. */
    public static int interior(int tier) {
        return INTERIOR[Math.max(0, Math.min(MAX_TIER, tier))];
    }

    /** Outside side, walls included. 16, 32 or 48 — always a whole number of chunks. */
    public static int footprint(int tier) {
        int inside = interior(tier);
        return inside == 0 ? 0 : inside + 2;
    }

    /** What an anchored room of this tier holds loaded: 1, 4 or 9. The room screen prints it. */
    public static int chunkCost(int tier) {
        int chunks = footprint(tier) / 16;
        return chunks * chunks;
    }

    /** The shell's ceiling layer. */
    public static int ceilingY(int tier) {
        return FLOOR_Y + HEIGHT + 1;
    }

    /**
     * The Exit block, on the entry pad at the interior's low corner. It is at the region origin
     * offset, so no upgrade ever moves it — which is the point of anchoring the shell at a corner.
     */
    public static BlockPos exitPos(int region) {
        return origin(region).offset(1, 1, 1);
    }

    /** Where a visitor arrives: beside the Exit block, looking at it. */
    public static Vec3 entrySpot(int region) {
        BlockPos pad = origin(region).offset(2, 1, 2);
        return new Vec3(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
    }

    /** Facing the Exit block from {@link #entrySpot}: north-west, so both of it is in view. */
    public static final float ENTRY_YAW = 135.0F;

    /** The air a player may stand in. Everything outside it in the Backshop is not theirs. */
    public static AABB interiorBox(int region, int tier) {
        int inside = interior(tier);
        BlockPos low = origin(region).offset(1, 1, 1);
        return new AABB(low.getX(), low.getY(), low.getZ(),
            low.getX() + inside, low.getY() + HEIGHT, low.getZ() + inside);
    }

    /** True when a position is inside the room's air, walls excluded. */
    public static boolean inside(BlockPos pos, int region, int tier) {
        int inside = interior(tier);
        BlockPos low = origin(region).offset(1, 1, 1);
        return pos.getX() >= low.getX() && pos.getX() < low.getX() + inside
            && pos.getY() >= low.getY() && pos.getY() < low.getY() + HEIGHT
            && pos.getZ() >= low.getZ() && pos.getZ() < low.getZ() + inside;
    }

    /** Every chunk a room of this tier occupies: what an anchored room forces, one ticket each. */
    public static java.util.List<ChunkPos> chunks(int region, int tier) {
        int side = footprint(tier) / 16;
        BlockPos origin = origin(region);
        ChunkPos first = new ChunkPos(origin);
        java.util.List<ChunkPos> out = new java.util.ArrayList<>(side * side);
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                out.add(new ChunkPos(first.x + x, first.z + z));
            }
        }
        return out;
    }
}
