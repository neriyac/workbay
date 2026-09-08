package com.neryos.workbay.world;

import com.neryos.workbay.init.WBBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds and grows a room in the Backshop. SPEC.md §8.
 *
 * <p>Shell bedrock, interior air, one Exit block on the entry pad, and nothing else: a floor the
 * player did not choose is a floor they have to dig up. The chunks generate empty — {@code
 * FlatLevelSource} with no layers — so a room is a block write and never a worldgen cost.
 *
 * <p><b>Growth only ever moves the far walls outward.</b> Because the shell is corner-anchored at
 * the region origin, expanding turns the old walls into air and writes new ones where there was
 * only air. Nothing a player built can be in the way, and the old wall is cleared <em>only where it
 * is still bedrock</em>, so a creative player who built into it loses nothing.
 */
public final class RoomBuilder {
    private RoomBuilder() {}

    private static final BlockState WALL = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * Makes the room in the world match {@code tier}, and returns the record that says so.
     *
     * <p>Idempotent, and cheap in the common case: a room already at the right tier is one block
     * read. Called on every entry, because a room whose Frame was upgraded while nobody was in it
     * has to grow before anyone stands in it.
     */
    public static RoomRecord ensure(ServerLevel backshop, RoomRecord room, int tier) {
        if (tier <= 0 || tier == room.builtTier()) {
            return room;
        }
        if (tier < room.builtTier()) {
            // Frames only ever go up (SPEC.md §1 has no removal path), so this is a datapack or a
            // config edit shrinking the ladder under a room that is already bigger. Shrinking would
            // put bedrock through somebody's factory; leaving it alone costs a few extra chunks.
            return room;
        }
        if (room.built()) {
            clearWalls(backshop, room.region(), room.builtTier());
        }
        shell(backshop, room.region(), tier);
        backshop.setBlock(RoomGeometry.exitPos(room.region()),
            WBBlocks.EXIT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        RoomRecord grown = room.withBuiltTier(tier);
        // After the shell, and on every growth: a bigger room reaches chunks that were never
        // written, and they would otherwise carry whatever the Backshop generates.
        RoomBiomes.apply(backshop, grown);
        return grown;
    }

    /** Writes the six faces of the shell, leaving everything inside them alone. */
    private static void shell(ServerLevel backshop, int region, int tier) {
        int side = RoomGeometry.footprint(tier);
        int top = RoomGeometry.HEIGHT + 1;
        BlockPos origin = RoomGeometry.origin(region);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                boolean edge = x == 0 || z == 0 || x == side - 1 || z == side - 1;
                for (int y = 0; y <= top; y++) {
                    if (!edge && y != 0 && y != top) {
                        continue;
                    }
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    // UPDATE_CLIENTS, not UPDATE_ALL: nothing observes the Backshop and neighbour
                    // updates across ten thousand blocks are pure cost.
                    if (!backshop.getBlockState(pos).is(WALL.getBlock())) {
                        backshop.setBlock(pos, WALL, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * Takes down the four side walls and the ceiling of the smaller room, so the bigger one's air
     * reaches them. The floor stays — it is the floor at every tier.
     *
     * <p>Only bedrock is cleared. Anything else standing where a wall was is a block a creative
     * player put there, and this is not the code that decides it should go.
     */
    private static void clearWalls(ServerLevel backshop, int region, int tier) {
        int side = RoomGeometry.footprint(tier);
        int top = RoomGeometry.HEIGHT + 1;
        BlockPos origin = RoomGeometry.origin(region);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                boolean edge = x == 0 || z == 0 || x == side - 1 || z == side - 1;
                for (int y = 1; y <= top; y++) {
                    if (!edge && y != top) {
                        continue;
                    }
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (backshop.getBlockState(pos).is(WALL.getBlock())) {
                        backshop.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** True when the shell is standing: the Exit block is the last thing {@link #ensure} writes. */
    public static boolean isBuilt(ServerLevel backshop, RoomRecord room) {
        return room.built()
            && backshop.getBlockState(RoomGeometry.exitPos(room.region())).is(WBBlocks.EXIT.get());
    }
}
