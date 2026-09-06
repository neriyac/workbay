package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.menu.WorkbaySnapshot;
import com.neryos.workbay.world.BayGeometry;
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
 * What the screens actually do, tested where the work happens. SPEC.md §4.
 *
 * <p>None of this touches a client. Every button on the bays and upgrades screens turns into one
 * {@link WorkbayAction} on {@link WorkbayMenu}, so driving the menu directly tests the same code
 * path a click takes, minus the pixels.
 */
@ForEachTest(groups = "menu")
public class MenuTests {

    /**
     * Bay View groups a machine's slots by what the player can actually do with each, and it reads
     * that rather than assuming it: a simulated insert either works or it does not. A vanilla
     * furnace is the one machine where the answer is known independently — input, fuel, output, in
     * that order — so it is the one that can prove the reading is real.
     *
     * <p>Mekanism machines answer OUT to every probe, because their null side refuses every insert.
     * That is not a bug in this and it is why a machine with no IN slot is drawn as one plain grid:
     * a grouping where everything lands in one group says nothing.
     */
    /**
     * Where a short machine's slots sit. Four slots pinned to the left of a nine-wide panel read as
     * a grid that ran out rather than as the whole machine, so an ungrouped row is centred — and
     * <b>in whole slots</b>, because these are the coordinates real {@code Slot}s are built at on
     * both sides, and half a slot of indent misaligns every column with the player's own grid
     * underneath it. A grid that fills the row is not moved at all.
     *
     * <p>Arithmetic rather than a screenshot on purpose: this is the one part of the change the
     * server also computes, and it is what a click lands on.
     */
    @GameTest
    @TestHolder(description = "A machine with fewer slots than the panel is wide is centred in it.")
    public static void aShortMachineGridIsCentredInWholeSlots(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            var out = com.neryos.workbay.menu.BayViewMenu.SlotRole.OUT;
            var four = com.neryos.workbay.menu.BayViewMenu.MachineLayout
                .of(java.util.List.of(out, out, out, out));
            helper.assertTrue(!four.grouped(), "four output slots are one plain row, not two groups");
            int indent = four.xs()[0] - com.neryos.workbay.menu.BayViewMenu.GRID_X;
            helper.assertValueEqual(indent, 36, "the indent of a four-slot row in a nine-wide panel");
            helper.assertValueEqual(indent % 18, 0, "the indent, in whole slots");

            var nine = com.neryos.workbay.menu.BayViewMenu.MachineLayout
                .of(java.util.Collections.nCopies(9, out));
            helper.assertValueEqual(nine.xs()[0], com.neryos.workbay.menu.BayViewMenu.GRID_X,
                "the first slot of a row that already fills the panel");
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder(description = "Bay View reads a furnace's slots as input, fuel and output.")
    public static void bayViewReadsAFurnacesSlotRoles(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, helper.absolutePos(new BlockPos(0, 1, 0)), player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.FURNACE));
            WorkbayMenu menu = menuFor(workbay, player);
            menu.act(WorkbayAction.SELECT_BAY, 0, java.util.Optional.empty());
            menu.act(WorkbayAction.RACK, 0, java.util.Optional.empty());

            var opening = com.neryos.workbay.menu.BayViewMenu.opening(player, record, 0);
            if (opening == null) {
                helper.fail("Bay View would not open on a racked furnace");
                return;
            }
            helper.assertValueEqual(opening.roles(), java.util.List.of(
                com.neryos.workbay.menu.BayViewMenu.SlotRole.IN,
                com.neryos.workbay.menu.BayViewMenu.SlotRole.FUEL,
                com.neryos.workbay.menu.BayViewMenu.SlotRole.OUT),
                "the roles Bay View read off a furnace");
            helper.assertTrue(com.neryos.workbay.menu.BayViewMenu.MachineLayout
                    .of(opening.roles()).grouped(),
                "a furnace has an input slot, so its slots should be grouped rather than in a row");

            // The other half of the reading, and the one a fixed probe list cannot do: a slot that
            // already holds something is asked whether it would take that back. An output slot will
            // not take back its own contents; an input slot will. This is what keeps a *filtered*
            // input from reading as an output on a machine whose slots only accept one recipe.
            ServerLevel back = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            BlockPos machine = BayGeometry.machinePos(record.bayColumn(), 0);
            if (back.getBlockEntity(machine) instanceof Container furnace) {
                furnace.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
                furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 1));
            }
            var filled = com.neryos.workbay.menu.BayViewMenu.opening(player, record, 0);
            helper.assertValueEqual(filled.roles().get(0),
                com.neryos.workbay.menu.BayViewMenu.SlotRole.IN,
                "a full input slot, which will take back what it holds");
            helper.assertValueEqual(filled.roles().get(2),
                com.neryos.workbay.menu.BayViewMenu.SlotRole.OUT,
                "a full output slot, which will not take back even its own contents");
            helper.succeed();
        });
    }

    /**
     * The honest limit, asserted so nobody rediscovers it as a bug. Mekanism's null-side proxy
     * refuses every simulated insert, so every slot reads OUT and the screen falls back to a plain
     * row — and it must, because there is nothing there to group on.
     *
     * <p>{@code isItemValid} does <b>not</b> rescue it. That proxy returns the real slot validity
     * when read-only, which sounds like the missing signal and is not: an output slot can hold the
     * item too, so it answers yes for every slot and turns "all OUT" into "all IN" — a grouping that
     * is wrong rather than absent. Tried on this machine, reverted, and written down here.
     *
     * <p>Nothing is writable either, and that is the half that matters: saying otherwise is what
     * drew a ghost item on the client.
     */
    @GameTest
    @TestHolder(description = "A Mekanism machine falls back to a plain grid, and nothing is writable.")
    public static void aMekanismMachineFallsBackToAPlainGrid(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, helper.absolutePos(new BlockPos(0, 1, 0)), player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            Block machine = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("mekanism:enrichment_chamber"));
            if (machine == Blocks.AIR) {
                helper.fail("mekanism:enrichment_chamber is not registered. This test is about a "
                    + "real mod's handler, so a missing partner mod is a failure, never a skip.");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(machine));
            WorkbayMenu menu = menuFor(workbay, player);
            menu.act(WorkbayAction.SELECT_BAY, 0, java.util.Optional.empty());
            menu.act(WorkbayAction.RACK, 0, java.util.Optional.empty());

            var opening = com.neryos.workbay.menu.BayViewMenu.opening(player, record, 0);
            if (opening == null) {
                helper.fail("Bay View would not open on a racked Enrichment Chamber");
                return;
            }
            helper.assertFalse(com.neryos.workbay.menu.BayViewMenu.MachineLayout
                    .of(opening.roles()).grouped(),
                "a Mekanism machine gives no role signal, so it must draw as a plain grid rather "
                    + "than a grouping invented from nothing: read " + opening.roles());
            helper.assertTrue(opening.slots().stream()
                    .noneMatch(com.neryos.workbay.menu.BayViewMenu.SlotInfo::writable),
                "Bay View cannot write to a Mekanism slot, and saying it can is what draws a ghost");
            helper.succeed();
        });
    }

    /**
     * Breadth, not depth: the same reading across every machine this runtime can reach, asserting
     * the one thing that has to hold for each — a furnace and a Mekanism machine separate into
     * groups, and a container does not, because twenty-seven input slots and no output would draw
     * an arrow pointing at a group that is not there.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "The slot reading holds across vanilla containers, vanilla machines and Mekanism.")
    public static void theSlotReadingHoldsAcrossMachines(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, helper.absolutePos(new BlockPos(0, 1, 0)), player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            WorkbayMenu menu = menuFor(workbay, player);

            record(helper, menu, player, record, Blocks.FURNACE, true, "a furnace");
            record(helper, menu, player, record, Blocks.BLAST_FURNACE, true, "a blast furnace");
            record(helper, menu, player, record, Blocks.SMOKER, true, "a smoker");
            record(helper, menu, player, record, Blocks.BARREL, false, "a barrel");
            record(helper, menu, player, record, Blocks.CHEST, false, "a chest");
            record(helper, menu, player, record, Blocks.DISPENSER, false, "a dispenser");
            Block mek = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("mekanism:enrichment_chamber"));
            helper.assertFalse(mek == Blocks.AIR, "mekanism:enrichment_chamber is not registered");
            record(helper, menu, player, record, mek, false, "an Enrichment Chamber");
            helper.succeed();
        });
    }

    /** Racks one machine, reads Bay View's opening, checks the grouping, and ejects it again. */
    private static void record(ExtendedGameTestHelper helper, WorkbayMenu menu, GameTestPlayer player,
        WorkbayRecord record, Block block, boolean shouldGroup, String what) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
        menu.act(WorkbayAction.SELECT_BAY, 0, java.util.Optional.empty());
        menu.act(WorkbayAction.RACK, 0, java.util.Optional.empty());
        var opening = com.neryos.workbay.menu.BayViewMenu.opening(player, record, 0);
        if (opening == null) {
            helper.fail("Bay View would not open on " + what);
            return;
        }
        boolean grouped = com.neryos.workbay.menu.BayViewMenu.MachineLayout
            .of(opening.roles()).grouped();
        if (grouped != shouldGroup) {
            helper.fail(what + " read as " + opening.roles() + ", which "
                + (grouped ? "grouped" : "did not group") + " when it should"
                + (shouldGroup ? "" : " not") + " have");
        }
        menu.act(WorkbayAction.EJECT, 0, java.util.Optional.empty());
        player.getInventory().clearContent();
    }

    /**
     * The one mod this project compiles against beyond NeoForge, and the one thing it buys: a
     * chemical has no capability anybody else implements, so a racked machine that holds gas would
     * otherwise show nothing at all. A Chemical Oxidizer turns a solid into a gas, so it has a
     * chemical tank to find.
     *
     * <p>Asserts the reading reaches Bay View's state, not merely that the class compiles: the
     * capability is looked up by name through the API, and a name that stops matching is exactly
     * the failure this catches.
     */
    @GameTest
    @TestHolder(description = "A Mekanism machine's chemical tank reaches Bay View.")
    public static void aMekanismChemicalTankIsRead(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, helper.absolutePos(new BlockPos(0, 1, 0)), player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            Block oxidizer = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("mekanism:chemical_oxidizer"));
            helper.assertFalse(oxidizer == Blocks.AIR, "mekanism:chemical_oxidizer is not registered");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(oxidizer));
            WorkbayMenu menu = menuFor(workbay, player);
            menu.act(WorkbayAction.SELECT_BAY, 0, java.util.Optional.empty());
            menu.act(WorkbayAction.RACK, 0, java.util.Optional.empty());

            var opening = com.neryos.workbay.menu.BayViewMenu.opening(player, record, 0);
            if (opening == null) {
                helper.fail("Bay View would not open on a racked Chemical Oxidizer");
                return;
            }
            helper.assertFalse(opening.state().chemicals().isEmpty(),
                "no chemical tank reached Bay View. The capability is looked up by name through "
                    + "Mekanism's API, so a renamed capability lands exactly here.");
            helper.succeed();
        });
    }

    private static WorkbayBlockEntity placeWorkbay(ExtendedGameTestHelper helper, BlockPos pos,
        GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        level.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
            new ItemStack(WBBlocks.WORKBAY.get()));
        WorkbayBlockEntity workbay = (WorkbayBlockEntity) level.getBlockEntity(pos);
        // stillValid is a distance check, and a mock player standing across the structure fails it.
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return workbay;
    }

    private static WorkbayMenu menuFor(WorkbayBlockEntity workbay, GameTestPlayer player) {
        return new WorkbayMenu(1, player.getInventory(), workbay,
            WorkbayMenu.build(workbay, player, 0));
    }

    /**
     * The insert flow from SPEC.md §4, without a screen: hold a machine, click the bay slot, and it
     * is standing in the Backshop. Then take it back out, with everything inside it.
     */
    @GameTest
    @TestHolder(description = "Racking and ejecting through the menu moves a real machine.")
    public static void rackAndEjectThroughTheMenu(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();

            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            WorkbayMenu menu = menuFor(workbay, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.FURNACE, 3));

            menu.act(WorkbayAction.RACK, 0, Optional.empty());

            if (!backshop.getBlockState(machinePos).is(Blocks.FURNACE)) {
                helper.fail("clicking the bay slot with a furnace in hand did not rack it; the bay "
                    + "holds " + backshop.getBlockState(machinePos));
                return;
            }
            // Exactly one leaves the hand, and the registry has to agree with the Backshop or the
            // rack icon shows an empty bay holding a machine.
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "furnaces left in hand");
            helper.assertValueEqual(workbay.record().orElseThrow().bay(0).hosted().isPresent(), true,
                "the registry knowing bay 0 is occupied");

            // Something inside it, so the eject has something to lose.
            if (backshop.getBlockEntity(machinePos) instanceof Container hosted) {
                hosted.setItem(1, new ItemStack(Items.COAL, 7));
            }

            menu.act(WorkbayAction.EJECT, 0, Optional.empty());

            helper.assertValueEqual(backshop.getBlockState(machinePos).isAir(), true,
                "the bay being empty after ejecting");
            helper.assertValueEqual(workbay.record().orElseThrow().bay(0).hosted().isEmpty(), true,
                "the registry knowing bay 0 is empty");

            // Exactly three furnaces: the two still in hand plus the one that came back. Racking
            // takes the machine and ejecting returns it — neither may quietly hand out a copy.
            helper.assertValueEqual(player.getInventory().countItem(Blocks.FURNACE.asItem()), 3,
                "furnaces the player holds after the round trip");

            // And it comes back carrying what was inside it, not as a fresh block. A furnace keeps
            // its contents in the `container` data component rather than in block_entity_data —
            // this test asserted the wrong one first and went red, which is how that was found.
            int coal = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                var contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                if (!stack.is(Blocks.FURNACE.asItem()) || contents == null) {
                    continue;
                }
                for (ItemStack inside : contents.nonEmptyItems()) {
                    if (inside.is(Items.COAL)) {
                        coal += inside.getCount();
                    }
                }
            }
            helper.assertValueEqual(coal, 7, "coal carried out of the bay on the ejected furnace");
            WorkbayTickets.release(backshop, record.id(), record.bayColumn());
            level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }

    /**
     * The isometric cube is not decoration. A bay configured with no output face for items has
     * nothing an insert link can pull from, and opening one has to be what makes it move again.
     *
     * <p>Runs both halves: the negative would pass on its own if links never worked at all, so the
     * positive control after it is what makes the first half mean anything.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A bay's face config decides which faces a link may use.")
    public static void faceConfigDecidesWhichFacesALinkUses(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 1, 4));

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = level.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            WorkbayMenu menu = menuFor(workbay, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.BARREL));
            menu.act(WorkbayAction.RACK, 0, Optional.empty());

            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);
            if (!(backshop.getBlockEntity(machinePos) instanceof Container hosted)) {
                helper.fail("the bay does not hold a container after racking a barrel");
                return;
            }
            hosted.setItem(0, new ItemStack(Items.IRON_INGOT, 64));

            // Items may go IN through north and nowhere else. An insert link pulls OUT of the
            // machine, so it has no face at all to use.
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            WorkbayRecord shut = registry.byId(record.id()).orElseThrow();
            registry.put(shut.withBay(shut.bay(0).withFaces(
                FaceConfig.NONE.cycled(BusConfig.Resource.ITEM, Direction.NORTH, false))));
            workbay.forgetBay(0);

            ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
            WorkbayBlock.pair(connector, record, GlobalPos.of(level.dimension(), workbayPos), 0);
            BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.DOWN);
            level.setBlock(chestPos.above(), state, Block.UPDATE_ALL);
            WBBlocks.CONNECTOR.get().setPlacedBy(level, chestPos.above(), state, player, connector);
            BusConfig link = workbay.buses().get(0);
            // A placed Connector's link starts disabled (SPEC.md §7); this test is about the face
            // config, so it turns the link on itself rather than testing that too.
            workbay.addBus(link.withRate(64).withSpeed(10).withEnabled(true));

            helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    int moved = countIn(level, chestPos, Items.IRON_INGOT);
                    if (moved > 0) {
                        helper.fail("the link moved " + moved + " iron out of a bay whose only "
                            + "configured item face is an input");
                    }
                })
                // The positive control: turn north into an output and the same link runs.
                .thenExecute(() -> {
                    WorkbayRecord open = registry.byId(record.id()).orElseThrow();
                    registry.put(open.withBay(open.bay(0).withFaces(
                        open.bay(0).faces().cycled(BusConfig.Resource.ITEM, Direction.NORTH, false))));
                    workbay.forgetBay(0);
                })
                .thenWaitUntil(() -> {
                    if (countIn(level, chestPos, Items.IRON_INGOT) <= 0) {
                        throw new GameTestAssertException("nothing moved after north became an "
                            + "output, so the face config is not what stopped it");
                    }
                })
                .thenExecute(() -> {
                    WorkbayTickets.release(backshop, record.id(), record.bayColumn());
                    level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenSucceed();
        });
    }

    /**
     * The upgrades screen's Add button. Upgrades are consumed on install with no removal path, so
     * the item has to leave the inventory exactly once and the counter has to stop at the maximum.
     */
    @GameTest
    @TestHolder(description = "Installing an upgrade consumes the item and stops at the maximum.")
    public static void installingAnUpgradeConsumesIt(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayMenu menu = menuFor(workbay, player);

            player.getInventory().add(new ItemStack(WBItems.MULTICHANNEL.get(), 2));
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 0,
                "Multichannel installed before anything is clicked");

            // Holding the item is not enough: an upgrade costs Levy, and the cost rises. A Workbay
            // that has never run an Assay cannot install anything.
            menu.act(WorkbayAction.INSTALL_UPGRADE, WorkbayUpgrade.MULTICHANNEL.ordinal(), Optional.empty());
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 0,
                "Multichannel installed with no Levy banked");
            helper.assertValueEqual(player.getInventory().countItem(WBItems.MULTICHANNEL.get()), 2,
                "Multichannel Upgrades left after an install refused for want of Levy");

            int cost = WorkbayUpgrade.MULTICHANNEL.levyCost(0);
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            registry.put(workbay.record().orElseThrow()
                .withAssay(WorkbayRecord.Assay.NONE.withLevy(cost + 3)));

            menu.act(WorkbayAction.INSTALL_UPGRADE, WorkbayUpgrade.MULTICHANNEL.ordinal(), Optional.empty());
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 1,
                "Multichannel installed after one click");
            helper.assertValueEqual(player.getInventory().countItem(WBItems.MULTICHANNEL.get()), 1,
                "Multichannel Upgrades left in the inventory");
            helper.assertValueEqual(workbay.record().orElseThrow().assay().levy(), 3,
                "Levy left after paying for one Multichannel");

            // Max is one. A second click must refuse rather than eat the item.
            menu.act(WorkbayAction.INSTALL_UPGRADE, WorkbayUpgrade.MULTICHANNEL.ordinal(), Optional.empty());
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 1,
                "Multichannel installed after clicking past the maximum");
            helper.assertValueEqual(player.getInventory().countItem(WBItems.MULTICHANNEL.get()), 1,
                "Multichannel Upgrades left after a refused install");

            // And what the screen would draw has to agree with what the registry holds.
            WorkbaySnapshot snapshot = WorkbayMenu.build(workbay, player, 0);
            helper.assertValueEqual(snapshot.upgrades().multichannel(), 1, "the snapshot's count");
            helper.assertValueEqual(snapshot.bayCapacity(), WorkbayRecord.BASE_BAYS,
                "the snapshot's bay capacity");
            helper.assertValueEqual(snapshot.levy(), 3, "the snapshot's Levy balance");

            level.setBlock(workbayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
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
     * Copy and paste, SPEC.md §7's second QOL item. Eight bays running the same machine means
     * setting the same faces eight times, which is the complaint this answers.
     *
     * <p>The interesting part is the packing: a whole {@link FaceConfig} rides one action as 36
     * bits, so a paste that loses a resource or a face would be silent. Two of the three resources
     * are set here, to different faces, so a collapsed field cannot pass.
     */
    @GameTest
    @TestHolder(description = "A bay's face config copies onto another bay through one action.")
    public static void pastingABayCarriesEveryFace(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            WorkbayBlockEntity workbay = placeWorkbay(helper, pos, player);
            WorkbayMenu menu = menuFor(workbay, player);

            // Bay 0: items out of the west face, energy into the top one.
            menu.act(WorkbayAction.CYCLE_FACE,
                BusConfig.Resource.ITEM.ordinal() | (Direction.WEST.ordinal() << 4), Optional.empty());
            menu.act(WorkbayAction.CYCLE_FACE,
                BusConfig.Resource.ITEM.ordinal() | (Direction.WEST.ordinal() << 4), Optional.empty());
            menu.act(WorkbayAction.CYCLE_FACE,
                BusConfig.Resource.ENERGY.ordinal() | (Direction.UP.ordinal() << 4), Optional.empty());

            FaceConfig source = workbay.record().orElseThrow().bay(0).faces();
            helper.assertValueEqual(source.role(BusConfig.Resource.ITEM, Direction.WEST),
                FaceConfig.Role.OUTPUT, "the copied bay's west item face");

            menu.act(WorkbayAction.SELECT_BAY, 1, Optional.empty());
            menu.act(WorkbayAction.PASTE_BAY, source.bits(), Optional.empty());

            FaceConfig pasted = workbay.record().orElseThrow().bay(1).faces();
            helper.assertValueEqual(pasted, source, "the pasted bay's whole face config");
            helper.assertValueEqual(pasted.role(BusConfig.Resource.ENERGY, Direction.UP),
                FaceConfig.Role.INPUT, "the pasted bay's up energy face");
            helper.assertValueEqual(pasted.role(BusConfig.Resource.FLUID, Direction.WEST),
                FaceConfig.Role.NONE, "a resource the source never set");
            helper.succeed();
        });
    }

    /**
     * The lock is the mod's only permission, and it was leaking. {@code rack} and {@code eject}
     * each carried their own copy of the owner check and {@code cycleFace} carried none, so anyone
     * could rewrite a locked Workbay's faces and quietly stop its links.
     */
    @GameTest
    @TestHolder(description = "A locked Workbay refuses face edits from anyone but its owner.")
    public static void aLockedWorkbayRefusesFaceEditsFromStrangers(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            WorkbayBlockEntity workbay = placeWorkbay(helper, pos, owner);
            menuFor(workbay, owner).act(WorkbayAction.TOGGLE_LOCK, 0, Optional.empty());
            if (!workbay.record().orElseThrow().locked()) {
                helper.fail("the owner could not lock their own Workbay");
                return;
            }

            GameTestPlayer stranger = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            stranger.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            WorkbayMenu theirs = new WorkbayMenu(2, stranger.getInventory(), workbay,
                WorkbayMenu.build(workbay, stranger, 0));
            theirs.act(WorkbayAction.CYCLE_FACE,
                BusConfig.Resource.ITEM.ordinal() | (Direction.WEST.ordinal() << 4), Optional.empty());
            theirs.act(WorkbayAction.PASTE_BAY, FaceConfig.NONE
                .cycled(BusConfig.Resource.ITEM, Direction.EAST, false).bits(), Optional.empty());

            FaceConfig after = workbay.record().orElseThrow().bay(0).faces();
            helper.assertValueEqual(after, FaceConfig.NONE,
                "a locked Workbay's faces after a stranger tried to change them");
            helper.succeed();
        });
    }

    /**
     * A drag out of JEI or EMI arrives as one number: the item's registry id, the way every vanilla
     * packet carries one. Asserted end to end because an id that survives the trip as the wrong
     * item would be a filter that silently blocks everything.
     */
    @GameTest
    @TestHolder(description = "The filter slot round-trips an item id, and -1 clears it.")
    public static void theFilterSlotRoundTripsAnItemId(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos targetPos = helper.absolutePos(new BlockPos(3, 1, 3));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, pos, player);
            ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
            WorkbayBlock.pair(connector, workbay.record().orElseThrow(),
                GlobalPos.of(level.dimension(), pos), 0);
            BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.DOWN);
            level.setBlock(targetPos.above(), state, Block.UPDATE_ALL);
            WBBlocks.CONNECTOR.get().setPlacedBy(level, targetPos.above(), state, player, connector);

            BusConfig link = workbay.buses().get(0);
            WorkbayMenu menu = menuFor(workbay, player);

            // Slot 0, and the id plus one, which is how the packet says "clear" with a zero.
            menu.act(WorkbayAction.SET_FILTER,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.REDSTONE) + 1L,
                Optional.of(link.id()));
            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().filter().entries(),
                java.util.List.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(Items.REDSTONE)),
                "the filter after dropping redstone on the slot");

            // A second entry goes in beside the first rather than replacing it. That is the whole
            // difference between this filter and the one item the row used to hold.
            menu.act(WorkbayAction.SET_FILTER, (1L << 32)
                | (net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.COAL) + 1L),
                Optional.of(link.id()));
            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().filter().entries().size(),
                2, "entries after listing a second item");

            menu.act(WorkbayAction.TOGGLE_FILTER_DENY, 0, Optional.of(link.id()));
            helper.assertTrue(workbay.bus(link.id()).orElseThrow().filter().deny(),
                "the filter did not turn into a deny list");

            menu.act(WorkbayAction.SET_FILTER, 0, Optional.of(link.id()));
            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().filter().entries(),
                java.util.List.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(Items.COAL)),
                "the filter after clicking slot 0 to clear it");

            // Changing what the link carries drops the list: those ids name items, and an item id
            // read as a fluid id is a filter that quietly refuses everything.
            workbay.addBus(workbay.bus(link.id()).orElseThrow()
                .withResource(BusConfig.Resource.FLUID));
            helper.assertTrue(workbay.bus(link.id()).orElseThrow().filter().isEmpty(),
                "the filter survived a change of resource");
            helper.succeed();
        });
    }

    /**
     * Reported from play: toggling a link's checkbox visibly jumped it to the bottom of the list.
     * The screen's default sort has no comparator at all and relies on the snapshot's own list
     * order — {@link com.neryos.workbay.content.workbay.WorkbayBlockEntity#addBus} used to remove
     * the edited link and append it, which silently reordered the list on every single edit.
     */
    @GameTest
    @TestHolder(description = "Editing a link does not change its position in the list.")
    public static void editingALinkDoesNotReorderTheList(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(7, 5, 7));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            WorkbayBlockEntity workbay = placeWorkbay(helper, pos, player);

            // Three links, three different targets, so their order is unambiguous.
            java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                BlockPos targetPos = helper.absolutePos(new BlockPos(3 + i, 1, 3));
                level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
                WorkbayBlock.pair(connector, workbay.record().orElseThrow(),
                    GlobalPos.of(level.dimension(), pos), 0);
                BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(ConnectorBlock.FACING, Direction.DOWN);
                level.setBlock(targetPos.above(), state, Block.UPDATE_ALL);
                WBBlocks.CONNECTOR.get().setPlacedBy(level, targetPos.above(), state, player, connector);
                ids.add(workbay.buses().get(workbay.buses().size() - 1).id());
            }

            WorkbayMenu menu = menuFor(workbay, player);
            // Edit the FIRST link every way the row offers: enable it, flip its mode, rename its
            // filter. Any one of these used to send it to the back of the list.
            menu.act(WorkbayAction.LINK_TOGGLE_ENABLED, 0, Optional.of(ids.get(0)));
            menu.act(WorkbayAction.LINK_FLIP_MODE, 0, Optional.of(ids.get(0)));
            menu.act(WorkbayAction.LINK_CYCLE_RESOURCE, 0, Optional.of(ids.get(0)));

            java.util.List<java.util.UUID> after = workbay.buses().stream()
                .map(BusConfig::id).toList();
            helper.assertValueEqual(after, ids, "link order after editing the first link three times");
            helper.succeed();
        });
    }

    /**
     * Right-click steps a cycling control to the <em>previous</em> value, everywhere one cycles.
     *
     * <p>Worth a test rather than eyeballing because the interesting cases are the wraps, and each
     * of the four rings wraps differently: the redstone modes and the face roles are plain enums,
     * the target face is a ring of seven whose first slot is "any" rather than a Direction, and
     * the target bay is a ring with a hole in it — the link's own bay is skipped, in whichever
     * direction it is being skipped from.
     */
    @GameTest
    @TestHolder(description = "Right-clicking a cycling control steps it back, wraps included.")
    public static void rightClickStepsACycleBackwards(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, pos, player);
            RoomRegistry registry = RoomRegistry.get(level.getServer());
            // Three bays, so the target-bay ring has a value on both sides of the hole. One plate,
            // because the base Workbay already grants two (WorkbayRecord.BASE_BAYS).
            registry.put(workbay.record().orElseThrow()
                .withUpgrades(new WorkbayRecord.Upgrades(1, 0, 0, 0, 0, 0, 0)));
            WorkbayMenu menu = menuFor(workbay, player);

            // The redstone ring: forward one step off ALWAYS, then back past it to the far end.
            menu.act(WorkbayAction.CYCLE_REDSTONE, 0, Optional.empty(), Optional.empty(), false);
            helper.assertValueEqual(workbay.record().orElseThrow().bay(0).redstone(),
                com.neryos.workbay.world.RedstoneMode.WITH_SIGNAL, "redstone after a left-click");
            menu.act(WorkbayAction.CYCLE_REDSTONE, 0, Optional.empty(), Optional.empty(), true);
            menu.act(WorkbayAction.CYCLE_REDSTONE, 0, Optional.empty(), Optional.empty(), true);
            helper.assertValueEqual(workbay.record().orElseThrow().bay(0).redstone(),
                com.neryos.workbay.world.RedstoneMode.PULSE, "redstone wrapped backwards past ALWAYS");

            // A face role, off the cube: NONE backwards is OUTPUT, not INPUT.
            menu.act(WorkbayAction.CYCLE_FACE,
                BusConfig.Resource.ITEM.ordinal() | (Direction.NORTH.ordinal() << 4),
                Optional.empty(), Optional.empty(), true);
            helper.assertValueEqual(
                workbay.record().orElseThrow().bay(0).faces()
                    .role(BusConfig.Resource.ITEM, Direction.NORTH),
                FaceConfig.Role.OUTPUT, "a face role stepped backwards off NONE");

            // An internal link, for the two rings that live on a row.
            menu.act(WorkbayAction.SELECT_BAY, 0, Optional.empty());
            menu.act(WorkbayAction.CREATE_INTERNAL_LINK, 1, Optional.empty());
            BusConfig link = workbay.buses().stream().filter(BusConfig::internal).findFirst()
                .orElseThrow(() -> new GameTestAssertException("no internal link was made"));

            // The face ring starts on "any", so one step back is the LAST direction, not the first.
            menu.act(WorkbayAction.LINK_CYCLE_TARGET_FACE, 0, Optional.of(link.id()),
                Optional.empty(), true);
            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().targetFace(),
                Optional.of(Direction.values()[Direction.values().length - 1]),
                "the target face stepped backwards off any");

            // Bay 1 holds the link and bay 2 is its target, so stepping back from bay 2 has to skip
            // bay 1 -- the link's own -- and land on bay 3.
            menu.act(WorkbayAction.LINK_CYCLE_TARGET_BAY, 0, Optional.of(link.id()),
                Optional.empty(), true);
            WorkbayRecord record = workbay.record().orElseThrow();
            helper.assertValueEqual(workbay.bus(link.id()).orElseThrow().target().pos(),
                BayGeometry.machinePos(record.bayColumn(), 2),
                "the target bay stepped backwards, skipping the link's own bay");

            WorkbayTickets.release(level.getServer().getLevel(WorkbayDimensions.BACKSHOP),
                record.id(), record.bayColumn());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.succeed();
        });
    }
}
