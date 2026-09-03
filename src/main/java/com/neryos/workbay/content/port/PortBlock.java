package com.neryos.workbay.content.port;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * One face of a bay's interior. SPEC.md §2.
 *
 * <p>It is what makes a bay a room rather than a hole: a machine with air neighbours has nothing to
 * push into, never sees redstone, and corrupts if it writes a bounding block. A Port gives the
 * machine something on every side.
 *
 * <p>Deliberately has <b>no block entity</b>. A Port's identity is its position — the bay column
 * layout in {@link com.neryos.workbay.world.BayGeometry} is pure arithmetic, so which Workbay and
 * which bay a Port belongs to is derivable and never needs storing, syncing or migrating.
 *
 * <p>Not craftable, not obtainable and not breakable. A bay with a missing wall is a state nothing
 * can recover from, so there is no way to reach it.
 */
public class PortBlock extends Block {
    public static final MapCodec<PortBlock> CODEC = simpleCodec(PortBlock::new);

    public PortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
