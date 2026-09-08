package com.neryos.workbay.content.room;

import com.neryos.workbay.world.RoomVisit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The way out of a room. SPEC.md §2 and §8.
 *
 * <p>One per room, on the entry pad at the region origin, so no Room Frame upgrade ever moves it:
 * a player told to walk back to the corner finds it in the corner at every tier.
 *
 * <p><b>It reads the return position off the player, not off the Workbay.</b> That is the whole
 * design: it works with no Workbay standing in the world, which is the state Compact Machines'
 * known failure — "lost the device, trapped inside" — leaves a player in. Never an item, never
 * breakable, and generated rather than placed.
 */
public class ExitBlock extends Block {
    public ExitBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            RoomVisit.leave(server);
        }
        return InteractionResult.CONSUME;
    }
}
