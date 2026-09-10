package com.neryos.workbay.content.connector;

import com.mojang.serialization.MapCodec;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.WorkbaySounds;
import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A link, as a block you can point at. SPEC.md §0 and §9.
 *
 * <p>The player pairs a Connector to a Workbay by right-clicking that Workbay with it, then sticks
 * it on any chest, tank or machine. Placing it is what creates the LINKS row; breaking it is what
 * removes it. That is the AE2 import-bus idiom with the cable removed, and it is what Flux Networks
 * and XNet both do — a link the player cannot see, find or break in the world is a link they cannot
 * debug either.
 *
 * <p><b>The block touches one face; the Workbay drives all six.</b> {@code FACING} points into the
 * target, and the target is simply {@code pos.relative(facing)}; which of the target's faces
 * actually answers is settled by {@link com.neryos.workbay.bus.BusEndpoint}, which tries all of
 * them. Nothing about the target's own orientation matters here.
 */
public class ConnectorBlock extends BaseEntityBlock {
    public static final MapCodec<ConnectorBlock> CODEC = simpleCodec(ConnectorBlock::new);

    /** Points <em>into</em> the block this Connector is stuck to, the way an observer's does. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /**
     * <b>One Connector, one link.</b> OPEN_ISSUES #77's model. It was a cap -- one resource on a
     * base Connector, every resource this install has once a Multichannel was in -- and the cap
     * was the thing that let one block hold four rows. The number is now one and it is not a
     * number: the invariant lives on {@link com.neryos.workbay.world.WorkbayRecord}, where every
     * path that writes a link has to go through it.
     *
     * <p><b>That was the Multichannel Upgrade's only reader.</b> Named in #77 rather than fixed
     * here: the upgrade is still craftable, priced and installable and now gates nothing.
     */
    public static final int LINKS_PER_CONNECTOR = 1;

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        // A 2px plate on the face it is stuck to. Thin enough to sit on a machine without hiding it.
        SHAPES[Direction.DOWN.ordinal()] = Block.box(4, 0, 4, 12, 2, 12);
        SHAPES[Direction.UP.ordinal()] = Block.box(4, 14, 4, 12, 16, 12);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(4, 4, 0, 12, 12, 2);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(4, 4, 14, 12, 12, 16);
        SHAPES[Direction.WEST.ordinal()] = Block.box(0, 4, 4, 2, 12, 12);
        SHAPES[Direction.EAST.ordinal()] = Block.box(14, 4, 4, 16, 12, 12);
    }

    public ConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WBBlockEntities.CONNECTOR.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The plate sits against the face named by FACING, which points away from this block.
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    /** Facing the block that was clicked, so the Connector ends up stuck to it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    /** Nothing to link to means nothing to be. Falls off if the target is mined out from under it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(target(state, pos)).isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
        net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == state.getValue(FACING) && !canSurvive(state, level, pos)
            ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
            : state;
    }

    public static BlockPos target(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING));
    }

    /**
     * Placing a paired Connector is what creates the link. An unpaired one still places — it is a
     * perfectly ordinary block that simply does nothing yet — and says so, because silently doing
     * nothing is how a player concludes the mod is broken.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector)) {
            return;
        }
        ConnectorPairing pairing = stack.get(WBDataComponents.PAIRING.get());
        if (pairing == null) {
            if (placer instanceof Player player) {
                WorkbaySounds.refuse(player, WorkbayLang.message("connector_unpaired"));
            }
            return;
        }
        connector.pairTo(pairing);
        // <b>The name comes off the item, and the item is named in an anvil.</b> OPEN_ISSUES #67
        // asks for a Connector to be named before it is placed, so that a link is named before it
        // exists -- and an item already has a name a player can set, drawn on the thing in their
        // hand, kept through a stack split and shown in the hotbar. A rename box on the item would
        // have been a second screen, a second packet and a second place for "what is this called"
        // to live; renaming an item is the game's own answer to the same question. Empty custom
        // name leaves the link deriving its name from its target, exactly as before.
        addLink(level, pos, state, connector,
            connector.pairing().map(ConnectorPairing::bay).orElse(0), BusConfig.Resource.ITEM,
            placer instanceof Player player ? player : null,
            nameOn(stack));
    }

    /**
     * Right-clicking a placed Connector opens its <b>rename panel</b>, and does nothing else.
     * OPEN_ISSUES #77.
     *
     * <p>It used to add a link -- the next resource this Connector did not carry, then the same
     * again on the next bay of the network -- which is how one block came to have four rows in a
     * list that is meant to have one row per Connector. The model is one object, one name, one
     * home bay: <b>a Connector is not a thing you feed bays with, it is a thing a bay picks up.</b>
     * Which bay it lands on is a question about a bay, and this block cannot see which bay the
     * player is standing in, so the answer never belonged in the world gesture at all -- it is the
     * Add list on the bay screen, which already moves a link from any bay to the one you are in.
     *
     * <p>What is left is the one thing a block <em>can</em> answer for itself: what it is called.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector)) {
            return InteractionResult.PASS;
        }
        if (connector.pairing().isEmpty()) {
            WorkbaySounds.refuse(player, WorkbayLang.message("connector_unpaired"));
            return InteractionResult.CONSUME;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.neryos.workbay.menu.ConnectorMenu.open(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Breaking the Connector removes its links. Done here rather than in
     * {@code BlockEntity#setRemoved}, which also fires on chunk unload — a Workbay whose Connectors
     * happen to be in an unloaded chunk must not quietly lose them.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector) {
            GlobalPos here = GlobalPos.of(level.dimension(), pos);
            connector.workbay().ifPresent(workbay -> workbay.removeLinksAt(here));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Whatever an anvil wrote on the stack, or "" for one that was never renamed. */
    private static String nameOn(ItemStack stack) {
        Component custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return custom == null ? "" : custom.getString().strip();
    }

    private static void addLink(Level level, BlockPos pos, BlockState state,
        ConnectorBlockEntity connector, int bay, BusConfig.Resource resource,
        @Nullable Player player, String name) {
        WorkbayBlockEntity workbay = connector.workbay().orElse(null);
        if (workbay == null) {
            if (player != null) {
                WorkbaySounds.refuse(player, WorkbayLang.message("connector_no_workbay"));
            }
            return;
        }
        ConnectorPairing pairing = connector.pairing().orElseThrow();
        BlockPos targetPos = target(state, pos);
        // Stamped here and nowhere else: this is the one moment the block at the far end is known
        // to be loaded, and from now on the screen can say "Chest" rather than two coordinates and
        // the flow map can draw the chest. See BusConfig#targetBlock.
        workbay.addBus(BusConfig.create(UUID.randomUUID(), bay, resource,
            BusConfig.Mode.INSERT,
            GlobalPos.of(level.dimension(), pos),
            GlobalPos.of(level.dimension(), targetPos))
            .withTargetBlock(java.util.Optional.ofNullable(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(targetPos).getBlock())))
            .withName(name));
        if (player != null) {
            WorkbaySounds.confirm(player, WorkbayLang.message("connector_linked",
                level.getBlockState(target(state, pos)).getBlock().getName(), bay + 1,
                pairing.code()),
                net.minecraft.sounds.SoundEvents.COPPER_BULB_TURN_ON, 1.0F);
        }
    }
}
