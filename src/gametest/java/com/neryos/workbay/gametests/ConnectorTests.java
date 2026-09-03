package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
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

/**
 * The Connector is the only way a link comes into existence, so these cover the whole life of one:
 * pairing, placing, the base one-type cap, what Multichannel buys, and what breaking it takes away.
 *
 * <p>SPEC.md §0 rejected a configuration-only link precisely so this life cycle exists — a link the
 * player cannot see, find or break in the world is a link they cannot debug either.
 */
@ForEachTest(groups = "connector")
public class ConnectorTests {

    private static WorkbayBlockEntity placeWorkbay(ExtendedGameTestHelper helper, BlockPos pos,
        GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        level.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
            new ItemStack(WBBlocks.WORKBAY.get()));
        return (WorkbayBlockEntity) level.getBlockEntity(pos);
    }

    private static ItemStack paired(ServerLevel level, WorkbayBlockEntity workbay) {
        ItemStack stack = new ItemStack(WBBlocks.CONNECTOR.get());
        WorkbayBlock.pair(stack, workbay.record().orElseThrow(),
            GlobalPos.of(level.dimension(), workbay.getBlockPos()), 0);
        return stack;
    }

    private static void place(ServerLevel level, BlockPos at, Direction facing, ItemStack stack,
        GameTestPlayer player) {
        BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
            .setValue(ConnectorBlock.FACING, facing);
        level.setBlock(at, state, Block.UPDATE_ALL);
        WBBlocks.CONNECTOR.get().setPlacedBy(level, at, state, player, stack);
    }

    /** Right-clicking the placed Connector with an empty hand, exactly as a player would. */
    private static void poke(ServerLevel level, BlockPos at, GameTestPlayer player) {
        level.getBlockState(at).useWithoutItem(level, player,
            new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    /**
     * The player's own sequence, in order: right-click the Workbay with a Connector, place the
     * Connector on a chest, and the LINKS list has a row pointing at that chest.
     */
    @GameTest
    @TestHolder(description = "Pairing a Connector and placing it creates a link on the Workbay.")
    public static void placingAPairedConnectorMakesALink(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);

            // Pairing is what the right-click on the Workbay does, and it has to reach the item.
            ItemStack stack = paired(level, workbay);
            if (stack.get(WBDataComponents.PAIRING.get()) == null) {
                helper.fail("pairing a Connector left no pairing component on the item");
                return;
            }
            helper.assertValueEqual(workbay.buses().size(), 0, "links before the Connector is placed");

            place(level, chestPos.above(), Direction.DOWN, stack, player);

            helper.assertValueEqual(workbay.buses().size(), 1, "links after placing the Connector");
            BusConfig link = workbay.buses().get(0);
            if (!link.target().pos().equals(chestPos)) {
                helper.fail("the link points at " + link.target().pos() + ", not at the chest it "
                    + "was placed against (" + chestPos + ")");
                return;
            }
            helper.assertValueEqual(link.connector().pos(), chestPos.above(), "the link's Connector");
            helper.assertValueEqual(link.resource(), BusConfig.Resource.ITEM, "the link's resource");
            helper.succeed();
        });
    }

    /**
     * Base behaviour is one resource type per Connector, and Multichannel is what buys the other
     * two. If the cap stops being enforced, the upgrade stops meaning anything.
     */
    @GameTest
    @TestHolder(description = "A Connector carries one resource type, or all three with Multichannel.")
    public static void multichannelRaisesTheOneTypeCap(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 1, 4));
            BlockPos connectorPos = chestPos.above();

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            place(level, connectorPos, Direction.DOWN, paired(level, workbay), player);

            // Base: asking for a second type does nothing at all.
            poke(level, connectorPos, player);
            helper.assertValueEqual(workbay.buses().size(), 1,
                "links on a base Connector after asking for a second type");

            // With Multichannel it carries all three, and no more.
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            WorkbayRecord record = workbay.record().orElseThrow();
            registry.put(record.withUpgrades(record.upgrades()
                .plus(com.neryos.workbay.content.workbay.WorkbayUpgrade.MULTICHANNEL)));

            for (int attempt = 0; attempt < 4; attempt++) {
                poke(level, connectorPos, player);
            }
            helper.assertValueEqual(workbay.buses().size(), 3,
                "links on a Multichannel Connector after four more attempts");
            helper.assertValueEqual(
                workbay.buses().stream().map(BusConfig::resource).distinct().count(), 3L,
                "distinct resource types on one Multichannel Connector");
            helper.succeed();
        });
    }

    /**
     * The block <em>is</em> the link. Breaking it has to take the link with it, or a player who
     * tidies up their base is left with rows pointing at nothing and no way to clear them.
     */
    @GameTest
    @TestHolder(description = "Breaking a Connector removes the links it anchored.")
    public static void breakingAConnectorRemovesItsLinks(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 1, 4));
            BlockPos connectorPos = chestPos.above();

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            place(level, connectorPos, Direction.DOWN, paired(level, workbay), player);
            helper.assertValueEqual(workbay.buses().size(), 1, "links after placing the Connector");

            level.setBlock(connectorPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(workbay.buses().size(), 0, "links after breaking the Connector");
            helper.succeed();
        });
    }

    /**
     * An unpaired Connector is an ordinary block that does nothing. It must not attach itself to
     * whichever Workbay happens to be nearby — a link nobody asked for is worse than no link.
     */
    @GameTest
    @TestHolder(description = "An unpaired Connector makes no link.")
    public static void anUnpairedConnectorMakesNoLink(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            place(level, chestPos.above(), Direction.DOWN, new ItemStack(WBBlocks.CONNECTOR.get()), player);

            helper.assertValueEqual(workbay.buses().size(), 0, "links from an unpaired Connector");
            helper.succeed();
        });
    }

    /**
     * The Connector's half of {@link WorkbayBlockTests#workbayDropsWhenMinedByAPlayer}: the same
     * missing {@code minecraft:mineable/pickaxe} tag would silently swallow a Connector too, and a
     * player who mines one back up expects it back in hand like any other block.
     */
    @GameTest
    @TestHolder(description = "A player mining a Connector with a pickaxe gets the item back.")
    public static void connectorDropsWhenMinedByAPlayer(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos chestPos = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos connectorPos = chestPos.above();

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.DOWN);
            level.setBlock(connectorPos, state, Block.UPDATE_ALL);
            WBBlocks.CONNECTOR.get().setPlacedBy(level, connectorPos, state, player,
                new ItemStack(WBBlocks.CONNECTOR.get()));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));

            player.gameMode.destroyBlock(connectorPos);

            if (!level.getBlockState(connectorPos).isAir()) {
                helper.fail("the Connector was still there after destroyBlock");
                return;
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(connectorPos).inflate(1.5));
            if (drops.stream().noneMatch(e -> e.getItem().is(WBBlocks.CONNECTOR.get().asItem()))) {
                helper.fail("mining a Connector with a diamond pickaxe dropped nothing. Check the "
                    + "minecraft:mineable/pickaxe block tag.");
                return;
            }
            helper.succeed();
        });
    }
}
