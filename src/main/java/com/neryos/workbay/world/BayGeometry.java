package com.neryos.workbay.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Where a bay is. SPEC.md §8, and nothing else in the mod may work this out for itself.
 *
 * <p>Pure arithmetic on purpose: every Y coordinate here is baked into saved worlds the moment
 * anyone plays, and there are no mod DataFixers to move a bay afterwards. Changing a constant in
 * this class strands every machine already hosted.
 */
public final class BayGeometry {
    private BayGeometry() {}

    /** A 3x3x3 interior inside a 5x5x5 shell: the machine at the centre, a Port on each face. */
    public static final int INTERIOR = 3;
    public static final int SHELL = 5;

    /** Bay 0's floor. Above bedrock-equivalent depth, and clear of the dimension's min_y of 0. */
    public static final int FIRST_FLOOR_Y = 8;

    /** 5 for the shell plus a 3-block gap, so eight bays sit between y 8 and y 68. */
    public static final int BAY_PITCH = 8;

    /** SPEC.md §4: eight bay buttons fit the screen strip without scrolling, so eight is the cap. */
    public static final int MAX_BAYS = 8;

    /** North-west, bottom corner of the bay's shell. Chunk-local (6, 6), so the shell fits one chunk. */
    public static BlockPos shellOrigin(ChunkPos column, int bay) {
        return new BlockPos(column.getMinBlockX() + 6, FIRST_FLOOR_Y + bay * BAY_PITCH, column.getMinBlockZ() + 6);
    }

    /** Where the hosted machine goes: the middle of the interior, two in from the shell corner. */
    public static BlockPos machinePos(ChunkPos column, int bay) {
        return shellOrigin(column, bay).offset(2, 2, 2);
    }

    /** The Port on one face of the interior — always directly against the machine. */
    public static BlockPos portPos(ChunkPos column, int bay, Direction face) {
        return machinePos(column, bay).relative(face);
    }

    /** True for the six interior positions that hold a Port. */
    public static boolean isPort(ChunkPos column, int bay, BlockPos pos) {
        BlockPos machine = machinePos(column, bay);
        for (Direction face : Direction.values()) {
            if (machine.relative(face).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which bay a position falls inside, or -1 for the gap between bays and anything outside them.
     * The inverse of {@link #shellOrigin}, and the only one there will ever be.
     */
    public static int bayAt(BlockPos pos) {
        int offset = pos.getY() - FIRST_FLOOR_Y;
        int bay = Math.floorDiv(offset, BAY_PITCH);
        return bay >= 0 && bay < MAX_BAYS && offset - bay * BAY_PITCH < SHELL ? bay : -1;
    }

    /**
     * The machine a Port stands against, or null when this is not a Port.
     *
     * <p>Exists because a Port is the machine's <b>door</b> as well as its socket: the six of them
     * seal the machine on every face, so a player standing in the bay cannot right-click it
     * directly and the Port has to pass the click through.
     */
    public static BlockPos machineBehind(BlockPos portPos) {
        int bay = bayAt(portPos);
        if (bay < 0) {
            return null;
        }
        ChunkPos column = new ChunkPos(portPos);
        return isPort(column, bay, portPos) ? machinePos(column, bay) : null;
    }

    /** Highest block a bay occupies, so callers can check the dimension is tall enough. */
    public static int topY(int bay) {
        return FIRST_FLOOR_Y + bay * BAY_PITCH + SHELL - 1;
    }
}
