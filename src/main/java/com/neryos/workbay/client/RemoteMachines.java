package com.neryos.workbay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The client's copy of a machine it was never sent, for as long as its screen is open.
 *
 * <p>A whole chunk rather than a block entity, because that is the question mods actually ask —
 * see {@link com.neryos.workbay.mixin.ClientChunkCacheMixin}. It holds exactly one block: the
 * machine's state, so {@code getBlockState} is right, and its block entity, so {@code
 * getBlockEntity} is right. Everything else in it is air, and nothing renders it.
 *
 * <p>One at a time. A player has one screen open.
 */
public final class RemoteMachines {
    private static ChunkPos where;
    private static LevelChunk chunk;

    private RemoteMachines() {}

    /** Null unless this is the chunk a remote screen is open on. Hot: called for every chunk miss. */
    public static LevelChunk chunkAt(int x, int z) {
        ChunkPos at = where;
        return at != null && at.x == x && at.z == z ? chunk : null;
    }

    public static void apply(BlockPos pos, BlockState state, CompoundTag data) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || data.isEmpty()) {
            clear();
            return;
        }

        LevelChunk held = chunk;
        BlockEntity open = held == null ? null : held.getBlockEntity(pos);
        if (open != null) {
            // Feed the machine the open screen is already holding, rather than replacing it: the
            // screen keeps a reference to that object, and a fresh one would leave it drawing a
            // copy nothing updates any more.
            //
            // loadWithComponents, not handleUpdateTag. The latter is a mod's own *sync* path and
            // is entitled to ignore most of a tag -- Mekanism's says so in as many words and calls
            // only BlockEntity's loadAdditional, so a side config fed through it never arrives.
            // This is the method a chunk load calls, which is what this copy stands in for. Public
            // by access transformer; there is no other door.
            open.loadWithComponents(data, level.registryAccess());
            return;
        }

        LevelChunk copy = new LevelChunk(level, new ChunkPos(pos));
        copy.setBlockState(pos, state, false);
        BlockEntity machine = BlockEntity.loadStatic(pos, state, data, level.registryAccess());
        if (machine != null) {
            // setBlockEntity refuses a position whose state has no block entity, which is why the
            // state goes in first rather than the two being set in either order.
            copy.setBlockEntity(machine);
        }
        chunk = copy;
        where = new ChunkPos(pos);
    }

    /** On close, and on disconnect, or the next screen inherits a machine from the last world. */
    public static void clear() {
        where = null;
        chunk = null;
    }
}
