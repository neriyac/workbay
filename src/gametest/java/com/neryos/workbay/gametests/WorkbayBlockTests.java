package com.neryos.workbay.gametests;

import com.neryos.workbay.content.workbay.WorkbayBinding;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.List;
import java.util.UUID;

/**
 * SPEC.md §14: a Workbay can be broken while its machines keep running, so the dropped item is the
 * only thread back to them. If that thread breaks, a player loses a factory and there is no way to
 * tell them apart from someone who never had one.
 */
@ForEachTest(groups = "workbay_block")
public class WorkbayBlockTests {

    private static BlockPos place(ExtendedGameTestHelper helper, ServerLevel level, BlockPos pos,
        GameTestPlayer player, ItemStack stack) {
        Block workbay = WBBlocks.WORKBAY.get();
        BlockState state = workbay.defaultBlockState();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        workbay.setPlacedBy(level, pos, state, player, stack);
        return pos;
    }

    @GameTest
    @TestHolder(description = "Placing a Workbay mints one registry record, and breaking it hands that record back on the item.")
    public static void bindingSurvivesBreakAndPlace(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

            int before = registry.size();
            BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0));
            place(helper, level, first, player, new ItemStack(WBBlocks.WORKBAY.get()));

            if (!(level.getBlockEntity(first) instanceof WorkbayBlockEntity placed)) {
                helper.fail("placing a Workbay produced no WorkbayBlockEntity");
                return;
            }
            UUID id = placed.workbayId().orElse(null);
            helper.assertNotNull(id, "a placed Workbay was never bound to a registry record");
            helper.assertValueEqual(registry.size(), before + 1, "records after placing one Workbay");

            String code = registry.byId(id).orElseThrow().code();

            // Break it the way the world does: through the real loot table, so copy_components is
            // what has to carry the binding rather than anything this test does by hand.
            List<ItemStack> drops = Block.getDrops(level.getBlockState(first), level, first, placed,
                player, ItemStack.EMPTY);
            if (drops.size() != 1) {
                helper.fail("breaking a Workbay dropped " + drops.size() + " stacks, expected 1: " + drops);
                return;
            }
            ItemStack dropped = drops.get(0);
            WorkbayBinding binding = dropped.get(WBDataComponents.BINDING.get());
            if (binding == null) {
                helper.fail("the dropped Workbay carries no binding, so its bays are unreachable "
                    + "forever. Check copy_components in the loot table and collectImplicitComponents.");
                return;
            }
            helper.assertValueEqual(binding.id(), id, "id on the dropped item");
            helper.assertValueEqual(binding.code(), code, "code on the dropped item");

            level.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            // Place it somewhere else. It must rebind, not mint a second Workbay.
            BlockPos second = helper.absolutePos(new BlockPos(2, 1, 2));
            place(helper, level, second, player, dropped);
            if (!(level.getBlockEntity(second) instanceof WorkbayBlockEntity replaced)) {
                helper.fail("re-placing a Workbay produced no WorkbayBlockEntity");
                return;
            }
            helper.assertValueEqual(replaced.workbayId().orElse(null), id, "id after re-placing");
            helper.assertValueEqual(registry.size(), before + 1,
                "records after re-placing the same Workbay");

            level.setBlock(second, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }

    /**
     * SPEC.md §14. Middle-click must not duplicate a factory, so pick-block hands back a plain
     * unbound Workbay, and the id is stripped from {@code block_entity_data} so a creative
     * ctrl-pick cannot clone it either.
     */
    @GameTest
    @TestHolder(description = "Pick-block on a bound Workbay returns an unbound one.")
    public static void pickBlockReturnsAnUnboundWorkbay(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            place(helper, level, pos, player, new ItemStack(WBBlocks.WORKBAY.get()));

            if (!(level.getBlockEntity(pos) instanceof WorkbayBlockEntity workbay)) {
                helper.fail("placing a Workbay produced no WorkbayBlockEntity");
                return;
            }
            helper.assertNotNull(workbay.workbayId().orElse(null), "the Workbay was never bound");

            ItemStack picked = WBBlocks.WORKBAY.get().getCloneItemStack(level.getBlockState(pos),
                null, level, pos, player);
            if (picked.get(WBDataComponents.BINDING.get()) != null) {
                helper.fail("pick-block returned a Workbay still bound to a live one, so middle-click "
                    + "duplicates a factory");
                return;
            }

            // Ctrl-pick in creative copies the block entity through saveToItem. The id must not
            // survive that either, which is what removeComponentsFromTag is for.
            ItemStack cloned = new ItemStack(WBBlocks.WORKBAY.get());
            workbay.saveToItem(cloned, level.registryAccess());
            var beData = cloned.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (beData != null && beData.copyTag().contains("WorkbayId")) {
                helper.fail("a creative ctrl-pick would clone the Workbay id through "
                    + "block_entity_data: " + beData.copyTag());
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder(description = "A comparator on a Workbay reads how many of its bays are occupied.")
    public static void comparatorReadsBayOccupancy(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            place(helper, level, pos, player, new ItemStack(WBBlocks.WORKBAY.get()));

            BlockState state = level.getBlockState(pos);
            if (!state.hasAnalogOutputSignal()) {
                helper.fail("a Workbay reports no comparator output at all");
                return;
            }
            helper.assertValueEqual(state.getAnalogOutputSignal(level, pos), 0,
                "comparator output with no bays occupied");

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }
}
