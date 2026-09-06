package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.assay.AssayBlock;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

import java.util.Optional;

/**
 * The economy, end to end. SPEC.md §3.
 *
 * <p>The first test here is the one that matters: <b>one Workbay, from nothing to the first
 * Expansion Plate installed</b>, with no second Workbay crafted anywhere in it. That is the loop the
 * whole tier table hangs off, and until this session nothing in the mod produced a single Levy — so
 * the table was decoration and the ladder had no bottom rung.
 *
 * <p>It is also what pins {@link WorkbayRecord#BASE_BAYS} at two. Set it back to one and this test
 * goes red at the second rack, which is the honest shape of the deadlock: the Assay takes a bay and
 * has no faces, so the only bay left has nothing to skim from.
 */
@ForEachTest(groups = "assay")
public class AssayTests {

    private static WorkbayBlockEntity placeWorkbay(ExtendedGameTestHelper helper, BlockPos pos,
        GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        level.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
            new ItemStack(WBBlocks.WORKBAY.get()));
        // stillValid is a distance check, and a mock player standing across the structure fails it.
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return (WorkbayBlockEntity) level.getBlockEntity(pos);
    }

    private static WorkbayMenu menuFor(WorkbayBlockEntity workbay, GameTestPlayer player) {
        return new WorkbayMenu(1, player.getInventory(), workbay,
            WorkbayMenu.build(workbay, player, 0));
    }

    /** Racks whatever is named into the selected bay the way a player does: hold it, click the slot. */
    private static void rack(WorkbayMenu menu, GameTestPlayer player, int bay, ItemStack machine) {
        menu.act(WorkbayAction.SELECT_BAY, bay, Optional.empty());
        player.setItemInHand(InteractionHand.MAIN_HAND, machine);
        menu.act(WorkbayAction.RACK, 0, Optional.empty());
    }

    /**
     * Pairs a Connector to one bay, sticks it on the block below {@code at}, and hands back the link
     * that placing it created — switched on and hurried up, since a gametest is not a play session.
     */
    private static BusConfig connect(ExtendedGameTestHelper helper, WorkbayBlockEntity workbay,
        int bay, BlockPos at, GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
        WorkbayBlock.pair(connector, workbay.record().orElseThrow(),
            GlobalPos.of(level.dimension(), workbay.getBlockPos()), bay);

        BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
            .setValue(ConnectorBlock.FACING, Direction.DOWN);
        level.setBlock(at, state, Block.UPDATE_ALL);
        WBBlocks.CONNECTOR.get().setPlacedBy(level, at, state, player, connector);

        var links = workbay.buses();
        if (links.isEmpty()) {
            throw new GameTestAssertException("placing a paired Connector did not create a link");
        }
        BusConfig link = links.get(links.size() - 1).withEnabled(true).withRate(64).withSpeed(10);
        workbay.addBus(link);
        return link;
    }

    private static void fill(ServerLevel backshop, BlockPos machinePos, ItemStack stack, int stacks) {
        if (backshop.getBlockEntity(machinePos) instanceof Container container) {
            for (int slot = 0; slot < stacks; slot++) {
                container.setItem(slot, stack.copy());
            }
        }
    }

    private static int countIn(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return -1;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static void setSkim(WorkbayMenu menu, int percent) {
        for (int step = 0; step < percent / AssayBlock.rateStep(); step++) {
            menu.act(WorkbayAction.SET_SKIM, 0, Optional.empty(), Optional.empty(), false);
        }
    }

    /**
     * The whole economy in one test, and the reason the base Workbay grants two bays.
     *
     * <p>A fresh player: one Workbay, an Assay in bay 1, a chest of refined goods in bay 2, a
     * Connector on a chest in the world, the skim dial turned up — and enough Levy to install an
     * Expansion Plate. No second Workbay is crafted or placed anywhere in it, which is the point:
     * SPEC.md §14 allows only one deployed Workbay per network, so if this needed two, the ladder
     * would have no bottom rung at all.
     */
    @GameTest(timeoutTicks = 1200)
    @TestHolder(description = "One Workbay reaches the first Expansion Plate: Assay racked, goods skimmed, Levy banked and spent.")
    public static void theFirstExpansionPlateIsReachableFromOneWorkbay(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayMenu menu = menuFor(workbay, player);
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            int cost = WorkbayUpgrade.EXPANSION_PLATE.levyCost(0);

            helper.startSequence()
                // Nothing here force-loads the bay column. SPEC.md §12's mirroring is the mod's
                // job, and a harness that does it instead tests a world nobody plays in.
                .thenIdle(3)
                .thenExecute(() -> {
                    WorkbayRecord record = workbay.record().orElseThrow();
                    helper.assertValueEqual(record.bayCapacity(), 2,
                        "bays on a Workbay with no upgrades installed");

                    // Bay 1 holds the Assay. It has no block entity, so the host gate's no_machine
                    // heuristic would refuse it -- #workbay:host_allowed is what lets it through,
                    // and racking it here is what proves that tag actually ships.
                    rack(menu, player, 0, new ItemStack(WBBlocks.ASSAY.get()));
                    if (!backshop.getBlockState(BayGeometry.machinePos(record.bayColumn(), 0))
                        .is(WBBlocks.ASSAY.get())) {
                        throw new GameTestAssertException("the Assay was refused by the host gate; "
                            + "is workbay:assay still in #workbay:host_allowed?");
                    }

                    // Bay 2 holds the factory. This is the bay that does not exist if the base
                    // Workbay grants one, and without it nothing ever moves for the Assay to skim.
                    rack(menu, player, 1, new ItemStack(Blocks.CHEST));
                    fill(backshop, BayGeometry.machinePos(record.bayColumn(), 1),
                        new ItemStack(Items.IRON_INGOT, 64), 8);

                    connect(helper, workbay, 1, targetPos.above(), player);
                    setSkim(menu, AssayBlock.maxRate());
                    helper.assertValueEqual(workbay.record().orElseThrow().assay().rate(),
                        AssayBlock.maxRate(), "the skim rate after turning the dial up");
                })
                .thenWaitUntil(() -> {
                    int levy = workbay.record().orElseThrow().assay().levy();
                    if (levy < cost) {
                        throw new GameTestAssertException("the Assay has banked " + levy + " of "
                            + cost + " Levy so far");
                    }
                })
                .thenExecute(() -> {
                    // The goods really went missing, and they went missing on the way: less arrived
                    // than left. This is the whole reason the rate is drawn on the row.
                    int arrived = countIn(level, targetPos, Items.IRON_INGOT);
                    int leftBehind = countIn(backshop,
                        BayGeometry.machinePos(workbay.record().orElseThrow().bayColumn(), 1),
                        Items.IRON_INGOT);
                    if (arrived >= 8 * 64 - leftBehind) {
                        helper.fail("everything that left the bay arrived at the chest, so nothing "
                            + "was skimmed: " + arrived + " arrived, " + leftBehind + " left");
                    }

                    // And now the rung. The plate is materials; the Levy is the ladder.
                    player.getInventory().add(new ItemStack(WBItems.EXPANSION_PLATE.get()));
                    menu.act(WorkbayAction.INSTALL_UPGRADE,
                        WorkbayUpgrade.EXPANSION_PLATE.ordinal(), Optional.empty());

                    WorkbayRecord after = workbay.record().orElseThrow();
                    helper.assertValueEqual(after.upgrades().expansionPlates(), 1,
                        "Expansion Plates installed");
                    helper.assertValueEqual(after.bayCapacity(), 3, "bays after the first plate");
                    helper.assertValueEqual(after.assay().levy() < cost, true,
                        "the Levy balance having been spent");
                })
                .thenExecute(() -> level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenSucceed();
        });
    }

    /**
     * A rate with no Assay behind it must take nothing. Charging a tax with nothing to convert the
     * goods into is not a tax, it is items disappearing — and it is the one failure of this feature
     * that would look exactly like a duplication bug run backwards.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "The skim takes nothing while no Assay is racked, whatever the dial says.")
    public static void theSkimTakesNothingWithNoAssayRacked(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayMenu menu = menuFor(workbay, player);
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);

            helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    rack(menu, player, 0, new ItemStack(Blocks.CHEST));
                    fill(backshop, BayGeometry.machinePos(
                        workbay.record().orElseThrow().bayColumn(), 0),
                        new ItemStack(Items.IRON_INGOT, 64), 1);
                    connect(helper, workbay, 0, targetPos.above(), player);
                    setSkim(menu, AssayBlock.maxRate());
                })
                .thenWaitUntil(() -> {
                    int arrived = countIn(level, targetPos, Items.IRON_INGOT);
                    if (arrived < 64) {
                        throw new GameTestAssertException(arrived + " of 64 iron has arrived");
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(countIn(level, targetPos, Items.IRON_INGOT), 64,
                        "iron that arrived with the dial at maximum and no Assay racked");
                    helper.assertValueEqual(workbay.record().orElseThrow().assay().skimmed(), 0,
                        "goods skimmed with no Assay racked");
                })
                .thenExecute(() -> level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenSucceed();
        });
    }

    /**
     * <b>A host's edited number reaches the running game, and is read on the click rather than at
     * startup.</b> Every balance number in the mod is config now, and the failure mode this guards
     * is the one this project has already found four times: a value loaded once into a constant,
     * never consulted again, and a screen that goes on drawing the shipped default.
     *
     * <p>Two halves, because they are read in two different places and either could rot alone: the
     * skim ceiling, which the <em>server</em> clamps against inside {@code SET_SKIM}, and the
     * upgrade ladder, which the <em>screen</em> prices through {@link WorkbayUpgrade#levyCost}. The
     * assertions before the change are what stop it going vacuous — a test that only checks the
     * value after would pass against a mod that always answered 40.
     *
     * <p>Restored in a finally: a gametest that leaves the server's config edited hands the next
     * test in the run a world it never asked for.
     */
    @GameTest
    @TestHolder(description = "A host's edited config value changes what the mod does, live.")
    public static void aHostsEditedBalanceIsReadOnEveryClickNotAtStartup(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayMenu menu = menuFor(workbay, player);

            var config = com.neryos.workbay.config.WorkbayConfig.SERVER;
            int wasCeiling = config.maxSkimPercent.get();
            int wasCost = config.expansionPlateCost.get();
            try {
                // The shipped defaults first, so "it changed" cannot mean "it was always this".
                helper.assertValueEqual(wasCeiling, 25, "the shipped skim ceiling");
                helper.assertValueEqual(WorkbayUpgrade.EXPANSION_PLATE.levyCost(0), 2,
                    "the shipped cost of a first Expansion Plate");

                config.maxSkimPercent.set(40);
                config.expansionPlateCost.set(7);

                // Clicked far past the old ceiling: 25 would be the answer if SET_SKIM were still
                // clamping against a number read when the class loaded.
                setSkim(menu, 100);
                helper.assertValueEqual(workbay.record().orElseThrow().assay().rate(), 40,
                    "the skim rate after a host raised the ceiling to 40 and the dial was run up");
                helper.assertValueEqual(WorkbayUpgrade.EXPANSION_PLATE.levyCost(0), 7,
                    "the cost of a first Expansion Plate after a host set it to 7");
            } finally {
                config.maxSkimPercent.set(wasCeiling);
                config.expansionPlateCost.set(wasCost);
                level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            helper.succeed();
        });
    }

    /**
     * The tax is on refined goods, not on everything that moves. {@code #workbay:levy_input} is what
     * says which, and a pack extends it — so this is really a test that the tag is read at all
     * rather than being a decoration on a hardcoded list.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "The skim ignores goods that are not in #workbay:levy_input.")
    public static void theSkimIgnoresGoodsOutsideTheTag(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayMenu menu = menuFor(workbay, player);
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);

            helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // A positive control in the same rig: iron is in the tag, cobblestone is not.
                    if (!new ItemStack(Items.IRON_INGOT).is(AssayBlock.LEVY_INPUT)) {
                        throw new GameTestAssertException("iron ingots are not in "
                            + "#workbay:levy_input, so this test cannot tell the tag from nothing");
                    }
                    rack(menu, player, 0, new ItemStack(WBBlocks.ASSAY.get()));
                    rack(menu, player, 1, new ItemStack(Blocks.CHEST));
                    fill(backshop, BayGeometry.machinePos(
                        workbay.record().orElseThrow().bayColumn(), 1),
                        new ItemStack(Items.COBBLESTONE, 64), 1);
                    connect(helper, workbay, 1, targetPos.above(), player);
                    setSkim(menu, AssayBlock.maxRate());
                })
                .thenWaitUntil(() -> {
                    int arrived = countIn(level, targetPos, Items.COBBLESTONE);
                    if (arrived < 64) {
                        throw new GameTestAssertException(arrived + " of 64 cobblestone has arrived");
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(countIn(level, targetPos, Items.COBBLESTONE), 64,
                        "cobblestone that arrived with the dial at maximum");
                    helper.assertValueEqual(workbay.record().orElseThrow().assay().skimmed(), 0,
                        "goods skimmed from a link carrying nothing refined");
                })
                .thenExecute(() -> level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenSucceed();
        });
    }
}
