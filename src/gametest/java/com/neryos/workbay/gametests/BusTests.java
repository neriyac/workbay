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
import com.neryos.workbay.world.RedstoneMode;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.world.WorkbayRecord;
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
import java.util.Optional;
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
        // Deliberately NOT force-loaded here. SPEC.md §12's mirroring is the mod's job, and while
        // this line was in the harness it did that job on the mod's behalf - so every test passed
        // against a bay column no real world ever loads.
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
        // A placed Connector makes a link that is switched off, so nothing moves before the player
        // has looked at the row. These tests are about what moves once it is on, so they turn it on
        // the way a player does. aNewLinkStartsSwitchedOff asserts the default itself.
        return links.get(links.size() - 1).withEnabled(true);
    }

    private static void tearDown(ExtendedGameTestHelper helper, BlockPos workbayPos) {
        ServerLevel level = helper.getLevel();
        if (level.getBlockEntity(workbayPos) instanceof WorkbayBlockEntity workbay) {
            workbay.record().ifPresent(record -> {
                ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
                BayHosting.eject(backshop, record.bayColumn(), 0, null);
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

    /**
     * The mod's whole promise, with nothing staged: place a Workbay, rack a machine, point a link
     * at a chest, and it works — <b>without the test loading the Backshop chunk for itself.</b>
     *
     * <p>Reported from play. Every link on a freshly placed Workbay said the machine was
     * unreachable, because SPEC.md §12's mirroring was specified, marked built, and never written:
     * nothing in the mod ever registered a ticket, so the bay column was simply never loaded. The
     * gametests could not see it, because their own setup force-loaded the column first.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A hosted machine is reachable without anyone force-loading its chunk.")
    public static void aFreshlyPlacedWorkbayLoadsItsOwnBayColumn(final DynamicTest test) {
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
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
            }

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(8).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(level, targetPos, Items.IRON_INGOT) < 8) {
                        throw new GameTestAssertException("nothing reached the chest; the link says "
                            + workbay.busStatus(link.id()) + " and the bay column is "
                            + (backshop.isLoaded(machinePos) ? "loaded" : "NOT loaded"));
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * The four standard redstone modes, SPEC.md §4. Asserted as a pair per mode — held while the
     * signal is wrong, moving when it is right — because a gate that never opens and a gate that
     * never closes both pass a one-sided test.
     */
    @GameTest(timeoutTicks = 1200)
    @TestHolder(description = "A bay set to run with a signal waits for one, then runs.")
    public static void withASignalHoldsUntilThereIsOne(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            BlockPos leverPos = workbayPos.above();

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
            }

            RoomRegistry.get(level.getServer()).put(record.withBay(
                record.bay(0).withRedstone(RedstoneMode.WITH_SIGNAL)));

            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(8).withSpeed(10));

            helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    // Half of the test: with no signal it must not have moved anything at all.
                    helper.assertValueEqual(countIn(level, targetPos, Items.IRON_INGOT), 0,
                        "items moved by a bay waiting for a signal it has not got");
                    helper.assertValueEqual(workbay.busStatus(link.id()),
                        BusRunner.BusStatus.HELD_BY_REDSTONE, "the held link's status");
                })
                // A redstone block on top is the shortest way to a real neighbour signal.
                .thenExecute(() -> level.setBlock(leverPos,
                    Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> {
                    if (countIn(level, targetPos, Items.IRON_INGOT) < 16) {
                        throw new GameTestAssertException("powered, the bus has moved "
                            + countIn(level, targetPos, Items.IRON_INGOT) + " of 16; status is "
                            + workbay.busStatus(link.id()));
                    }
                })
                .thenExecute(() -> level.setBlock(leverPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Pulse is the one mode with state, so it is the one that can leak: an edge that is never spent
     * turns pulse into always, and an edge spent twice turns one click into two loads.
     */
    @GameTest(timeoutTicks = 1200)
    @TestHolder(description = "Pulse moves one load per rising edge and then stops again.")
    public static void pulseMovesOncePerRisingEdge(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            BlockPos leverPos = workbayPos.above();

            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }

            RoomRegistry.get(level.getServer()).put(record.withBay(
                record.bay(0).withRedstone(RedstoneMode.PULSE)));

            // Rate 4, so one operation is unmistakably four items and not "some".
            BusConfig link = connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            workbay.addBus(link.withRate(4).withSpeed(10));

            helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(
                    countIn(level, targetPos, Items.IRON_INGOT), 0,
                    "items moved before any edge"))
                .thenExecute(() -> level.setBlock(leverPos,
                    Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(
                    countIn(level, targetPos, Items.IRON_INGOT), 4,
                    "items moved by one rising edge at a rate of 4"))
                // Still held high. A level, not an edge, so nothing more may move.
                .thenIdle(80)
                .thenExecute(() -> helper.assertValueEqual(
                    countIn(level, targetPos, Items.IRON_INGOT), 4,
                    "items moved while the signal simply stayed on"))
                .thenExecute(() -> level.setBlock(leverPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenIdle(20)
                .thenExecute(() -> level.setBlock(leverPos,
                    Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(
                    countIn(level, targetPos, Items.IRON_INGOT), 8,
                    "items moved after a second rising edge"))
                .thenExecute(() -> level.setBlock(leverPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Placing a Connector used to start a link running immediately, which emptied a chest into the
     * wrong machine before the row had been read. A link is made switched off now.
     */
    @GameTest
    @TestHolder(description = "A link made by placing a Connector starts switched off.")
    public static void aNewLinkStartsSwitchedOff(final DynamicTest test) {
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
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
            }

            connect(helper, workbay, targetPos.above(), Direction.DOWN, player);
            BusConfig asCreated = workbay.buses().get(0);
            if (asCreated.enabled()) {
                helper.fail("a link made by placing a Connector was already running");
                return;
            }

            helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> helper.assertValueEqual(
                    countIn(level, targetPos, Items.IRON_INGOT), 0,
                    "items moved by a link nobody switched on"))
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Bay to bay, no Connector at all — SPEC.md §0's "no physical anchor" rejection was about links
     * that leave the Workbay, and this one never does. Two bays in the same column, an internal
     * link from one to the other, and it has to move items exactly like an external one — including
     * never reporting CONNECTOR_GONE, which is the trap an internal link falls into if the "am I
     * still anchored" check ever runs for it: its connector field is the Workbay's own position, and
     * that position is never going to hold a Connector block.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A bay-to-bay link moves items with no Connector anywhere.")
    public static void aBayToBayLinkMovesItemsWithNoConnector(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));

            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            WorkbayRecord record = workbay.record().orElseThrow();
            // A second bay to link to. Racking directly, the way setUp racks bay 0, rather than
            // going through an Expansion Plate item this test does not need to own.
            registry.put(record.withUpgrades(new WorkbayRecord.Upgrades(1, 0, 0, 0, 0, 0)));
            record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BayHosting.rack(backshop, record.bayColumn(), 1, new ItemStack(Blocks.CHEST), player,
                Direction.NORTH);

            BlockPos bay0 = BayGeometry.machinePos(record.bayColumn(), 0);
            BlockPos bay1 = BayGeometry.machinePos(record.bayColumn(), 1);
            if (backshop.getBlockEntity(bay0) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }

            // stillValid is a distance check; setUp never moves the mock player near the
            // Workbay because nothing in this file went through the menu before this test.
            player.moveTo(workbayPos.getX() + 0.5, workbayPos.getY(), workbayPos.getZ() + 0.5);
            WorkbayMenu menu = new WorkbayMenu(1, player.getInventory(), workbay,
                WorkbayMenu.build(workbay, player, 0));
            menu.act(WorkbayAction.SELECT_BAY, 0, Optional.empty());
            menu.act(WorkbayAction.CREATE_INTERNAL_LINK, 0, Optional.empty());

            BusConfig created = workbay.buses().stream().filter(BusConfig::internal).findFirst()
                .orElse(null);
            if (created == null) {
                helper.fail("CREATE_INTERNAL_LINK made no internal link");
                return;
            }
            helper.assertValueEqual(created.target().pos(), bay1, "the internal link's target bay");
            menu.act(WorkbayAction.LINK_TOGGLE_ENABLED, 0, Optional.of(created.id()));
            workbay.addBus(workbay.bus(created.id()).orElseThrow().withRate(8).withSpeed(10));

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(backshop, bay1, Items.IRON_INGOT) < 64) {
                        throw new GameTestAssertException("the internal link has moved "
                            + countIn(backshop, bay1, Items.IRON_INGOT) + " of 64 iron so far; "
                            + "status is " + workbay.busStatus(created.id()));
                    }
                })
                .thenExecute(() -> {
                    if (workbay.busStatus(created.id()) == BusRunner.BusStatus.CONNECTOR_GONE) {
                        helper.fail("an internal link reported CONNECTOR_GONE — it has no "
                            + "Connector to be gone");
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }

    /**
     * Handing a link to another bay, which is what the Add picker does. The assertion that matters
     * is the second one: the runner caches a resolved capability per link, and that cache is keyed
     * on the link but built from the bay it had at the time. Without dropping it on the edit, the
     * link keeps pulling out of the bay it used to belong to and every screen in the mod says it
     * belongs to the new one.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "Reassigning a link to another bay moves where it actually pulls from.")
    public static void assigningALinkToAnotherBayMovesWhereItPullsFrom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(3, 1, 0));
            helper.setBlock(new BlockPos(3, 1, 0), Blocks.CHEST);

            WorkbayBlockEntity workbay = setUp(helper, workbayPos, player, new ItemStack(Blocks.CHEST));
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            WorkbayRecord record = workbay.record().orElseThrow();
            registry.put(record.withUpgrades(new WorkbayRecord.Upgrades(1, 0, 0, 0, 0, 0)));
            record = workbay.record().orElseThrow();

            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BayHosting.rack(backshop, record.bayColumn(), 1, new ItemStack(Blocks.CHEST), player,
                Direction.NORTH);
            BlockPos bay0 = BayGeometry.machinePos(record.bayColumn(), 0);
            BlockPos bay1 = BayGeometry.machinePos(record.bayColumn(), 1);
            // Only bay 1 has anything. A link on bay 0 must move nothing until it is reassigned,
            // and everything after.
            if (backshop.getBlockEntity(bay1) instanceof Container hosted) {
                hosted.setItem(0, new ItemStack(Items.GOLD_INGOT, 32));
            }

            BusConfig link = connect(helper, workbay, chestPos.above(), Direction.DOWN, player);
            workbay.addBus(workbay.bus(link.id()).orElseThrow()
                .withMode(BusConfig.Mode.INSERT).withEnabled(true).withRate(8).withSpeed(10));

            player.moveTo(workbayPos.getX() + 0.5, workbayPos.getY(), workbayPos.getZ() + 0.5);
            WorkbayMenu menu = new WorkbayMenu(1, player.getInventory(), workbay,
                WorkbayMenu.build(workbay, player, 0));
            menu.act(WorkbayAction.LINK_ASSIGN_BAY, 1, Optional.of(link.id()));

            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().bay(), 1,
                "the reassigned link's bay");

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countIn(level, chestPos, Items.GOLD_INGOT) < 32) {
                        throw new GameTestAssertException("the reassigned link has moved "
                            + countIn(level, chestPos, Items.GOLD_INGOT) + " of 32 gold out of "
                            + "bay 2; status is " + workbay.busStatus(link.id()));
                    }
                })
                .thenExecute(() -> {
                    if (backshop.getBlockEntity(bay0) instanceof Container old
                        && !old.isEmpty()) {
                        helper.fail("the reassigned link touched bay 1, which it no longer "
                            + "belongs to");
                    }
                })
                .thenExecute(() -> tearDown(helper, workbayPos))
                .thenSucceed();
        });
    }
}
