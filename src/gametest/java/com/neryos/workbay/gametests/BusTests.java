package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.BayHosting;
import com.neryos.workbay.world.FaceConfig;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import com.neryos.workbay.world.WorkbayTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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

/**
 * The mod's central promise, end to end: a machine standing in another dimension, reached from a
 * block in this one, with no cable in between. SPEC.md §9.
 *
 * <p>Every link here is made the way a player makes one — a Connector paired to the Workbay and
 * stuck on the target block. There is no other way to make one, which is the point: if the
 * Connector path breaks, none of these pass.
 */
@ForEachTest(groups = "bus")
public class BusTests {

    /** Places a Workbay, gives it a bay with a chest in it, and returns the block entity. */
    private static WorkbayBlockEntity setUp(ExtendedGameTestHelper helper, BlockPos workbayPos,
        GameTestPlayer player, ItemStack inTheBay) {
        ServerLevel level = helper.getLevel();
        level.setBlock(workbayPos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, workbayPos,
            level.getBlockState(workbayPos), player, new ItemStack(WBBlocks.WORKBAY.get()));

        WorkbayBlockEntity workbay = (WorkbayBlockEntity) level.getBlockEntity(workbayPos);
        WorkbayRecord record = workbay.record().orElseThrow();

        ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
        WorkbayTickets.force(backshop, record.id(), record.bayColumn());
        BayHosting.rack(backshop, record.bayColumn(), 0, inTheBay, player, Direction.NORTH);
        return workbay;
    }

    /**
     * Pairs a Connector to the Workbay, sticks it on the block below {@code at}, and returns the
     * link that placing it created. Mirrors the player's two actions exactly: right-click the
     * Workbay with the Connector, then place it against the thing you want linked.
     */
    private static BusConfig connect(ExtendedGameTestHelper helper, WorkbayBlockEntity workbay,
        BlockPos at, Direction facing, GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
        WorkbayBlock.pair(connector, workbay.record().orElseThrow(),
            GlobalPos.of(level.dimension(), workbay.getBlockPos()), 0);

        BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
            .setValue(ConnectorBlock.FACING, facing);
        level.setBlock(at, state, Block.UPDATE_ALL);
        WBBlocks.CONNECTOR.get().setPlacedBy(level, at, state, player, connector);

        var links = workbay.buses();
        if (links.isEmpty()) {
            helper.fail("placing a paired Connector did not create a link");
            throw new IllegalStateException("no link");
        }
        return links.get(links.size() - 1);
    }

    private static void tearDown(ExtendedGameTestHelper helper, BlockPos workbayPos) {
        ServerLevel level = helper.getLevel();
        if (level.getBlockEntity(workbayPos) instanceof WorkbayBlockEntity workbay) {
            workbay.record().ifPresent(record -> {
                ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
                BayHosting.eject(backshop, record.bayColumn(), 0, null);
                WorkbayTickets.release(backshop, record.id(), record.bayColumn());
            });
        }
        level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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

    /**
     * The whole thing in one test. A chest in a bay in the Backshop, a chest on the floor in the
     * overworld, and a bus that moves iron from one to the other across a dimension boundary with
     * nothing physical connecting them.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "An insert bus moves items out of a hosted machine into a chest in another dimension.")
    public static void insertBusMovesItemsAcrossDimensions(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));

            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (!(backshop.getBlockEntity(machinePos) instanceof Container hosted)) {
                helper.fail("the bay does not hold a container after racking a chest");
                return;
            }
            hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(8).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(level, targetPos, Items.IRON_INGOT) < 64) {
                        throw new GameTestAssertException("the bus has moved "
                            + countIn(level, targetPos, Items.IRON_INGOT) + " of 64 iron so far");
                    }
                })
                .thenExecute(() -> {
                    // Everything has to arrive, and nothing may be created on the way.
                    helper.assertValueEqual(countIn(level, targetPos, Items.IRON_INGOT), 64,
                        "iron in the target chest");
                    int left = 0;
                    for (int slot = 0; slot < hosted.getContainerSize(); slot++) {
                        left += hosted.getItem(slot).getCount();
                    }
                    helper.assertValueEqual(left, 0, "items left in the hosted chest");
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "An extract bus pulls items out of a chest into a hosted machine.")
    public static void extractBusPullsItemsIntoAHostedMachine(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(targetPos) instanceof Container source) {
                source.setItem(0, new ItemStack(Items.GOLD_INGOT, 32));
            }
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.BARREL));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withMode(BusConfig.Mode.EXTRACT).withRate(16).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(backshop, machinePos, Items.GOLD_INGOT) < 32) {
                        throw new GameTestAssertException("the bus has pulled "
                            + countIn(backshop, machinePos, Items.GOLD_INGOT) + " of 32 gold so far");
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(countIn(backshop, machinePos, Items.GOLD_INGOT), 32,
                        "gold in the hosted barrel");
                    helper.assertValueEqual(countIn(level, targetPos, Items.GOLD_INGOT), 0,
                        "gold left in the source chest");
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Rate and speed are the two numbers a player sets, so they have to mean exactly what the screen
     * says. A bus that quietly moves more than its rate is a bus nobody can plan around.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A bus never moves more than its rate in one operation.")
    public static void busObeysItsRate(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }

            // The slowest legal speed, so the test can look between operations.
            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(4).withSpeed(200));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(level, targetPos, Items.IRON_INGOT) <= 0) {
                        throw new GameTestAssertException("the bus has not moved anything yet");
                    }
                })
                .thenExecute(() -> {
                    int moved = countIn(level, targetPos, Items.IRON_INGOT);
                    if (moved > 4) {
                        helper.fail("the first operation moved " + moved + " items with a rate of 4");
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Reported from play, not from reading: a chest hosted in a bay with one face set to <b>in</b>
     * refused to send anything and the row said nothing was wrong, while another row said
     * <code>No port</code> without saying which end had none.
     *
     * <p>Both halves are asserted here. A link whose direction of travel the face config forbids
     * must not report {@code IDLE} — idle means "working, nothing to do right now", and a link that
     * can never work is not idle. And a link blocked at the machine end must not report a status
     * whose name blames the target.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "A face config that forbids a link's direction is reported, not silently idle.")
    public static void aFaceConfigThatBlocksALinkIsReported(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }

            // Exactly what was set in play: one face, in, for items. Nothing marked out.
            RoomRegistry.get(level.getServer()).put(record.withBay(
                record.bay(0).withFaces(FaceConfig.NONE.cycled(BusConfig.Resource.ITEM, Direction.WEST))));
            workbay.forgetBay(0);

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(8).withSpeed(10));
            java.util.UUID id = link.id();

            helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    BusRunner.BusStatus status = workbay.busStatus(id);
                    if (status == BusRunner.BusStatus.IDLE) {
                        helper.fail("a link the face config forbids reported IDLE, so the player is "
                            + "told nothing is wrong while it can never move anything");
                    }
                    if (!status.isProblem()) {
                        helper.fail("a link that can never work reported " + status
                            + ", which the screen does not count as a problem");
                    }
                    helper.assertValueEqual(countIn(level, targetPos, Items.IRON_INGOT), 0,
                        "items that reached the target");
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * The other half of the same report: with one face set to <b>in</b>, pulling into the hosted
     * chest is exactly what the player configured, so it has to work.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "An in face lets an extract link fill the hosted machine through it.")
    public static void anInputFaceLetsAnExtractLinkThrough(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(targetPos) instanceof Container source) {
                source.setItem(0, new ItemStack(Items.GOLD_INGOT, 32));
            }
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            RoomRegistry.get(level.getServer()).put(record.withBay(
                record.bay(0).withFaces(FaceConfig.NONE.cycled(BusConfig.Resource.ITEM, Direction.WEST))));
            workbay.forgetBay(0);

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withMode(BusConfig.Mode.EXTRACT).withRate(16).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(backshop, machinePos, Items.GOLD_INGOT) < 32) {
                        throw new GameTestAssertException("the bus has pulled "
                            + countIn(backshop, machinePos, Items.GOLD_INGOT) + " of 32 gold in "
                            + "through the one face marked in; status is "
                            + workbay.busStatus(link.id()));
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * The case actually reported: <b>both</b> directions marked on the cube — one face in, the
     * opposite face out — two links on the same bay, and nothing moved. Both links are live at
     * once here, which is the part a single-link test cannot see: the machine end is one
     * {@link com.neryos.workbay.bus.BusEndpoint} shared per bay, with one bound face, and the two
     * links want different ones.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "Two links on one bay, one in face and one out face, both move.")
    public static void anInAndAnOutFaceOnOneBayBothWork(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(7, 5, 7));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos sendTo = helper.absolutePos(new BlockPos(6, 1, 6));
            BlockPos pullFrom = helper.absolutePos(new BlockPos(6, 1, 0));

            level.setBlock(sendTo, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pullFrom, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pullFrom) instanceof Container source) {
                source.setItem(0, new ItemStack(Items.GOLD_INGOT, 32));
            }

            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }

            // West in, east out. Exactly what was set in play.
            RoomRegistry.get(level.getServer()).put(record.withBay(record.bay(0).withFaces(
                FaceConfig.NONE
                    .cycled(BusConfig.Resource.ITEM, Direction.WEST)
                    .cycled(BusConfig.Resource.ITEM, Direction.EAST)
                    .cycled(BusConfig.Resource.ITEM, Direction.EAST))));
            workbay.forgetBay(0);

            BusConfig send = connect(helper, workbay, sendTo.above(), Direction.DOWN, player);
            workbay.addBus(send.withRate(8).withSpeed(10));
            BusConfig pull = connect(helper, workbay, pullFrom.above(), Direction.DOWN, player);
            workbay.addBus(pull.withMode(BusConfig.Mode.EXTRACT).withRate(8).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    int sent = countIn(level, sendTo, Items.IRON_INGOT);
                    int pulled = countIn(backshop, machinePos, Items.GOLD_INGOT);
                    if (sent < 64 || pulled < 32) {
                        throw new GameTestAssertException("sent " + sent + "/64 iron ("
                            + workbay.busStatus(send.id()) + "), pulled " + pulled + "/32 gold ("
                            + workbay.busStatus(pull.id()) + ")");
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * The row's ghost slot, doing its job. SPEC.md §5: one item per link today, nine to thirty-six
     * once the filter items exist, and they widen at exactly the predicate this asserts.
     *
     * <p>The hosted chest holds two items and the link is filtered to one, so a filter that is read
     * but not applied — or applied to the destination rather than the source — cannot pass.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A filtered link moves its item and leaves everything else behind.")
    public static void aFilteredLinkMovesOnlyItsItem(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.GOLD_INGOT, 16));
                hosted.setItem(1, new ItemStack(Items.IRON_INGOT, 16));
            }

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(8).withSpeed(10).withFilter(
                java.util.Optional.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(Items.IRON_INGOT))));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(level, targetPos, Items.IRON_INGOT) < 16) {
                        throw new GameTestAssertException("the filtered bus has moved "
                            + countIn(level, targetPos, Items.IRON_INGOT) + " of 16 iron so far");
                    }
                })
                // Long enough after the iron arrived that unfiltered gold would have followed it.
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(countIn(level, targetPos, Items.GOLD_INGOT), 0,
                        "gold that reached the target past an iron-only filter");
                    helper.assertValueEqual(countIn(backshop, machinePos, Items.GOLD_INGOT), 16,
                        "gold left in the hosted chest");
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }
}
