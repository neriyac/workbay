package com.neryos.workbay.world;

import com.neryos.workbay.init.WBBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a bay in the Backshop. SPEC.md §8.
 *
 * <p>A 5x5x5 shell around a 3x3x3 interior, a Port on each of the interior's six faces, and the
 * machine in the middle. The shape is the whole reason bays are not bare 1x1x1 holes: a machine
 * with air neighbours has nothing to push into, every {@code hasNeighborSignal} machine is dead,
 * and anything writing a bounding block at {@code pos.above()} corrupts.
 */
public final class BayBuilder {
    private BayBuilder() {}

    /**
     * Bedrock: nothing a survival player carries gets through it, and it has no gravity, no
     * burning, no piston interaction and no block entity. It is the layer <em>under</em> the
     * rule that keeps a visitor in a bay ({@link BayVisit}: no screen open, no visit), not the
     * rule itself. SPEC.md §2 lists exactly three blocks for this mod and a wall material is not
     * one of them, so it is vanilla.
     */
    private static final BlockState WALL = Blocks.BEDROCK.defaultBlockState();

    /**
     * Builds the bay if it is not there already, and returns where the machine goes.
     *
     * <p>Idempotent: it is called every time a machine is racked, and a bay that already exists must
     * not be rebuilt around a machine that is standing in it.
     */
    public static BlockPos ensure(ServerLevel backshop, ChunkPos column, int bay) {
        BlockPos machine = BayGeometry.machinePos(column, bay);
        if (backshop.getBlockState(BayGeometry.portPos(column, bay, Direction.UP))
            .is(WBBlocks.PORT.get())) {
            reshell(backshop, column, bay);
            return machine;
        }
        build(backshop, column, bay);
        return machine;
    }

    /**
     * Brings an older bay's walls up to the current material without touching anything inside it.
     *
     * <p>{@link #ensure} returns early once the Ports are there, which is right — a rebuild would
     * replace the machine standing in the middle with air. But it also meant every bay built before
     * the walls became bedrock kept its obsidian, in every save already played: a shell a survival
     * player can break out of, backing a rule that says nobody is in the Backshop without a screen
     * open. The shell is not the rule, but it is not decoration either.
     *
     * <p>Only the shell's 98 outer positions, and only the ones that are not already {@link #WALL},
     * so nothing in the interior can be reached from here whatever it holds. One block state read
     * in the common case: the walls are checked only when a corner is not bedrock.
     */
    private static void reshell(ServerLevel backshop, ChunkPos column, int bay) {
        BlockPos origin = BayGeometry.shellOrigin(column, bay);
        if (backshop.getBlockState(origin).is(WALL.getBlock())) {
            return;
        }
        for (int x = 0; x < BayGeometry.SHELL; x++) {
            for (int y = 0; y < BayGeometry.SHELL; y++) {
                for (int z = 0; z < BayGeometry.SHELL; z++) {
                    boolean interior = x > 0 && x < BayGeometry.SHELL - 1
                        && y > 0 && y < BayGeometry.SHELL - 1
                        && z > 0 && z < BayGeometry.SHELL - 1;
                    if (interior) {
                        continue;
                    }
                    backshop.setBlock(origin.offset(x, y, z), WALL, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void build(ServerLevel backshop, ChunkPos column, int bay) {
        BlockPos origin = BayGeometry.shellOrigin(column, bay);
        BlockState port = WBBlocks.PORT.get().defaultBlockState();
        BlockPos machine = BayGeometry.machinePos(column, bay);

        for (int x = 0; x < BayGeometry.SHELL; x++) {
            for (int y = 0; y < BayGeometry.SHELL; y++) {
                for (int z = 0; z < BayGeometry.SHELL; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    boolean interior = x > 0 && x < BayGeometry.SHELL - 1
                        && y > 0 && y < BayGeometry.SHELL - 1
                        && z > 0 && z < BayGeometry.SHELL - 1;
                    BlockState state;
                    if (!interior) {
                        state = WALL;
                    } else if (BayGeometry.isPort(column, bay, pos)) {
                        state = port;
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    // UPDATE_CLIENTS, not UPDATE_ALL: nothing observes the Backshop and neighbour
                    // updates across a hundred blocks per bay are pure cost.
                    backshop.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
        backshop.setBlock(machine, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** True when the bay's shape is intact: six Ports around an empty-or-occupied middle. */
    public static boolean isBuilt(ServerLevel backshop, ChunkPos column, int bay) {
        for (Direction face : Direction.values()) {
            if (!backshop.getBlockState(BayGeometry.portPos(column, bay, face)).is(WBBlocks.PORT.get())) {
                return false;
            }
        }
        return true;
    }
}
