package com.neryos.workbay.content.room;

import com.neryos.workbay.menu.RoomDoorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A room's shell, and its way out. SPEC.md §8.
 *
 * <p><b>Not bedrock.</b> Bedrock was the placeholder that said "you cannot break this" and nothing
 * else; six faces of it around a room somebody lives in is a quarry, not a room. This says the
 * same thing, carries a colour while it says it, and is the door.
 *
 * <p><b>The whole shell is the door.</b> There was a single Exit block on the entry pad, and it was
 * wrong twice over: it could be lost, and in a 46-block room it had to be walked back to. Every
 * wall, floor and ceiling block opens the same screen, so "how do I get out" is answered by
 * right-clicking whatever you are standing next to. The {@code DOOR_*} parts draw a real 2×2 door
 * in the middle of each wall so that it is <em>discoverable</em> rather than merely true; it never
 * opens, has no hinge and no handle to press, because it is not a door — it is the picture of one
 * over the thing that already works everywhere.
 *
 * <p><b>It refuses to be destroyed.</b> -1 hardness stops a pick and not a creative click, and a
 * hole in a room's shell is a hole into the void with a player beside it.
 */
public class RoomWallBlock extends Block {

    public static final EnumProperty<RoomColour> COLOUR =
        EnumProperty.create("colour", RoomColour.class);

    /**
     * Which piece of the shell this is. Cosmetic only — <b>every</b> block of the shell opens the
     * same screen, and the door is where a player looks for one rather than where the only one is.
     */
    public static final EnumProperty<RoomPart> PART = EnumProperty.create("part", RoomPart.class);

    public RoomWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(COLOUR, RoomColour.DEFAULT)
            .setValue(PART, RoomPart.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOUR, PART);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            RoomDoorMenu.open(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
        boolean willHarvest, FluidState fluid) {
        return false;
    }
}
