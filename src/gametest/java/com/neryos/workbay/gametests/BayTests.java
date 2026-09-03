package com.neryos.workbay.gametests;

import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.BayBuilder;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.UUID;

/**
 * SPEC.md §8. The bay's shape is the answer to why bays are not bare 1x1x1 holes, so it is worth
 * asserting rather than eyeballing: six Ports against the machine, a solid shell around them, and
 * enough room between bays that they never touch.
 */
@ForEachTest(groups = "bay")
public class BayTests {

    private static final UUID SHAPE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000ba91");
    private static final ChunkPos SHAPE_COLUMN = new ChunkPos(3072, 0);

    @GameTest(timeoutTicks = 600)
    @TestHolder(description = "A generated bay is a 5x5x5 shell with six Ports around an empty middle.")
    public static void bayIsBuiltToShape(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            helper.assertNotNull(backshop, "getLevel(workbay:backshop) returned null");
            WorkbayTickets.force(backshop, SHAPE_OWNER, SHAPE_COLUMN);

            BlockPos machine = BayBuilder.ensure(backshop, SHAPE_COLUMN, 0);
            helper.assertValueEqual(machine, BayGeometry.machinePos(SHAPE_COLUMN, 0), "machine position");

            if (!backshop.getBlockState(machine).isAir()) {
                helper.fail("the machine slot is not empty after building a bay: "
                    + backshop.getBlockState(machine));
                return;
            }

            // Every side of the machine must be a Port. This is the whole point of the shape: a
            // pushing machine needs somewhere to push and a redstone-gated one needs a neighbour.
            for (Direction face : Direction.values()) {
                BlockPos port = BayGeometry.portPos(SHAPE_COLUMN, 0, face);
                if (!backshop.getBlockState(port).is(WBBlocks.PORT.get())) {
                    helper.fail("the " + face + " face of the bay is "
                        + backshop.getBlockState(port) + ", not a Port");
                    return;
                }
            }

            // The shell has to be closed, or a machine's output leaves the bay.
            BlockPos origin = BayGeometry.shellOrigin(SHAPE_COLUMN, 0);
            int walls = 0;
            for (int x = 0; x < BayGeometry.SHELL; x++) {
                for (int y = 0; y < BayGeometry.SHELL; y++) {
                    for (int z = 0; z < BayGeometry.SHELL; z++) {
                        boolean edge = x == 0 || y == 0 || z == 0
                            || x == BayGeometry.SHELL - 1 || y == BayGeometry.SHELL - 1
                            || z == BayGeometry.SHELL - 1;
                        if (!edge) {
                            continue;
                        }
                        BlockPos pos = origin.offset(x, y, z);
                        if (backshop.getBlockState(pos).isAir()) {
                            helper.fail("the bay shell has a hole at " + pos);
                            return;
                        }
                        walls++;
                    }
                }
            }
            helper.assertValueEqual(walls, 5 * 5 * 5 - 3 * 3 * 3, "blocks in the shell");

            if (!BayBuilder.isBuilt(backshop, SHAPE_COLUMN, 0)) {
                helper.fail("isBuilt disagrees with the bay this test just checked block by block");
            }
            WorkbayTickets.release(backshop, SHAPE_OWNER, SHAPE_COLUMN);
            helper.succeed();
        });
    }

    /**
     * Building a bay a second time must not disturb what is standing in it. This is called on every
     * insert, so a rebuild would replace a hosted machine with air and lose it silently.
     */
    @GameTest(timeoutTicks = 600)
    @TestHolder(description = "Building a bay that already exists leaves the machine in it alone.")
    public static void rebuildingABayDoesNotClearIt(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
            helper.assertNotNull(backshop, "getLevel(workbay:backshop) returned null");
            ChunkPos column = new ChunkPos(3072, 64);
            UUID owner = UUID.fromString("00000000-0000-0000-0000-00000000ba92");
            WorkbayTickets.force(backshop, owner, column);

            BlockPos machine = BayBuilder.ensure(backshop, column, 1);
            backshop.setBlock(machine, Blocks.FURNACE.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

            BayBuilder.ensure(backshop, column, 1);
            if (!backshop.getBlockState(machine).is(Blocks.FURNACE)) {
                helper.fail("rebuilding the bay replaced the hosted machine with "
                    + backshop.getBlockState(machine));
            }

            backshop.setBlock(machine, Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
            WorkbayTickets.release(backshop, owner, column);
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder(description = "Eight bays fit the dimension without their shells touching.")
    public static void eightBaysFitWithoutTouching(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ChunkPos column = new ChunkPos(0, 0);
            for (int bay = 0; bay < BayGeometry.MAX_BAYS; bay++) {
                int floor = BayGeometry.shellOrigin(column, bay).getY();
                if (floor < WorkbayDimensions.MIN_Y) {
                    helper.fail("bay " + bay + " starts below the dimension at y " + floor);
                    return;
                }
                if (BayGeometry.topY(bay) >= WorkbayDimensions.MIN_Y + WorkbayDimensions.HEIGHT) {
                    helper.fail("bay " + bay + " reaches y " + BayGeometry.topY(bay)
                        + ", past the top of the dimension");
                    return;
                }
                if (bay > 0 && floor <= BayGeometry.topY(bay - 1)) {
                    helper.fail("bay " + bay + " starts at y " + floor + " but bay " + (bay - 1)
                        + " ends at y " + BayGeometry.topY(bay - 1) + " - the shells overlap");
                    return;
                }
            }

            // The shell must stay inside one chunk, or a bay column costs more than one chunk to
            // keep loaded and the whole mirroring model in §12 stops being free.
            BlockPos origin = BayGeometry.shellOrigin(column, 0);
            if (origin.getX() < column.getMinBlockX()
                || origin.getX() + BayGeometry.SHELL - 1 > column.getMaxBlockX()
                || origin.getZ() < column.getMinBlockZ()
                || origin.getZ() + BayGeometry.SHELL - 1 > column.getMaxBlockZ()) {
                helper.fail("a bay shell at " + origin + " leaves chunk " + column);
                return;
            }
            helper.succeed();
        });
    }
}
