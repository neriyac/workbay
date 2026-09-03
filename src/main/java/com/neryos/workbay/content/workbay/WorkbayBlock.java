package com.neryos.workbay.content.workbay;

import com.mojang.serialization.MapCodec;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.content.connector.ConnectorPairing;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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

import java.util.UUID;

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

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
        Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, WBBlockEntities.WORKBAY.get(), WorkbayBlockEntity::serverTick);
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
     * <p>A stack with no binding — freshly crafted, or one that lost its component — does not
     * always mint a new network any more (SPEC.md §14). A player already owns at most
     * {@code maxNetworksPerPlayer}; placing an unbound Workbay reuses the first of those instead,
     * so losing the physical block is never the end of a base. {@link WorkbayItem} is what refuses
     * the placement outright when the target network is already at
     * {@code maxDeployedWorkbaysPerNetwork} — by the time this runs the block already exists, too
     * late to say no.
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
        WorkbayRecord existing = binding != null ? registry.byId(binding.id()).orElse(null) : null;

        if (existing != null) {
            registry.put(existing.withDeployedCount(existing.deployedCount() + 1));
            workbay.bindTo(existing.id());
            workbay.rememberPosition();
            return;
        }

        UUID ownerId = placer instanceof Player player ? player.getUUID() : UUID.randomUUID();
        String ownerName = placer instanceof Player player ? player.getGameProfile().getName() : "unknown";
        WorkbayRecord reused = registry.ownedBy(ownerId).stream().findFirst().orElse(null);

        WorkbayRecord record = reused != null ? reused
            : registry.create(ownerId, ownerName, server.getRandom());
        registry.put(record.withDeployedCount(record.deployedCount() + 1));
        workbay.bindTo(record.id());
        workbay.rememberPosition();

        if (placer instanceof Player player) {
            // No code in the message. SPEC.md §14: the network is found by owner, so a code is
            // something for a player to write down, mistype and ask about, and nothing else.
            player.sendSystemMessage(WorkbayLang.message(
                reused != null ? "network_reused" : "room_created"));
        }
    }

    /**
     * The other half of the deployed count {@link #setPlacedBy} increments. On {@code BlockEntity}
     * removal, not here: that fires on chunk unload too, and a Workbay whose chunk merely unloaded
     * has not been given up — the player would come back to find their "lost" network silently
     * handed to whoever placed next.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay) {
            workbay.workbayId().ifPresent(id -> {
                if (level instanceof ServerLevel server) {
                    RoomRegistry registry = RoomRegistry.get(server.getServer());
                    registry.byId(id).ifPresent(record ->
                        registry.put(record.withDeployedCount(record.deployedCount() - 1)));
                }
            });
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Right-clicking a Workbay with a Connector pairs that Connector to it. Pairing before placing
     * is what lets {@code setPlacedBy} create the link in one step — a Connector that had to be
     * paired after placement would sit in the world doing nothing, which reads as a broken mod.
     *
     * <p>The bay is the Workbay's first free one, so the common case — one machine, one Connector —
     * needs no screen at all. Screen 1's `+ Pair` button pairs to the selected bay instead.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
        Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
        net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(WBBlocks.CONNECTOR.get().asItem())) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay)) {
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }
        WorkbayRecord record = workbay.record().orElse(null);
        if (record == null) {
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }
        pair(stack, record, GlobalPos.of(level.dimension(), pos), firstOccupiedBay(record));
        player.displayClientMessage(WorkbayLang.message("connector_paired"), true);
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    /**
     * An empty hand opens the screens. The whole snapshot rides the menu-open buffer, so the screen
     * draws the real state on its first frame rather than flashing defaults (SPEC.md §4).
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level,
        BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay)
            || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (workbay.record().isEmpty()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, viewer) -> new com.neryos.workbay.menu.WorkbayMenu(id, inventory, workbay,
                com.neryos.workbay.menu.WorkbayMenu.build(workbay, serverPlayer, 0)),
            WorkbayLang.gui("title")),
            buffer -> com.neryos.workbay.menu.WorkbaySnapshot.STREAM_CODEC.encode(buffer,
                com.neryos.workbay.menu.WorkbayMenu.build(workbay, serverPlayer, 0)));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** Stamps a Connector item with the Workbay and bay its link will land on. */
    public static void pair(ItemStack stack, WorkbayRecord record, GlobalPos workbayPos, int bay) {
        stack.set(WBDataComponents.PAIRING.get(),
            new ConnectorPairing(record.id(), workbayPos, record.code(), bay));
    }

    private static int firstOccupiedBay(WorkbayRecord record) {
        return record.bays().stream().filter(bay -> bay.hosted().isPresent())
            .mapToInt(WorkbayRecord.Bay::index).min().orElse(0);
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
                    player.sendSystemMessage(WorkbayLang.message("break_warning"));
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
