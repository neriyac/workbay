package com.neryos.workbay.content.workbay;

import com.mojang.serialization.MapCodec;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The mod. A cabinet holding bays in the Backshop, reachable by bus. SPEC.md §2, §7 and §14.
 *
 * <p>{@code state} carries the whole at-a-glance signal (SPEC.md §7): a player should be able to see
 * that something is wrong from across the room without opening anything.
 */
public class WorkbayBlock extends BaseEntityBlock {
    public static final MapCodec<WorkbayBlock> CODEC = simpleCodec(WorkbayBlock::new);

    public static final EnumProperty<WorkbayState> STATE = EnumProperty.create("state", WorkbayState.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public WorkbayBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
            .setValue(STATE, WorkbayState.IDLE)
            .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, STATE, POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WBBlockEntities.WORKBAY.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Binding happens here, not in the block entity's constructor, because it is the only point that
     * knows both who placed the block and what the item they placed remembered.
     *
     * <p>A stack with no binding mints a fresh Workbay and tells the player its code once, in chat,
     * because that code is the only way back to their machines if the block is ever destroyed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)
            || !(level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay)) {
            return;
        }

        RoomRegistry registry = RoomRegistry.get(server.getServer());
        WorkbayBinding binding = stack.get(WBDataComponents.BINDING.get());

        if (binding != null && registry.byId(binding.id()).isPresent()) {
            workbay.bindTo(binding.id());
            workbay.rememberPosition();
            return;
        }

        WorkbayRecord record = registry.create(
            placer instanceof Player player ? player.getUUID() : java.util.UUID.randomUUID(),
            placer instanceof Player player ? player.getGameProfile().getName() : "unknown",
            server.getRandom());
        workbay.bindTo(record.id());
        workbay.rememberPosition();

        if (placer instanceof Player player) {
            player.sendSystemMessage(WorkbayLang.message("room_created",
                Component_aqua(record.code())));
        }
    }

    private static net.minecraft.network.chat.Component Component_aqua(String text) {
        return net.minecraft.network.chat.Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    /**
     * SPEC.md §14: the machines keep running, so say so before the player walks away thinking they
     * lost them. Sent on the break rather than before it, because there is no "before" for a block
     * that breaks in one hit.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay) {
            workbay.record().ifPresent(record -> {
                if (!record.bays().isEmpty()) {
                    player.sendSystemMessage(WorkbayLang.message("break_warning",
                        Component_aqua(record.code())));
                }
            });
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * SPEC.md §14: pick-block returns an <em>unbound</em> Workbay. Handing back a bound one would
     * make middle-click duplicate a factory.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(this);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads bay occupancy — the same thing the block's lit state says (SPEC.md §7). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay ? workbay.occupiedBays() : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING,
            rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0; // glass front: the interior is meant to be visible
    }
}
