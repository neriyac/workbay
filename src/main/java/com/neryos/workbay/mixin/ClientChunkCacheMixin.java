package com.neryos.workbay.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.neryos.workbay.client.RemoteMachines;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a screen find the machine it was opened for. Client only.
 *
 * <p><b>The chunk, not the block entity.</b> Patching {@code Level#getBlockEntity} was measured and
 * was not enough: Mekanism — and every mod using the same near-universal helper — asks whether the
 * chunk is loaded <em>first</em>, and gives up before any lookup happens. The disconnect it throws
 * says so in as many words: "Client could not locate tile at BlockPos(x=8, y=10, z=8) for tile
 * container." A hosted machine's chunk is in a dimension that client was never sent, so the answer
 * is no, whatever the block-entity call would have returned.
 *
 * <p>So the chunk is what is answered. One synthetic {@link LevelChunk} holding one block state and
 * one block entity, and every guard downstream — {@code hasChunkAt}, {@code getChunkAt},
 * {@code getBlockState}, {@code getBlockEntity} — is satisfied by the ordinary path instead of
 * being patched one at a time.
 *
 * <p>A real chunk always wins: only vanilla's {@code null} and its shared empty chunk are replaced.
 * The cost of that is a collision nobody will hit but which is worth writing down — a player
 * standing next to the same chunk coordinates in their own dimension gets the real chunk, and the
 * remote screen refuses to open until they walk away.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @ModifyReturnValue(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
        + "Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("RETURN"))
    private LevelChunk workbay$remoteMachineChunk(LevelChunk original, int x, int z,
        ChunkStatus status, boolean load) {
        if (original != null && !(original instanceof EmptyLevelChunk)) {
            return original;
        }
        LevelChunk remote = RemoteMachines.chunkAt(x, z);
        return remote != null ? remote : original;
    }
}
