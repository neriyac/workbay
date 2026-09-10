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

import java.util.List;
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
     * Base cap: one resource type per Connector. The Multichannel upgrade raises it to every
     * resource that <em>exists here</em> — three without Mekanism, four with it, because a fourth
     * slot for a chemical link that can never bind is a slot that reads as broken. Not a constant
     * for the same reason: whether chemicals exist is a property of the install.
     */
    public static final int BASE_LINKS = 1;

    public static int multichannelLinks() {
        return (int) java.util.Arrays.stream(BusConfig.Resource.values())
            .filter(BusConfig.Resource::available).count();
    }

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
        addLink(level, pos, state, connector, BusConfig.Resource.ITEM,
            placer instanceof Player player ? player : null,
            nameOn(stack));
    }

    /**
     * Right-clicking a placed Connector with an empty hand adds the next resource type it does not
     * already carry. Base is one type per Connector; Multichannel is what buys the other two, and
     * the refusal names it rather than doing nothing.
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
        BusConfig.Resource next = nextFreeResource(level, pos, connector);
        if (next == null) {
            WorkbaySounds.refuse(player, WorkbayLang.message("connector_full"));
            return InteractionResult.CONSUME;
        }
        // The second and third resources on one Connector take the name the first one was given,
        // because they are the same Connector and the player named the Connector, not the row.
        String named = workbayNameAt(level, pos, connector);
        addLink(level, pos, state, connector, next, player, named);
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

    @Nullable
    private static BusConfig.Resource nextFreeResource(Level level, BlockPos pos, ConnectorBlockEntity connector) {
        WorkbayBlockEntity workbay = connector.workbay().orElse(null);
        if (workbay == null) {
            return null;
        }
        List<BusConfig> here = workbay.linksAt(GlobalPos.of(level.dimension(), pos));
        int cap = workbay.record().map(r -> r.upgrades().multichannel() > 0
            ? multichannelLinks() : BASE_LINKS).orElse(BASE_LINKS);
        if (here.size() >= cap) {
            return null;
        }
        for (BusConfig.Resource resource : BusConfig.Resource.values()) {
            if (resource.available()
                && here.stream().noneMatch(link -> link.resource() == resource)) {
                return resource;
            }
        }
        return null;
    }

    /** Whatever an anvil wrote on the stack, or "" for one that was never renamed. */
    private static String nameOn(ItemStack stack) {
        Component custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return custom == null ? "" : custom.getString().strip();
    }

    /** The name the links already on this Connector carry, so a second resource matches the first. */
    private static String workbayNameAt(Level level, BlockPos pos, ConnectorBlockEntity connector) {
        return connector.workbay()
            .map(workbay -> workbay.linksAt(GlobalPos.of(level.dimension(), pos)).stream()
                .map(BusConfig::name)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElse(""))
            .orElse("");
    }

    private static void addLink(Level level, BlockPos pos, BlockState state,
        ConnectorBlockEntity connector, BusConfig.Resource resource, @Nullable Player player,
        String name) {
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
        workbay.addBus(BusConfig.create(UUID.randomUUID(), pairing.bay(), resource,
            BusConfig.Mode.INSERT,
            GlobalPos.of(level.dimension(), pos),
            GlobalPos.of(level.dimension(), targetPos))
            .withTargetBlock(java.util.Optional.ofNullable(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(targetPos).getBlock())))
            .withName(name));
        if (player != null) {
            WorkbaySounds.confirm(player, WorkbayLang.message("connector_linked",
                level.getBlockState(target(state, pos)).getBlock().getName(), pairing.code()),
                net.minecraft.sounds.SoundEvents.COPPER_BULB_TURN_ON, 1.0F);
        }
    }
}
