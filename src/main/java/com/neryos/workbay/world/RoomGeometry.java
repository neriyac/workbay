package com.neryos.workbay.world;

import com.neryos.workbay.content.room.RoomPart;
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

    /**
     * Interior height: <b>the same as the interior side</b>, so every room is a cube.
     *
     * <p>Height still costs no chunks — a ticket is a column, and that is why the <em>price</em>
     * is the footprint and nothing else. What does not follow from a free axis is that it should
     * be 32 at every tier: 14 across and 32 tall is a shaft, not a room. Compact Machines' sizes
     * are cubes for the same reason, and a cube is the one proportion nobody has to think about.
     */
    public static int height(int tier) {
        return interior(tier);
    }

    /** The shell's floor. The interior is y 1..height(tier) and the ceiling is the one above. */
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
        return FLOOR_Y + height(tier) + 1;
    }

    /**
     * The four doors, as a block position to the part it draws. One <b>2×2 door</b> in the middle
     * of each of the four walls, sitting on the floor.
     *
     * <p>Two wide because a wall is a whole number of chunks across and sixteen has no middle
     * block: a one-block doorway is off-centre by half a block, which is exactly what it looks
     * like. Two straddles the seam and is dead centre. Left and right are named looking
     * <em>into</em> the room, which is the only side anybody ever sees.
     */
    public static java.util.Map<BlockPos, RoomPart> doors(int region, int tier) {
        int side = footprint(tier);
        java.util.Map<BlockPos, RoomPart> out = new java.util.LinkedHashMap<>();
        if (side < 4) {
            return out;
        }
        int a = side / 2 - 1;
        int b = side / 2;
        BlockPos o = origin(region);
        for (int i = 0; i < 2; i++) {
            int y = 1 + i;
            RoomPart left = i == 0 ? RoomPart.DOOR_BOTTOM_LEFT : RoomPart.DOOR_TOP_LEFT;
            RoomPart right = i == 0 ? RoomPart.DOOR_BOTTOM_RIGHT : RoomPart.DOOR_TOP_RIGHT;
            // West wall (x = 0), seen looking east: -z is on the viewer's left.
            out.put(o.offset(0, y, a), left);
            out.put(o.offset(0, y, b), right);
            // East wall, seen looking west: the order flips.
            out.put(o.offset(side - 1, y, b), left);
            out.put(o.offset(side - 1, y, a), right);
            // North wall (z = 0), seen looking south.
            out.put(o.offset(b, y, 0), left);
            out.put(o.offset(a, y, 0), right);
            // South wall.
            out.put(o.offset(a, y, side - 1), left);
            out.put(o.offset(b, y, side - 1), right);
        }
        return out;
    }

    /** Where a visitor arrives: on the pad in the low corner. */
    public static Vec3 entrySpot(int region) {
        BlockPos pad = origin(region).offset(2, 1, 2);
        return new Vec3(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
    }

    /** Facing into the room from {@link #entrySpot}, which is its low corner. */
    public static final float ENTRY_YAW = -45.0F;

    /** The air a player may stand in. Everything outside it in the Backshop is not theirs. */
    public static AABB interiorBox(int region, int tier) {
        int inside = interior(tier);
        BlockPos low = origin(region).offset(1, 1, 1);
        return new AABB(low.getX(), low.getY(), low.getZ(),
            low.getX() + inside, low.getY() + height(tier), low.getZ() + inside);
    }

    /** True when a position is inside the room's air, walls excluded. */
    public static boolean inside(BlockPos pos, int region, int tier) {
        int inside = interior(tier);
        BlockPos low = origin(region).offset(1, 1, 1);
        return pos.getX() >= low.getX() && pos.getX() < low.getX() + inside
            && pos.getY() >= low.getY() && pos.getY() < low.getY() + height(tier)
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
