package com.neryos.workbay.gametests;

import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

/**
 * SPEC.md §14's network model. A Workbay belongs to the player who placed it, not to whatever
 * NBT the specific dropped item happened to carry — so losing the block is never the end of a
 * base.
 */
@ForEachTest(groups = "network")
public class NetworkTests {

    private static WorkbayBlockEntity place(ServerLevel level, BlockPos pos, GameTestPlayer player,
        ItemStack stack) {
        Block workbay = WBBlocks.WORKBAY.get();
        level.setBlock(pos, workbay.defaultBlockState(), Block.UPDATE_ALL);
        workbay.setPlacedBy(level, pos, level.getBlockState(pos), player, stack);
        return (WorkbayBlockEntity) level.getBlockEntity(pos);
    }

    /**
     * The whole point: break the block for real, place a completely uncrafted one, and the same
     * bays, upgrades and links come back — nothing written down, nothing typed in.
     */
    @GameTest
    @TestHolder(description = "A fresh, unbound Workbay reuses the placer's existing network.")
    public static void unboundWorkbayReusesThePlayersExistingNetwork(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));

            WorkbayBlockEntity workbay = place(level, first, player, new ItemStack(WBBlocks.WORKBAY.get()));
            var id = workbay.workbayId().orElseThrow();
            String code = workbay.record().orElseThrow().code();
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            registry.put(workbay.record().orElseThrow()
                .withBay(workbay.record().orElseThrow().bay(0)
                    .withHosted(java.util.Optional.of(net.minecraft.core.registries.BuiltInRegistries
                        .BLOCK.getKey(Blocks.CHEST)))));

            // Genuinely broken, not merely unloaded: destroyBlock is the real path a finished dig
            // takes, the same one workbayDropsWhenMinedByAPlayer exercises.
            player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            player.gameMode.destroyBlock(first);
            helper.assertValueEqual(registry.byId(id).orElseThrow().deployedCount(), 0,
                "deployed count after the only Workbay was broken");

            BlockPos second = helper.absolutePos(new BlockPos(3, 1, 3));
            WorkbayBlockEntity again = place(level, second, player,
                new ItemStack(WBBlocks.WORKBAY.get()));

            helper.assertValueEqual(again.workbayId().orElse(null), id,
                "id after placing an unbound Workbay for the same player");
            helper.assertValueEqual(again.record().orElseThrow().code(), code,
                "code after reusing the network");
            helper.assertValueEqual(again.record().orElseThrow().bay(0).hosted().isPresent(), true,
                "the bay's remembered contents after reusing the network");
            helper.succeed();
        });
    }

    /**
     * {@code maxDeployedWorkbaysPerNetwork} defaults to 1. A second unbound Workbay while the
     * first is still standing must be refused before it is ever placed, not placed and then
     * quietly two front doors into the same bays.
     */
    @GameTest
    @TestHolder(description = "A second unbound Workbay is refused while one is already deployed.")
    public static void secondUnboundWorkbayIsRefusedWhileOneIsDeployed(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));
            place(level, first, player, new ItemStack(WBBlocks.WORKBAY.get()));

            BlockPos second = helper.absolutePos(new BlockPos(3, 1, 1));
            level.setBlock(second.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack fresh = new ItemStack(WBBlocks.WORKBAY.get());
            UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND, fresh,
                new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second.below(), false));

            InteractionResult result = fresh.getItem().useOn(context);
            if (result.consumesAction() && level.getBlockState(second).is(WBBlocks.WORKBAY.get())) {
                helper.fail("a second Workbay was placed while the network's deployed cap was "
                    + "already reached");
                return;
            }
            if (level.getBlockState(second).is(WBBlocks.WORKBAY.get())) {
                helper.fail("the block went down at " + second + " despite the item's own "
                    + "useOn refusing the placement");
            }
            helper.succeed();
        });
    }
}
