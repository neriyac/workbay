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
     * the entry pad and every coordinate the player built at stay where they were. Centring the
     * room would preserve blocks too, and would move the pad a lost player is told to walk to.
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

    /** The room's own footprint in chunks: 1, 4 or 9. One forced ticket per chunk of it. */
    public static int chunkCost(int tier) {
        int chunks = footprint(tier) / 16;
        return chunks * chunks;
    }

    /**
     * What anchoring a room of that footprint really keeps loaded, which is <b>not</b> the number
     * of tickets it takes.
     *
     * <p>A forced chunk is held at the entity-ticking level, and its neighbours are dragged up to
     * merely loaded two chunks out — so one ticket is a five-by-five square. Measured in the
     * Backshop rather than reasoned from the ticket API: a tier-1 room whose page said "1 chunk"
     * had twenty-five chunks loaded around it, and a Vast room said 9 and had forty-nine. The
     * screen prints this now, because the number a host is paying is the one that is loaded.
     */
    public static int anchorChunks(int footprintChunks) {
        // Every footprint chunk gets its own ticket, and a ticket reaches chunkTicketRadius
        // further on all four sides -- so the loaded square is the footprint's side plus twice
        // the radius. Read from the knob, not written down: 25/36/49 at radius 2, 9/16/25 at the
        // shipped 1. OPEN_ISSUES #60.
        int side = (int) Math.round(Math.sqrt(footprintChunks)) + 2 * WorkbayTickets.radius();
        return footprintChunks == 0 ? 0 : side * side;
    }

    /**
     * Shell-relative coordinates, on one axis, of the ceiling's light fixtures. OPEN_ISSUES #45.
     *
     * <p>One roughly every eight blocks and symmetric about the middle, so a 14 room gets two per
     * axis (four lamps), a 30 gets four (sixteen) and a 46 gets six (thirty-six) — and every tier
     * lands three blocks in from each wall, which is what stops the grid reading as having been cut
     * off at one end. The spacing is the number, not the count: a lamp every eight blocks is the
     * same room at every size, and a fixed count would put four lamps in a 46-block hall.
     */
    public static int[] lightAxis(int tier) {
        int inside = interior(tier);
        if (inside == 0) {
            return new int[0];
        }
        int lamps = Math.max(1, Math.round(inside / 8.0F));
        int[] out = new int[lamps];
        for (int i = 0; i < lamps; i++) {
            // Shell-relative, so the +1 is the wall the interior starts after.
            out[i] = 1 + (int) ((i + 0.5) * inside / lamps);
        }
        return out;
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
     * like. Two straddles the seam and is dead centre.
     *
     * <p><b>Left and right are the viewer's, and the viewer is inside.</b> The first draft named
     * them looking <em>into</em> the room, which is the one side nobody is ever on — a player
     * stands in the room and looks <em>out</em> at the wall. Every wall came out mirrored: both
     * handles on the outer edges, a hinge stile down the middle of each leaf, two single doors hung
     * backwards rather than one double door. Found by Neriya on the first screenshot of a room.
     * {@code aDoorsLeavesMeetInTheMiddle} is the guard.
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
            // West wall (x = 0), seen from inside looking west: +z is on the viewer's left.
            out.put(o.offset(0, y, b), left);
            out.put(o.offset(0, y, a), right);
            // East wall, seen looking east: the order flips.
            out.put(o.offset(side - 1, y, a), left);
            out.put(o.offset(side - 1, y, b), right);
            // North wall (z = 0), seen looking north: -x is on the viewer's left.
            out.put(o.offset(a, y, 0), left);
            out.put(o.offset(b, y, 0), right);
            // South wall.
            out.put(o.offset(b, y, side - 1), left);
            out.put(o.offset(a, y, side - 1), right);
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
