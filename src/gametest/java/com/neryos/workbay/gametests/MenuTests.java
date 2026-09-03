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
            if (player.getInventory().countItem(Blocks.FURNACE.asItem()) < 3) {
                helper.fail("the ejected furnace did not come back to the player");
                return;
            }
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
                FaceConfig.NONE.cycled(BusConfig.Resource.ITEM, Direction.NORTH))));
            workbay.forgetBay(0);

            ItemStack connector = new ItemStack(WBBlocks.CONNECTOR.get());
            WorkbayBlock.pair(connector, record, GlobalPos.of(level.dimension(), workbayPos), 0);
            BlockState state = WBBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.DOWN);
            level.setBlock(chestPos.above(), state, Block.UPDATE_ALL);
            WBBlocks.CONNECTOR.get().setPlacedBy(level, chestPos.above(), state, player, connector);
            BusConfig link = workbay.buses().get(0);
            workbay.addBus(link.withRate(64).withSpeed(10));

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
                        open.bay(0).faces().cycled(BusConfig.Resource.ITEM, Direction.NORTH))));
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

            menu.act(WorkbayAction.INSTALL_UPGRADE, WorkbayUpgrade.MULTICHANNEL.ordinal(), Optional.empty());
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 1,
                "Multichannel installed after one click");
            helper.assertValueEqual(player.getInventory().countItem(WBItems.MULTICHANNEL.get()), 1,
                "Multichannel Upgrades left in the inventory");

            // Max is one. A second click must refuse rather than eat the item.
            menu.act(WorkbayAction.INSTALL_UPGRADE, WorkbayUpgrade.MULTICHANNEL.ordinal(), Optional.empty());
            helper.assertValueEqual(workbay.record().orElseThrow().upgrades().multichannel(), 1,
                "Multichannel installed after clicking past the maximum");
            helper.assertValueEqual(player.getInventory().countItem(WBItems.MULTICHANNEL.get()), 1,
                "Multichannel Upgrades left after a refused install");

            // And what the screen would draw has to agree with what the registry holds.
            WorkbaySnapshot snapshot = WorkbayMenu.build(workbay, player, 0);
            helper.assertValueEqual(snapshot.upgrades().multichannel(), 1, "the snapshot's count");
            helper.assertValueEqual(snapshot.bayCapacity(), 1, "the snapshot's bay capacity");

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
}
