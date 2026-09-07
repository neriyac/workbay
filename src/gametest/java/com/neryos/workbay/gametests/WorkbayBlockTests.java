package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.workbay.WorkbayBinding;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.BayHosting;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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

    /**
     * SPEC.md §14, and the question the config's own comment leaves open: what happens when a
     * network has <b>two</b> Workbay blocks and both are broken — which one does re-placing bring
     * back?
     *
     * <p>The answer is that there is nothing to choose between. A Workbay block is a handle, not a
     * container: the bays, the links, the upgrades and the lock all live on the
     * {@code WorkbayRecord}, {@code deployedCount} is a count and not an identity, and both dropped
     * items carry the same {@link WorkbayBinding}. Re-placing either one rebinds to the same record
     * and everything comes back. This test is what says so, because "obviously equivalent" is how a
     * player loses a factory.
     *
     * <p>Both are placed by one player on purpose, which is what makes them one network (§14). The
     * item path refuses that by default — {@code maxDeployedWorkbaysPerNetwork} is 1 — but
     * {@code setPlacedBy} is what a pack that raises it would run, so that is what is tested.
     * OPEN_ISSUES #40 is the other half: what is still wrong when a pack does raise it.
     */
    @GameTest
    @TestHolder(description = "Two Workbays on one network are two handles to it, and either one alone brings it back.")
    public static void bothWorkbaysBrokenAndEitherOnePlacedBackRestoresTheNetwork(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 3, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

            int before = registry.size();
            BlockPos left = place(helper, level, helper.absolutePos(new BlockPos(0, 1, 0)), player,
                new ItemStack(WBBlocks.WORKBAY.get()));
            BlockPos right = place(helper, level, helper.absolutePos(new BlockPos(4, 1, 4)), player,
                new ItemStack(WBBlocks.WORKBAY.get()));

            if (!(level.getBlockEntity(left) instanceof WorkbayBlockEntity one)
                || !(level.getBlockEntity(right) instanceof WorkbayBlockEntity two)) {
                helper.fail("placing two Workbays produced fewer than two block entities");
                return;
            }
            UUID id = one.workbayId().orElse(null);
            helper.assertNotNull(id, "a placed Workbay was never bound to a registry record");
            helper.assertValueEqual(two.workbayId().orElse(null), id,
                "the id of the second Workbay placed by the same player");
            helper.assertValueEqual(registry.size(), before + 1,
                "records after placing two Workbays for one player");
            helper.assertValueEqual(registry.byId(id).orElseThrow().deployedCount(), 2,
                "deployed blocks on the network");

            // Something on the record worth losing, so "it came back" means more than "an id
            // matched". A bay-to-bay link needs no Connector, so nothing here depends on a block
            // that a break could take with it.
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            var record = registry.byId(id).orElseThrow();
            BayHosting.rack(backshop, record.bayColumn(), 0, new ItemStack(Blocks.CHEST), player,
                Direction.NORTH);
            one.addBus(BusConfig.createInternal(UUID.randomUUID(), 0,
                GlobalPos.of(level.dimension(), left),
                GlobalPos.of(WorkbayDimensions.BACKSHOP,
                    BayGeometry.machinePos(record.bayColumn(), 1))));
            helper.assertValueEqual(registry.byId(id).orElseThrow().buses().size(), 1,
                "links on the network before either block is broken");

            // Broken the way the world breaks them, through the real loot table.
            ItemStack fromLeft = dropOf(helper, level, left, player);
            level.setBlock(left, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(registry.byId(id).orElseThrow().deployedCount(), 1,
                "deployed blocks after breaking the first of two");

            ItemStack fromRight = dropOf(helper, level, right, player);
            level.setBlock(right, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(registry.byId(id).orElseThrow().deployedCount(), 0,
                "deployed blocks after breaking both");

            WorkbayBinding leftBinding = fromLeft.get(WBDataComponents.BINDING.get());
            WorkbayBinding rightBinding = fromRight.get(WBDataComponents.BINDING.get());
            if (leftBinding == null || rightBinding == null) {
                helper.fail("a broken Workbay dropped an item with no binding, so its bays are "
                    + "unreachable forever");
                return;
            }
            helper.assertValueEqual(leftBinding.id(), rightBinding.id(),
                "the id both dropped Workbays carry -- if these differ there really is a choice "
                    + "to make about which one to place back, and nothing makes it");

            // The second one alone, and the whole network is back: same record, same bay, same link.
            BlockPos again = place(helper, level, helper.absolutePos(new BlockPos(2, 1, 2)), player,
                fromRight);
            if (!(level.getBlockEntity(again) instanceof WorkbayBlockEntity back)) {
                helper.fail("re-placing one of the two broken Workbays produced no block entity");
                return;
            }
            helper.assertValueEqual(back.workbayId().orElse(null), id, "id after re-placing one");
            helper.assertValueEqual(registry.size(), before + 1,
                "records after re-placing one of two -- a second record here means the other block "
                    + "would have minted a network of its own");
            helper.assertValueEqual(registry.byId(id).orElseThrow().deployedCount(), 1,
                "deployed blocks after re-placing one of two");
            helper.assertValueEqual(back.buses().size(), 1, "links after both were broken");
            // Read from the Backshop, not from the record: BayHosting.rack is what puts the block
            // in the bay, and the block standing there is SPEC.md §14's actual claim -- a hosted
            // machine keeps running while its Workbay is in somebody's pocket.
            helper.assertTrue(backshop.getBlockState(BayGeometry.machinePos(
                    registry.byId(id).orElseThrow().bayColumn(), 0)).is(Blocks.CHEST),
                "the machine in bay 1 is still in its bay after both Workbays were broken");

            BayHosting.eject(backshop, registry.byId(id).orElseThrow().bayColumn(), 0, null);
            level.setBlock(again, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }

    /** What breaking this block would actually put on the floor, through the real loot table. */
    private static ItemStack dropOf(ExtendedGameTestHelper helper, ServerLevel level, BlockPos pos,
        GameTestPlayer player) {
        List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos,
            level.getBlockEntity(pos), player, ItemStack.EMPTY);
        return drops.size() == 1 ? drops.get(0) : ItemStack.EMPTY;
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

    /**
     * Reported from play: mining a Workbay with a diamond pickaxe took a long time and then it
     * just vanished, no item. {@link #bindingSurvivesBreakAndPlace} did not catch this because it
     * breaks the block through {@code Block.getDrops} directly, which is the loot table's own
     * query path and skips the step that actually failed.
     *
     * <p>The real path is {@code ServerPlayerGameMode#destroyBlock}, and it only calls
     * {@code Block#playerDestroy} — which is what queries the loot table — when
     * {@code player.hasCorrectToolForDrops(state)} is true. That in turn is
     * {@code !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)}, checked
     * against the block's own {@code minecraft:mineable/*} tag membership. Both Workbay and
     * Connector copy their properties from {@code Blocks.IRON_BLOCK}, which sets
     * {@code requiresCorrectToolForDrops()} — but copying properties does not copy tag
     * membership, and neither block was ever added to {@code minecraft:mineable/pickaxe}. So no
     * tool was ever "correct", the correct-tool branch never ran, and the block was simply removed
     * with nothing dropped — silently, because {@code destroyBlock} returns {@code true} either
     * way and nothing here throws.
     */
    @GameTest
    @TestHolder(description = "A player mining a Workbay with a pickaxe gets the item back.")
    public static void workbayDropsWhenMinedByAPlayer(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            place(helper, level, pos, player, new ItemStack(WBBlocks.WORKBAY.get()));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));

            // The exact call ServerPlayerGameMode.destroyBlock makes when a player finishes
            // mining — not helper.destroyBlock, which is the creative-style instant removal and
            // would not have caught this either.
            player.gameMode.destroyBlock(pos);

            if (!level.getBlockState(pos).isAir()) {
                helper.fail("the Workbay was still there after destroyBlock");
                return;
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1.5));
            if (drops.stream().noneMatch(e -> e.getItem().is(WBBlocks.WORKBAY.get().asItem()))) {
                helper.fail("mining a Workbay with a diamond pickaxe dropped nothing. Check the "
                    + "minecraft:mineable/pickaxe block tag.");
                return;
            }
            helper.succeed();
        });
    }
}
