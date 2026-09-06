package com.neryos.workbay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of a machine it was never sent, for as long as its screen is open.
 *
 * <p>Read only by {@link com.neryos.workbay.mixin.LevelMixin}, and only when the real lookup found
 * nothing. Empty whenever no remote screen is open, which is almost always.
 */
public final class RemoteMachines {
    private static final Map<BlockPos, BlockEntity> SHADOWS = new ConcurrentHashMap<>();

    private RemoteMachines() {}

    public static BlockEntity shadow(BlockPos pos) {
        return SHADOWS.isEmpty() ? null : SHADOWS.get(pos);
    }

    public static void apply(BlockPos pos, BlockState state, CompoundTag data) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (data.isEmpty()) {
            SHADOWS.remove(pos);
            return;
        }
        BlockEntity copy = BlockEntity.loadStatic(pos, state, data, level.registryAccess());
        if (copy != null) {
            copy.setLevel(level);
            SHADOWS.put(pos, copy);
        }
    }

    /** On disconnect, or the next screen inherits a machine from the last world. */
    public static void clear() {
        SHADOWS.clear();
    }
}
