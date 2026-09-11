package com.neryos.workbay.world;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Racking a machine into a bay and getting it back out again. SPEC.md §10.
 *
 * <p>Both orders are requirements, not suggestions. Every step exists because of a specific way of
 * losing somebody's factory silently, and the two that are easiest to talk yourself out of are the
 * ones that cost the most:
 *
 * <ul>
 * <li><b>Insert steps 2 and 3.</b> Skipping them leaves a Mekanism machine with no containers at
 *     all — it answers on none of its six faces, so a bay reports "no ports" for a machine that is
 *     perfectly fine. Measured, and pinned by {@code shortInsertOrderLeavesTheMachineUnreachable}.
 * <li><b>Extract step 3.</b> Removing the block entity <em>before</em> the setBlock. Otherwise
 *     {@code Block#onRemove} runs {@code Containers.dropContents} and spills the machine's entire
 *     inventory into a sealed room in a dimension nobody can reach.
 * </ul>
 */
public final class BayHosting {
    private BayHosting() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** The properties a machine might orient itself by, in the order a block is likely to use them. */
    private static final DirectionProperty[] FACINGS = {
        BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING
    };

    /**
     * Puts a machine in a bay. SPEC.md §10's insert order, in order.
     *
     * @param placer the real player doing the inserting, never null — every Mekanism machine records
     *               an owner UUID in {@code setPlacedBy}, and an owner-less machine is GUI-locked.
     * @return true if the machine is now standing in the bay
     */
    public static boolean rack(ServerLevel backshop, ChunkPos column, int bay, ItemStack stack,
        ServerPlayer placer, Direction facing) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        BlockPos pos = BayBuilder.ensure(backshop, column, bay);

        BlockState state = orient(block.defaultBlockState(), facing);

        // 1. The block itself.
        backshop.setBlock(pos, state, Block.UPDATE_ALL);

        // Steps 2-4 are one guarded block: a foreign loader or setPlacedBy throwing is a real
        // outcome, and a throw between the setBlock and the caller's hand shrink would leave the
        // machine standing in the bay AND the item in the hand (night 2026-09-11, 1B #5b). Undo the
        // placement rather than leave a half-placed machine.
        try {
            // 2 and 3. What the item was carrying. minecraft:block_entity_data excludes x, y, z,
            // components and keepPacked, so the BlockState is not carried by the item and the
            // facing above is ours to choose - which is why it is stored per bay rather than
            // inferred.
            BlockItem.updateCustomBlockEntityTag(backshop, placer, pos, stack);
            BlockEntity placed = backshop.getBlockEntity(pos);
            if (placed != null) {
                placed.applyComponentsFromItemStack(stack);
            }

            // 4. setBlock alone never calls this, and skipping it leaves machines that record an
            // owner owner-less and GUI-locked, and leaves bounding blocks unplaced.
            block.setPlacedBy(backshop, pos, state, placer, stack);
        } catch (Exception e) {
            LOG.error("placing {} in a bay threw; refusing to host it",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block), e);
            backshop.removeBlockEntity(pos);
            backshop.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            backshop.invalidateCapabilities(pos);
            return false;
        }

        // 5. The bay's ports and any bus pointed here have to re-resolve against the new machine.
        backshop.invalidateCapabilities(pos);
        return true;
    }

    /**
     * Takes the machine back out, with everything inside it. SPEC.md §10's extract order.
     *
     * @return the machine as an item, or an empty stack if the bay was empty
     */
    public static ItemStack eject(ServerLevel backshop, ChunkPos column, int bay, @Nullable ServerPlayer player) {
        BlockPos pos = BayGeometry.machinePos(column, bay);
        BlockState state = backshop.getBlockState(pos);
        if (state.isAir()) {
            return ItemStack.EMPTY;
        }

        // 1. getCloneItemStack, not Block#getDrops: getDrops is loot-table driven and hands back the
        // machine and its contents as separate stacks, which in a sealed room means the contents
        // are simply gone.
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack stack = state.getCloneItemStack(hit, backshop, pos, player);
        if (stack.isEmpty()) {
            stack = new ItemStack(state.getBlock());
        }

        // 2. Everything the machine was holding, onto the item.
        BlockEntity be = backshop.getBlockEntity(pos);
        if (be != null) {
            be.saveToItem(stack, backshop.registryAccess());
        }

        // 3. Before the setBlock. Block#onRemove runs Containers.dropContents, and a chest's worth
        // of somebody's ore would spill into a room nobody can reach.
        backshop.removeBlockEntity(pos);

        // 4 and 5.
        backshop.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        backshop.invalidateCapabilities(pos);
        return stack;
    }

    private static BlockState orient(BlockState state, Direction facing) {
        for (DirectionProperty property : FACINGS) {
            if (state.hasProperty(property) && property.getPossibleValues().contains(facing)) {
                return state.setValue(property, facing);
            }
        }
        return state;
    }
}
