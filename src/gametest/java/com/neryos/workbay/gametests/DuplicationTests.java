package com.neryos.workbay.gametests;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.assay.AssayBlock;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.menu.BayViewMenu;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import com.neryos.workbay.world.WorkbayTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.Optional;

/**
 * The duplication audit. ROADMAP.md §2.
 *
 * <p>One rule, and every test here is a way of trying to break it: <b>the item exists in exactly
 * one place at the end of every tick.</b> Not one place plus a copy, and not nought places — a mod
 * whose pitch is "put your machine inside this block and take it back out" has to be right about
 * both, and the audience that finds either is the one that publishes it.
 *
 * <p>Each test therefore <em>counts</em> rather than asserts a state. A census over every owner an
 * item could be sitting in — a player's inventory, a cursor, a container in the world, a container
 * in the Backshop, the contents saved onto an ejected machine's item, and loose item entities —
 * has to come back with the same number it started with. That shape is deliberate: an assertion
 * that "the bay is empty" passes just as happily when the machine was deleted.
 */
@ForEachTest(groups = "duplication")
public class DuplicationTests {

    // ------------------------------------------------------------- the census

    /** Everything one item could be in, on one player. Cursor included: that is where a dupe hides. */
    private static int onPlayer(GameTestPlayer player, Item item) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            total += count(inventory.getItem(slot), item);
        }
        return total;
    }

    /** A stack, plus whatever that stack is carrying — an ejected machine keeps its contents. */
    private static int count(ItemStack stack, Item item) {
        if (stack.isEmpty()) {
            return 0;
        }
        int total = stack.is(item) ? stack.getCount() : 0;
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inside : contents.nonEmptyItems()) {
                total += count(inside, item);
            }
        }
        var blockEntity = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntity != null) {
            total += inSavedBlockEntity(blockEntity.copyTag(), item);
        }
        return total;
    }

    /**
     * A machine ejected with a part-finished recipe in it keeps its contents in
     * {@code minecraft:block_entity_data}, not in {@code minecraft:container} — SPEC.md §10 step 2
     * uses {@code saveToItem}, which writes the block entity's own NBT. Reading it back is the only
     * way a census can tell "the contents came with it" from "the contents are gone".
     */
    private static int inSavedBlockEntity(net.minecraft.nbt.CompoundTag tag, Item item) {
        if (!tag.contains("Items", net.minecraft.nbt.Tag.TAG_LIST)) {
            return 0;
        }
        int total = 0;
        var list = tag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            if (id.equals(entry.getString("id"))) {
                total += entry.getInt("count");
            }
        }
        return total;
    }

    /**
     * Through the capability rather than {@code Container}, because a Mekanism machine is not one.
     * The null side is what Bay View itself reads, so this counts what Bay View can see.
     */
    private static int inHandler(ServerLevel level, BlockPos pos, Item item) {
        var handler = level.getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            total += count(handler.getStackInSlot(slot), item);
        }
        return total;
    }

    /**
     * Millibuckets of one fluid standing in a block's tanks, through the same capability Bay View
     * reads. A fluid census has to count these <em>and</em> the buckets carrying it, because a
     * bucket that empties into a tank without emptying is exactly the item-dupe bug wearing a
     * different hat.
     */
    private static int inTanks(ServerLevel level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        var handler = level.getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var contents = handler.getFluidInTank(tank);
            if (contents.getFluid() == fluid) {
                total += contents.getAmount();
            }
        }
        return total;
    }

    /** Energy stored in a block, on whichever side answers. Never the null side twice over. */
    private static int inEnergy(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            var store = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, side);
            if (store != null) {
                return store.getEnergyStored();
            }
        }
        var store = level.getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, null);
        return store == null ? 0 : store.getEnergyStored();
    }

    private static int inContainer(ServerLevel level, BlockPos pos, Item item) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += count(container.getItem(slot), item);
        }
        return total;
    }

    /**
     * Loose on the floor, inside this test's own structure and a little around it.
     *
     * <p><b>Bounded by {@code helper.getBounds()}, not by a radius around a position.</b> Two
     * things go wrong with a radius. Two calls around two positions in the same structure count
     * every dropped stack twice; and a box wide enough to be safe reaches into the <em>next</em>
     * gametest's structure, so a neighbour's spilled chest lands in this census. Both read exactly
     * like a duplication bug in the mod, and this file "found" each of them once.
     */
    private static int loose(ExtendedGameTestHelper helper, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            helper.getBounds().inflate(2))) {
            total += count(entity.getItem(), item);
        }
        return total;
    }

    private static int carried(net.minecraft.world.inventory.AbstractContainerMenu menu, Item item) {
        return count(menu.getCarried(), item);
    }

    /**
     * Every place a container handed to Bay View's fluid slots could be: the player, the cursor,
     * both fluid slots, and the floor. A census that skips one of those passes a screen that
     * quietly keeps a second copy in it.
     */
    private static int inEveryPlace(ExtendedGameTestHelper helper, GameTestPlayer player,
        BayViewMenu view, Item item) {
        return onPlayer(player, item) + carried(view, item) + loose(helper, item)
            + count(inFluidSlot(view, 0), item) + count(inFluidSlot(view, 1), item);
    }

    /** The fluid slots are the last two on the menu: in, then out. */
    private static ItemStack inFluidSlot(BayViewMenu view, int which) {
        return view.getSlot(view.slots.size() - 2 + which).getItem();
    }

    private static void putInFluidSlot(BayViewMenu view, int which, ItemStack stack) {
        view.getSlot(view.slots.size() - 2 + which).set(stack);
    }

    /**
     * A real mod's fluid handler is the point of these tests, so a missing partner mod is a
     * failure and never a skip.
     */
    private static Block mekanismTank(ExtendedGameTestHelper helper) {
        Block tank = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(net.minecraft.resources.ResourceLocation.parse("mekanism:basic_fluid_tank"));
        if (tank == Blocks.AIR) {
            helper.fail("mekanism:basic_fluid_tank is not registered. These tests are about a real "
                + "mod's fluid handler, so a missing partner mod is a failure, never a skip. "
                + "Check the gametestRuntimeOnly Mekanism dependency in build.gradle.");
        }
        return tank;
    }

    // ---------------------------------------------------------------- fixture

    private static WorkbayBlockEntity placeWorkbay(ExtendedGameTestHelper helper, BlockPos pos,
        GameTestPlayer player) {
        ServerLevel level = helper.getLevel();
        level.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
            new ItemStack(WBBlocks.WORKBAY.get()));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return (WorkbayBlockEntity) level.getBlockEntity(pos);
    }

    private static WorkbayMenu menuFor(WorkbayBlockEntity workbay, GameTestPlayer player) {
        return new WorkbayMenu(1, player.getInventory(), workbay,
            WorkbayMenu.build(workbay, player, 0));
    }

    private static void rack(WorkbayMenu menu, GameTestPlayer player, int bay, ItemStack machine) {
        menu.act(WorkbayAction.SELECT_BAY, bay, Optional.empty());
        player.setItemInHand(InteractionHand.MAIN_HAND, machine);
        menu.act(WorkbayAction.RACK, 0, Optional.empty());
    }

    /** Bay View, built exactly the way {@link BayViewMenu#open} builds it, minus the connection. */
    private static BayViewMenu bayView(int containerId, GameTestPlayer player,
        WorkbayBlockEntity workbay, int bay) {
        WorkbayRecord record = workbay.record().orElseThrow();
        BayViewMenu.Opening opening = BayViewMenu.opening(player, record, bay);
        if (opening == null) {
            throw new GameTestAssertException("nothing in bay " + (bay + 1) + " answers by hand, so "
                + "Bay View would not open on it");
        }
        return new BayViewMenu(containerId, player.getInventory(), workbay, bay,
            opening.machineId(), opening.roles(), opening.handler(), opening.state());
    }

    private static ServerLevel backshop(ExtendedGameTestHelper helper) {
        return helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
    }

    private static void put(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            container.setItem(slot, stack);
        }
    }

    // ------------------------------------------------------- racking the block

    /**
     * The base case, and the reason every other test here is a census. Rack a machine, eject it,
     * and there is one of it the whole way through — never two, never nought.
     */
    @GameTest
    @TestHolder(description = "Racking then ejecting a machine leaves exactly one of it in the world.")
    public static void aRackedMachineIsNeverInTwoPlacesOrNone(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            WorkbayMenu menu = menuFor(workbay, player);
            player.getInventory().clearContent();
            rack(menu, player, 0, new ItemStack(Blocks.FURNACE, 1));

            int afterRack = onPlayer(player, Items.FURNACE) + loose(helper, Items.FURNACE)
                + (backshop.getBlockState(machinePos).is(Blocks.FURNACE) ? 1 : 0);
            helper.assertValueEqual(afterRack, 1, "furnaces in existence after racking one");

            menu.act(WorkbayAction.EJECT, 0, Optional.empty());
            int afterEject = onPlayer(player, Items.FURNACE) + loose(helper, Items.FURNACE)
                + (backshop.getBlockState(machinePos).is(Blocks.FURNACE) ? 1 : 0);
            helper.assertValueEqual(afterEject, 1, "furnaces in existence after ejecting it again");
            helper.succeed();
        });
    }

    /**
     * Two players, one bay, both clicking eject in the same tick. ROADMAP §2 names this one
     * explicitly, and it is the classic: the second eject must find the bay already empty rather
     * than hand out a second copy of somebody's machine.
     */
    @GameTest
    @TestHolder(description = "Two players ejecting the same bay in one tick get one machine between them.")
    public static void twoPlayersEjectingOneBayGetOneMachine(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer first = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            GameTestPlayer second = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, first);
            second.moveTo(workbayPos.getX() + 0.5, workbayPos.getY(), workbayPos.getZ() + 0.5);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            first.getInventory().clearContent();
            second.getInventory().clearContent();
            WorkbayMenu mine = menuFor(workbay, first);
            rack(mine, first, 0, new ItemStack(Blocks.FURNACE, 1));

            // Both screens were opened before either clicked, which is the whole point: the second
            // menu's snapshot still says the bay is full when the first eject lands.
            WorkbayMenu theirs = menuFor(workbay, second);
            mine.act(WorkbayAction.EJECT, 0, Optional.empty());
            theirs.act(WorkbayAction.EJECT, 0, Optional.empty());

            int total = onPlayer(first, Items.FURNACE) + onPlayer(second, Items.FURNACE)
                + loose(helper, Items.FURNACE)
                + (backshop.getBlockState(machinePos).isAir() ? 0 : 1);
            helper.assertValueEqual(total, 1, "furnaces after two players both clicked eject");
            helper.succeed();
        });
    }

    /**
     * A machine ejected with a part-finished recipe inside it. SPEC.md §10's extract order removes
     * the block entity <em>before</em> the setBlock precisely so this does not spill into a sealed
     * room, and the census reads the saved NBT rather than the block, so "the contents came with
     * it" cannot be confused with "the contents are gone".
     */
    @GameTest
    @TestHolder(description = "Ejecting a machine that is mid-recipe brings its contents out with it.")
    public static void ejectingAMidRecipeMachineKeepsWhatIsInside(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            WorkbayMenu menu = menuFor(workbay, player);
            rack(menu, player, 0, new ItemStack(Blocks.FURNACE, 1));
            // Smelting in progress: ore in the input, fuel in the burner, a finished ingot waiting.
            put(backshop, machinePos, 0, new ItemStack(Items.RAW_IRON, 5));
            put(backshop, machinePos, 1, new ItemStack(Items.COAL, 3));
            put(backshop, machinePos, 2, new ItemStack(Items.IRON_INGOT, 2));

            menu.act(WorkbayAction.EJECT, 0, Optional.empty());

            helper.assertValueEqual(onPlayer(player, Items.RAW_IRON)
                + loose(helper, Items.RAW_IRON), 5, "raw iron after the eject");
            helper.assertValueEqual(onPlayer(player, Items.COAL)
                + loose(helper, Items.COAL), 3, "coal after the eject");
            helper.assertValueEqual(onPlayer(player, Items.IRON_INGOT)
                + loose(helper, Items.IRON_INGOT), 2, "iron ingots after the eject");
            helper.assertValueEqual(inContainer(backshop, machinePos, Items.RAW_IRON), 0,
                "raw iron left standing in an emptied bay");
            helper.succeed();
        });
    }

    /**
     * Breaking the Workbay while a machine is racked. SPEC.md §14 keeps the machines running, so
     * the machine must still be exactly where it was — the failure this guards against is a
     * teardown path that helpfully "returns" it as well.
     */
    @GameTest
    @TestHolder(description = "Breaking a Workbay with a machine racked neither drops nor deletes it.")
    public static void breakingTheWorkbayLeavesTheMachineWhereItIs(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.FURNACE, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.RAW_IRON, 7));

            level.destroyBlock(workbayPos, true, player);

            int furnaces = onPlayer(player, Items.FURNACE) + loose(helper, Items.FURNACE)
                + (backshop.getBlockState(machinePos).is(Blocks.FURNACE) ? 1 : 0);
            helper.assertValueEqual(furnaces, 1, "furnaces after breaking the Workbay around one");
            helper.assertValueEqual(inContainer(backshop, machinePos, Items.RAW_IRON), 7,
                "raw iron still inside the hosted furnace");
            helper.succeed();
        });
    }

    // -------------------------------------------------------------- Bay View

    /**
     * Two players in the same Bay View, both taking the same stack in the same tick. The server's
     * handler is the only authority, so the second click must find an empty slot — the failure is a
     * client-optimistic screen that lets both of them walk away with a diamond.
     */
    @GameTest
    @TestHolder(description = "Two players taking one stack out of one bay share it, never copy it.")
    public static void twoPlayersInOneBayCannotBothTakeTheSameStack(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer first = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            GameTestPlayer second = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, first);
            second.moveTo(workbayPos.getX() + 0.5, workbayPos.getY(), workbayPos.getZ() + 0.5);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            first.getInventory().clearContent();
            second.getInventory().clearContent();
            rack(menuFor(workbay, first), first, 0, new ItemStack(Blocks.CHEST, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.DIAMOND, 1));

            BayViewMenu mine = bayView(2, first, workbay, 0);
            BayViewMenu theirs = bayView(3, second, workbay, 0);
            mine.clicked(0, 0, ClickType.PICKUP, first);
            theirs.clicked(0, 0, ClickType.PICKUP, second);

            int total = carried(mine, Items.DIAMOND) + carried(theirs, Items.DIAMOND)
                + onPlayer(first, Items.DIAMOND) + onPlayer(second, Items.DIAMOND)
                + inContainer(backshop, machinePos, Items.DIAMOND);
            helper.assertValueEqual(total, 1, "diamonds after both players clicked the same slot");
            helper.succeed();
        });
    }

    /**
     * The machine is ejected while somebody has Bay View open on it. Every later click must move
     * nothing: the handler is gone, so {@code mayPlace} and {@code mayPickup} are both false and
     * vanilla's click paths decline. The failure mode this catches is the quiet one — a cached
     * handler still accepting the insert, into a block entity nobody owns any more.
     */
    @GameTest
    @TestHolder(description = "Ejecting a machine under an open Bay View makes it refuse, not swallow.")
    public static void bayViewOverAnEjectedMachineMovesNothing(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            WorkbayMenu bays = menuFor(workbay, player);
            rack(bays, player, 0, new ItemStack(Blocks.CHEST, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.DIAMOND, 4));

            BayViewMenu view = bayView(2, player, workbay, 0);
            // A stack on the cursor when the floor disappears: the worst moment to get this wrong.
            view.setCarried(new ItemStack(Items.EMERALD, 6));
            bays.act(WorkbayAction.EJECT, 0, Optional.empty());

            if (view.stillValid(player)) {
                helper.fail("Bay View stayed valid after its machine was ejected");
            }
            view.clicked(0, 0, ClickType.PICKUP, player);
            view.clicked(0, 0, ClickType.QUICK_MOVE, player);

            helper.assertValueEqual(carried(view, Items.EMERALD), 6,
                "emeralds still on the cursor after clicking into a bay that no longer has a machine");
            int diamonds = carried(view, Items.DIAMOND) + onPlayer(player, Items.DIAMOND)
                + loose(helper, Items.DIAMOND);
            helper.assertValueEqual(diamonds, 4,
                "diamonds after the chest holding them was ejected under an open Bay View");
            helper.succeed();
        });
    }

    /**
     * Logging out with a stack on the cursor. {@code AbstractContainerMenu#removed} is what puts it
     * back, and a menu that overrides {@code removed} badly is how a disconnect eats an inventory —
     * so this asserts the mod did not, rather than trusting that it did not.
     */
    @GameTest
    @TestHolder(description = "Closing Bay View with a stack on the cursor returns it, never drops it into nothing.")
    public static void aStackInFlightSurvivesClosingBayView(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.CHEST, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.DIAMOND, 9));

            BayViewMenu view = bayView(2, player, workbay, 0);
            view.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertValueEqual(carried(view, Items.DIAMOND), 9,
                "diamonds picked up onto the cursor");

            view.removed(player);
            int total = carried(view, Items.DIAMOND) + onPlayer(player, Items.DIAMOND)
                + inContainer(backshop, machinePos, Items.DIAMOND)
                + loose(helper, Items.DIAMOND);
            helper.assertValueEqual(total, 9, "diamonds after the screen closed with them in flight");
            helper.assertValueEqual(carried(view, Items.DIAMOND), 0,
                "diamonds left stranded on a closed menu's cursor");
            helper.succeed();
        });
    }

    /**
     * The whole reason Bay View exists: a racked container the player can actually fill. Shift in,
     * shift back out, and the count never moves — which is the assertion that catches
     * {@code moveItemStackTo} being used on a handler that hands out copies.
     */
    @GameTest
    @TestHolder(description = "Shift-clicking a stack into a hosted container and back out conserves it.")
    public static void bayViewMovesItemsBothWaysWithoutLosingAny(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.CHEST, 1));
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 37));

            BayViewMenu view = bayView(2, player, workbay, 0);
            int playerSlot = view.machineSlots();
            // Find the player slot the ingots landed in; the grid starts right after the machine's.
            for (int slot = view.machineSlots(); slot < view.slots.size(); slot++) {
                if (view.slots.get(slot).getItem().is(Items.IRON_INGOT)) {
                    playerSlot = slot;
                    break;
                }
            }
            view.clicked(playerSlot, 0, ClickType.QUICK_MOVE, player);
            helper.assertValueEqual(inContainer(backshop, machinePos, Items.IRON_INGOT), 37,
                "iron ingots shift-clicked into the hosted chest");
            helper.assertValueEqual(onPlayer(player, Items.IRON_INGOT), 0,
                "iron ingots left in the player's inventory after shift-clicking them in");

            view.clicked(0, 0, ClickType.QUICK_MOVE, player);
            int total = onPlayer(player, Items.IRON_INGOT)
                + inContainer(backshop, machinePos, Items.IRON_INGOT)
                + carried(view, Items.IRON_INGOT) + loose(helper, Items.IRON_INGOT);
            helper.assertValueEqual(total, 37, "iron ingots after a round trip through Bay View");
            helper.assertValueEqual(onPlayer(player, Items.IRON_INGOT), 37,
                "iron ingots back in the player's inventory");
            helper.succeed();
        });
    }

    /**
     * The one that was found by hand, in {@code runClient}, against a real Mekanism machine — and
     * the reason {@link BayViewMenu.Live} no longer trusts a foreign handler's own answers.
     *
     * <p>Mekanism hands out a <b>read-only</b> handler on the null side for every machine it has
     * ({@code ProxyHandler}: {@code readOnly = side == null}), and that handler does two things
     * that combine into an item shredder. {@code ProxyItemHandler#isItemValid} reports the real
     * slot validity <em>regardless</em> of read-only, so {@code Slot#mayPlace} says yes; and
     * {@code setStackInSlot} is {@code if (!readOnly) &#123; ... &#125;}, a silent no-op. Vanilla's
     * {@code doClick} takes the stack off the cursor between those two, so sixteen redstone dust
     * stopped existing — no error, no message, nothing in the log.
     *
     * <p>Deliberately driven with a {@code PICKUP} click rather than a shift-click: shift-click
     * goes through {@link BayViewMenu#quickMoveStack}, which was already written against
     * {@code insertItem} and was never the broken path. Every machine slot is tried, because which
     * index is the recipe input is Mekanism's business and the census has to hold for all of them.
     */
    @GameTest
    @TestHolder(description = "Clicking items onto a Mekanism machine's read-only slots never deletes them.")
    public static void bayViewOverAReadOnlyHandlerNeverEatsTheCursor(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            Block machine = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("mekanism:enrichment_chamber"));
            if (machine == Blocks.AIR) {
                helper.fail("mekanism:enrichment_chamber is not registered. This test is about a "
                    + "real mod's read-only handler, so a missing partner mod is a failure, never "
                    + "a skip. Check the gametestRuntimeOnly Mekanism dependency in build.gradle.");
            }

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(machine, 1));

            BayViewMenu view = bayView(2, player, workbay, 0);
            helper.assertTrue(view.machineSlots() > 0,
                "Bay View opened on a Mekanism machine with no slots, so this test proves nothing");

            for (int slot = 0; slot < view.machineSlots(); slot++) {
                // Counted per slot rather than once: a slot that legitimately accepts the redstone
                // leaves it in the machine, and the next slot's census must expect it to be there.
                int held = inHandler(backshop, machinePos, Items.REDSTONE);
                view.setCarried(new ItemStack(Items.REDSTONE, 16));
                view.clicked(slot, 0, ClickType.PICKUP, player);
                int total = onPlayer(player, Items.REDSTONE)
                    + carried(view, Items.REDSTONE)
                    + inHandler(backshop, machinePos, Items.REDSTONE)
                    + loose(helper, Items.REDSTONE);
                helper.assertValueEqual(total, held + 16,
                    "redstone after clicking sixteen onto machine slot " + slot
                        + ", which already held " + held);
                // Only the player's copy is cleared; whatever the machine took stays where it is.
                player.getInventory().clearContent();
                view.setCarried(ItemStack.EMPTY);
            }
            helper.succeed();
        });
    }

    /**
     * Bay View feeding a bay that already has a link running on it, which is the shape of the
     * actual loop: put something in by hand, and the automation picks it up.
     *
     * <p>Written after walking it in {@code runClient} and briefly believing the mod had eaten two
     * items. It had not - they went into the racked chest and the link carried them out to its
     * target within the same second, which is <em>correct</em> and looks identical to a leak from
     * the screen. The census is what tells the two apart, and this test is what stops the next
     * person spending ten minutes on it.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "Items put into a bay by hand and carried straight out by its link are never lost.")
    public static void bayViewFeedsARunningLinkWithoutLosingAnything(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.CHEST, 1));
            connect(helper, workbay, 0, targetPos.above(), player);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 37));

            BayViewMenu view = bayView(2, player, workbay, 0);
            int playerSlot = view.machineSlots();
            for (int slot = view.machineSlots(); slot < view.slots.size(); slot++) {
                if (view.slots.get(slot).getItem().is(Items.IRON_INGOT)) {
                    playerSlot = slot;
                    break;
                }
            }
            int handSlot = playerSlot;
            helper.startSequence()
                .thenExecute(() -> view.clicked(handSlot, 0, ClickType.QUICK_MOVE, player))
                .thenWaitUntil(() -> {
                    if (inContainer(level, targetPos, Items.IRON_INGOT) <= 0) {
                        throw new GameTestAssertException("the link has not carried anything out of "
                            + "the bay Bay View just filled");
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    int total = onPlayer(player, Items.IRON_INGOT)
                        + inContainer(backshop, machinePos, Items.IRON_INGOT)
                        + inContainer(level, targetPos, Items.IRON_INGOT)
                        + carried(view, Items.IRON_INGOT)
                        + loose(helper, Items.IRON_INGOT);
                    helper.assertValueEqual(total, 37,
                        "iron ingots after Bay View fed a bay whose link was already running");
                })
                .thenSucceed();
        });
    }

    /**
     * The fluid half of Bay View, against a real Mekanism tank. One bucket of water goes in and
     * comes back out through the two container slots, and at every step the census is the same:
     * <b>a thousand millibuckets exist, in exactly one place</b> — either in a bucket or in the
     * tank, never in both, never in neither. The bucket itself is counted in every place it could
     * be: the player, the cursor, both fluid slots, and the floor.
     *
     * <p>Mekanism is the machine that makes this worth testing rather than a formality. Its
     * null-side handler reports the tank's contents perfectly and then silently refuses every
     * write, so a screen that reads and writes through the same resolved handler shows a tank, eats
     * a bucket of water and moves nothing. {@link BayViewMenu.Live#reach} is why this passes: the
     * read takes the null side, the write simulates and takes a face.
     *
     * <p>Closing the screen is the last step, because the fluid slots are the menu's own and a
     * container standing in one when the menu goes away is a container that has to come back.
     */
    @GameTest
    @TestHolder(description = "A bucket of water into a hosted Mekanism tank and back out is never copied or lost.")
    public static void bayViewMovesFluidBothWaysWithoutLosingAny(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(mekanismTank(helper), 1));

            BayViewMenu view = bayView(2, player, workbay, 0);
            // The read half, first: without a tank on the screen the rest of this proves nothing.
            if (view.state().tanks().isEmpty()) {
                helper.fail("Bay View sees no tanks on a hosted Mekanism tank, so there is nothing "
                    + "for the container slots to exchange with");
            }

            var water = net.minecraft.world.level.material.Fluids.WATER;
            // Millibuckets in existence: a full bucket carries a thousand, the tank holds what it
            // holds. Both fluid slots count, and so do the cursor and the floor.
            java.util.function.IntSupplier census = () ->
                inEveryPlace(helper, player, view, Items.WATER_BUCKET) * 1000
                    + inTanks(backshop, machinePos, water);
            java.util.function.IntSupplier buckets = () ->
                inEveryPlace(helper, player, view, Items.WATER_BUCKET)
                    + inEveryPlace(helper, player, view, Items.BUCKET);

            putInFluidSlot(view, 0, new ItemStack(Items.WATER_BUCKET));
            helper.assertValueEqual(census.getAsInt(), 1000, "millibuckets of water before the tick");

            view.tick();
            helper.assertValueEqual(census.getAsInt(), 1000, "millibuckets of water after filling "
                + "the hosted tank from the container slot");
            helper.assertValueEqual(buckets.getAsInt(), 1, "buckets in existence after the fill");
            if (inTanks(backshop, machinePos, water) <= 0) {
                helper.fail("the exchange emptied the bucket somewhere that is not the tank. That "
                    + "is the Mekanism read-only null-side handler: the write must resolve a face "
                    + "by simulating, not take whichever handler answers first.");
            }

            // And back out, which is the direction that can hand the player a full bucket while
            // leaving the tank full as well. The direction is set, not inferred: with the button
            // still on "into the machine" an empty bucket moves nothing, and every count below
            // would hold anyway - which is a test that passes without testing.
            ItemStack emptied = inFluidSlot(view, 1).copy();
            helper.assertValueEqual(emptied.getItem(), Items.BUCKET,
                "the item the fill left in the result slot");
            putInFluidSlot(view, 1, ItemStack.EMPTY);
            putInFluidSlot(view, 0, emptied);
            view.clickMenuButton(player, BayViewMenu.MODE_BUTTON);
            view.tick();
            helper.assertValueEqual(census.getAsInt(), 1000, "millibuckets of water after drawing "
                + "it back out of the hosted tank");
            helper.assertValueEqual(buckets.getAsInt(), 1, "buckets in existence after the draw");
            // The half that makes the two above mean something: the water really came back.
            helper.assertValueEqual(inTanks(backshop, machinePos, water), 0,
                "millibuckets left in the hosted tank after the draw");
            helper.assertValueEqual(inEveryPlace(helper, player, view, Items.WATER_BUCKET), 1,
                "buckets of water the draw handed back");

            view.removed(player);
            helper.assertValueEqual(census.getAsInt(), 1000,
                "millibuckets of water after the screen closed on a full container slot");
            helper.assertValueEqual(buckets.getAsInt(), 1,
                "buckets in existence after the screen closed on a full container slot");
            if (!inFluidSlot(view, 1).isEmpty()) {
                helper.fail("the result slot still holds a container after the screen closed, so "
                    + "closing it a second time would hand out a second one");
            }
            helper.succeed();
        });
    }

    /**
     * <b>The duplication trap this feature is built around.</b> Sixteen empty buckets in the slot
     * and one bucket of water in the tank: exactly one bucket may come out full. A handler that
     * reads the slot's whole stack and fills it makes sixteen buckets of water out of one, which is
     * why {@code FluidUtil.tryFillContainer} starts by copying one container off the stack and why
     * the exchange shrinks the input by exactly one to match.
     *
     * <p>Two ticks, not one. One tick proves nothing: the second is the one that would fill a
     * second bucket out of a tank that no longer has anything in it.
     */
    @GameTest
    @TestHolder(description = "A stack of empty buckets fills one bucket per tick and never more than the tank held.")
    public static void bayViewFillsOneContainerFromAStackNotTheWholeStack(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(mekanismTank(helper), 1));
            BayViewMenu view = bayView(2, player, workbay, 0);

            // Put the thousand millibuckets in the tank by the route the test above proves works.
            putInFluidSlot(view, 0, new ItemStack(Items.WATER_BUCKET));
            view.tick();
            putInFluidSlot(view, 1, ItemStack.EMPTY);
            var water = net.minecraft.world.level.material.Fluids.WATER;
            helper.assertValueEqual(inTanks(backshop, machinePos, water), 1000,
                "millibuckets in the hosted tank before the stack of empty buckets goes in");

            // Out of the machine now, which is the button rather than a guess about the bucket:
            // the direction is the player's to set, so the test presses what the player presses.
            view.clickMenuButton(player, BayViewMenu.MODE_BUTTON);
            helper.assertValueEqual(view.state().mode(), com.neryos.workbay.bus.BusConfig.Mode.EXTRACT,
                "the exchange direction after one press");

            putInFluidSlot(view, 0, new ItemStack(Items.BUCKET, 16));
            java.util.function.IntSupplier census = () ->
                inEveryPlace(helper, player, view, Items.WATER_BUCKET) * 1000
                    + inTanks(backshop, machinePos, water);
            helper.assertValueEqual(census.getAsInt(), 1000,
                "millibuckets of water before the stack of empty buckets is served");

            view.tick();
            helper.assertValueEqual(census.getAsInt(), 1000,
                "millibuckets of water after one tick with sixteen empty buckets waiting");
            helper.assertValueEqual(inEveryPlace(helper, player, view, Items.WATER_BUCKET), 1,
                "buckets of water made out of a tank that held exactly one bucket");
            helper.assertValueEqual(inEveryPlace(helper, player, view, Items.BUCKET), 15,
                "empty buckets left after one of the sixteen was filled");

            // The tank is empty now, so a second tick must do nothing at all.
            view.tick();
            helper.assertValueEqual(census.getAsInt(), 1000,
                "millibuckets of water after a second tick against an emptied tank");
            helper.assertValueEqual(inEveryPlace(helper, player, view, Items.WATER_BUCKET), 1,
                "buckets of water after a second tick against an emptied tank");
            helper.succeed();
        });
    }

    /**
     * A tank holding one fluid refuses a container of another, moves nothing either way, and
     * <b>says so</b>. The last part is the half worth a test: a refusal the player cannot see is
     * indistinguishable from a broken screen, and the line under the slots is the only thing that
     * tells them apart.
     */
    @GameTest
    @TestHolder(description = "A lava bucket against a water-filled hosted tank moves nothing and says why.")
    public static void bayViewSaysSoWhenTheTankHoldsADifferentFluid(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(mekanismTank(helper), 1));
            BayViewMenu view = bayView(2, player, workbay, 0);

            putInFluidSlot(view, 0, new ItemStack(Items.WATER_BUCKET));
            view.tick();
            putInFluidSlot(view, 1, ItemStack.EMPTY);
            var water = net.minecraft.world.level.material.Fluids.WATER;
            var lava = net.minecraft.world.level.material.Fluids.LAVA;
            helper.assertValueEqual(inTanks(backshop, machinePos, water), 1000,
                "millibuckets of water in the hosted tank before the lava arrives");

            // The record and the level must agree on the same tick the transfer happened. They did
            // not: the gauges were read before the exchange ran and showed last tick's tank.
            helper.assertValueEqual(view.state().tanks().get(0).contents().getFluid(), water,
                "what the screen's own record says the tank holds, one tick after it was filled");
            putInFluidSlot(view, 0, new ItemStack(Items.LAVA_BUCKET));
            view.tick();
            helper.assertValueEqual(inTanks(backshop, machinePos, lava), 0,
                "millibuckets of lava in a tank that already holds water");
            helper.assertValueEqual(inTanks(backshop, machinePos, water), 1000,
                "millibuckets of water still in the tank after lava was offered to it");
            helper.assertValueEqual(inEveryPlace(helper, player, view, Items.LAVA_BUCKET), 1,
                "lava buckets in existence after the tank refused one");
            helper.assertValueEqual(view.state().hint(), BayViewMenu.Hint.MIXED,
                "what the screen says after a tank holding water refused a bucket of lava");
            helper.succeed();
        });
    }

    /**
     * A machine that is gone must refuse a fluid exchange the same way it refuses an item one. The
     * failure this catches is the quiet one: a handler resolved before the eject, still accepting
     * the bucket, into a block entity nobody owns any more.
     */
    @GameTest
    @TestHolder(description = "A bucket in an ejected bay's fluid slot moves nothing.")
    public static void bayViewOverAnEjectedMachineRefusesFluidToo(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            player.getInventory().clearContent();
            WorkbayMenu bays = menuFor(workbay, player);
            rack(bays, player, 0, new ItemStack(mekanismTank(helper), 1));

            BayViewMenu view = bayView(2, player, workbay, 0);
            putInFluidSlot(view, 0, new ItemStack(Items.WATER_BUCKET));
            bays.act(WorkbayAction.EJECT, 0, Optional.empty());

            view.tick();
            helper.assertValueEqual(count(inFluidSlot(view, 0), Items.WATER_BUCKET), 1,
                "water buckets still in the fluid slot after a tick against a bay with no machine");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ links

    /**
     * A link whose target is destroyed while goods are moving. ROADMAP §2 names it; the answer is
     * that {@link com.neryos.workbay.bus.BusEndpoint} resolves through a capability cache that goes
     * null rather than stale, so the move never half-happens.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "Destroying a link's target mid-transfer loses nothing from the source.")
    public static void destroyingALinkTargetMidTransferLosesNothing(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.CHEST, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.IRON_INGOT, 64));
            connect(helper, workbay, 0, targetPos.above(), player);

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (inContainer(level, targetPos, Items.IRON_INGOT) <= 0) {
                        throw new GameTestAssertException("nothing has moved through the link yet");
                    }
                })
                .thenExecute(() -> {
                    // Mid-flight: some ingots are here, some are there. Take the target away.
                    int arrived = inContainer(level, targetPos, Items.IRON_INGOT);
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    test.framework().logger().info("{} ingots had arrived when the chest went",
                        arrived);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    // One loose() call: the chest spilled what had arrived, and the census box
                    // covers the whole structure.
                    int total = inContainer(backshop, machinePos, Items.IRON_INGOT)
                        + loose(helper, Items.IRON_INGOT);
                    helper.assertValueEqual(total, 64,
                        "iron ingots after the link's target was destroyed mid-transfer");
                })
                .thenSucceed();
        });
    }

    /**
     * Breaking the Connector while goods are in flight through it. The link goes with the block
     * (SPEC.md §2), so the question is whether the tick that removed it can also have been halfway
     * through a move.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "Breaking a Connector while its link is running loses nothing.")
    public static void breakingAConnectorMidFlightLosesNothing(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            BlockPos connectorPos = targetPos.above();
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(Blocks.CHEST, 1));
            put(backshop, machinePos, 0, new ItemStack(Items.IRON_INGOT, 64));
            connect(helper, workbay, 0, connectorPos, player);

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (inContainer(level, targetPos, Items.IRON_INGOT) <= 0) {
                        throw new GameTestAssertException("nothing has moved through the link yet");
                    }
                })
                .thenExecute(() -> level.setBlock(connectorPos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL))
                .thenIdle(40)
                .thenExecute(() -> {
                    int total = inContainer(backshop, machinePos, Items.IRON_INGOT)
                        + inContainer(level, targetPos, Items.IRON_INGOT)
                        + loose(helper, Items.IRON_INGOT);
                    helper.assertValueEqual(total, 64,
                        "iron ingots after the Connector was broken mid-transfer");
                })
                .thenSucceed();
        });
    }

    /**
     * The skim's own conservation law, which is the one piece of this mod that <b>destroys</b>
     * items on purpose (SPEC.md §3: they become a balance, and the Assay has no faces for a buffer
     * to be reachable through). So the sum that must hold is not "nothing went missing" but
     * <b>what left equals what arrived plus what the Assay is holding or has already banked</b>.
     * Without this test the skim is indistinguishable from a leak.
     */
    @GameTest(timeoutTicks = 800)
    @TestHolder(description = "Every item the skim takes is banked: what left equals what arrived plus what the Assay holds.")
    public static void theSkimBanksEverythingItTakes(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos targetPos = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());

            player.getInventory().clearContent();
            WorkbayMenu menu = menuFor(workbay, player);
            rack(menu, player, 0, new ItemStack(WBBlocks.ASSAY.get()));
            rack(menu, player, 1, new ItemStack(Blocks.CHEST, 1));
            BlockPos sourcePos = BayGeometry.machinePos(record.bayColumn(), 1);
            put(backshop, sourcePos, 0, new ItemStack(Items.IRON_INGOT, 64));
            put(backshop, sourcePos, 1, new ItemStack(Items.IRON_INGOT, 64));
            connect(helper, workbay, 1, targetPos.above(), player);
            for (int step = 0; step < AssayBlock.MAX_RATE / AssayBlock.RATE_STEP; step++) {
                menu.act(WorkbayAction.SET_SKIM, 0, Optional.empty());
            }

            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (inContainer(backshop, sourcePos, Items.IRON_INGOT) > 0) {
                        throw new GameTestAssertException("the bay still holds "
                            + inContainer(backshop, sourcePos, Items.IRON_INGOT) + " ingots");
                    }
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    WorkbayRecord now = workbay.record().orElseThrow();
                    int arrived = inContainer(level, targetPos, Items.IRON_INGOT);
                    int held = now.assay().skimmed();
                    int banked = now.assay().levy() * AssayBlock.ITEMS_PER_LEVY;
                    if (held + banked <= 0) {
                        helper.fail("the skim was at " + AssayBlock.MAX_RATE + "% and the Assay has "
                            + "nothing to show for it, so nothing was actually taken");
                    }
                    helper.assertValueEqual(arrived + held + banked, 128,
                        "ingots that arrived plus ingots the Assay is holding or has banked");
                })
                .thenSucceed();
        });
    }

    /**
     * The energy half, end to end, with a real Mekanism machine at both ends: a charged Basic
     * Energy Cube standing in the world, a link pulling from it, and an empty Basic Energy Cube
     * racked in a bay. <b>Nothing had ever pushed FE through a link into a hosted machine</b> — the
     * energy path was proven only at the read end, where the bays screen showed a cube's stored
     * figure — so this is the half that was taken on trust.
     *
     * <p>Counted rather than asserted, like every other test here: energy is a resource, and a
     * transfer that hands the destination more than the source lost is the same bug as a duplicated
     * item. The tolerance is not slack — Mekanism stores joules and converts on every FE call, so a
     * few units round away — but it only ever forgives a <em>loss</em>. A gain of one FE fails.
     */
    @GameTest(timeoutTicks = 600)
    @TestHolder(description = "FE pushed through a link into a racked Mekanism Energy Cube arrives, and none is created.")
    public static void energyPushedIntoARackedEnergyCubeIsNeverCreated(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(5, 5, 5));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            BlockPos workbayPos = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos sourcePos = helper.absolutePos(new BlockPos(4, 1, 4));

            Block cube = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("mekanism:basic_energy_cube"));
            if (cube == Blocks.AIR) {
                helper.fail("mekanism:basic_energy_cube is not registered. This test is about a real "
                    + "mod's energy handler, so a missing partner mod is a failure, never a skip.");
            }

            // The source, in the world, placed the way a player would and then charged by hand.
            BlockState cubeState = cube.defaultBlockState();
            level.setBlock(sourcePos, cubeState, Block.UPDATE_ALL);
            net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, player, sourcePos,
                new ItemStack(cube));
            if (level.getBlockEntity(sourcePos) != null) {
                level.getBlockEntity(sourcePos).applyComponentsFromItemStack(new ItemStack(cube));
            }
            cube.setPlacedBy(level, sourcePos, cubeState, player, new ItemStack(cube));
            level.invalidateCapabilities(sourcePos);

            int charged = 0;
            for (Direction side : Direction.values()) {
                var store = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    sourcePos, side);
                if (store != null) {
                    charged = store.receiveEnergy(200_000, false);
                    if (charged > 0) {
                        break;
                    }
                }
            }
            if (charged <= 0) {
                helper.fail("could not charge the source Energy Cube through any face, so there is "
                    + "nothing for the link to carry");
            }

            WorkbayBlockEntity workbay = placeWorkbay(helper, workbayPos, player);
            WorkbayRecord record = workbay.record().orElseThrow();
            ServerLevel backshop = backshop(helper);
            WorkbayTickets.force(backshop, record.id(), record.bayColumn());
            BlockPos machinePos = BayGeometry.machinePos(record.bayColumn(), 0);

            player.getInventory().clearContent();
            rack(menuFor(workbay, player), player, 0, new ItemStack(cube, 1));

            // EXTRACT: the target is the source and the hosted machine is the destination, which is
            // the direction a player uses to charge something they have put away.
            BusConfig link = connect(helper, workbay, 0, sourcePos.above(), player);
            workbay.addBus(link.withResource(BusConfig.Resource.ENERGY)
                .withMode(BusConfig.Mode.EXTRACT).withRate(20_000).withSpeed(10));

            int before = inEnergy(level, sourcePos) + inEnergy(backshop, machinePos);
            helper.startSequence()
                .thenWaitUntil(() -> {
                    if (inEnergy(backshop, machinePos) <= 0) {
                        throw new GameTestAssertException("no FE has reached the racked Energy Cube "
                            + "yet; the source holds " + inEnergy(level, sourcePos)
                            + " and the link reads " + workbay.busStatus(link.id()));
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    int arrived = inEnergy(backshop, machinePos);
                    int left = inEnergy(level, sourcePos);
                    test.framework().logger().info("{} FE arrived, {} left at the source, {} before",
                        arrived, left, before);
                    if (arrived + left > before) {
                        helper.fail("the link created energy: " + before + " FE existed, "
                            + (arrived + left) + " FE exists now");
                    }
                    // A generous floor, because the conversion is Mekanism's and rounds per call.
                    // It is here to catch energy vanishing wholesale, not to police the last unit.
                    if (arrived + left < before - before / 100) {
                        helper.fail("the link lost energy: " + before + " FE existed, "
                            + (arrived + left) + " FE exists now");
                    }
                })
                .thenSucceed();
        });
    }

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
        BusConfig link = links.get(links.size() - 1).withEnabled(true).withRate(16).withSpeed(10);
        workbay.addBus(link);
        return link;
    }
}
