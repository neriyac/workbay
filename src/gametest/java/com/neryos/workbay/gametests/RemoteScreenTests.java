package com.neryos.workbay.gametests;

import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.remote.RemoteScreens;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import com.neryos.workbay.world.WorkbayTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.Optional;

/**
 * A hosted machine's screen, opened without going anywhere. SPEC.md §5.
 *
 * <p>Only half of that is testable here, and it is the half worth guarding: a gametest has no
 * client, so it can prove the server opened a screen and kept it open, never that the client drew
 * one. The client half — a menu resolving a block entity in a dimension it was never sent — is
 * {@code LevelMixin}, and only a real client can say whether it holds.
 *
 * <p>A <b>vanilla furnace</b> on purpose. Its {@code stillValid} is the standard one every mod
 * inherits when it does not write its own, and it lands exactly on {@code canInteractWithBlock} —
 * so this measures the injection rather than one mod's opinion of it.
 */
@ForEachTest(groups = "remote_screen")
public class RemoteScreenTests {

    @GameTest
    @TestHolder(description = "A machine's own screen opens where the player stands, and survives "
        + "the distance check that would otherwise close it on the next tick.")
    public static void aRemoteScreenSurvivesTheDistanceCheck(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            ServerLevel home = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
            home.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
            WBBlocks.WORKBAY.get().setPlacedBy(home, pos, home.getBlockState(pos), player,
                new ItemStack(WBBlocks.WORKBAY.get()));
            player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            WorkbayBlockEntity workbay = (WorkbayBlockEntity) home.getBlockEntity(pos);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = home.getServer().getLevel(WorkbayDimensions.BACKSHOP);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            WorkbayMenu menu = new WorkbayMenu(1, player.getInventory(), workbay,
                WorkbayMenu.build(workbay, player, 0));
            menu.act(WorkbayAction.SELECT_BAY, 0, Optional.empty());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FURNACE));
            menu.act(WorkbayAction.RACK, 0, Optional.empty());

            BlockPos machine = BayGeometry.machinePos(record.bayColumn(), 0);
            helper.assertFalse(backshop.getBlockState(machine).isAir(), "racking a furnace left the bay empty");

            helper.assertTrue(RemoteScreens.open(player, backshop, machine),
                "the furnace in the bay opened no screen for a player standing in the overworld");
            helper.assertTrue(player.level().dimension().equals(home.dimension()),
                "opening a screen remotely moved the player; it must not");

            helper.startSequence()
                // ServerPlayer#doTick asks stillValid every tick. Without the injection the menu is
                // gone on the first one, so waiting is the whole test.
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(player.containerMenu != player.inventoryMenu,
                    "the remote screen was closed by the distance check within 5 ticks"))
                .thenExecute(player::closeContainer)
                .thenExecute(() -> helper.assertFalse(
                    RemoteScreens.isOpenAt(player, machine),
                    "closing the screen left the player reaching a machine in another dimension"))
                .thenSucceed();
        });
    }
}
