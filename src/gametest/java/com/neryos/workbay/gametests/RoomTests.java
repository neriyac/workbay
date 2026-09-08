package com.neryos.workbay.gametests;

import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.RoomBuilder;
import com.neryos.workbay.world.RoomGeometry;
import com.neryos.workbay.world.RoomRecord;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.RoomVisit;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * A room is a place a player stands in. SPEC.md §8.
 *
 * <p>The thing that can go wrong here is the opposite of a bay's: a bay visitor with no screen open
 * is put out on the next tick, and applying that rule to a room throws the first player to walk
 * into one straight back out. So the first test below stands in a room and then <em>waits</em>,
 * which is the only shape that catches it.
 */
@ForEachTest(groups = "room")
public class RoomTests {

    /** A Workbay bound to a mock player, with a Room Frame of the given tier granted. */
    private record Site(GameTestPlayer player, ServerLevel backshop, WorkbayRecord record,
        Vec3 from, float yRot, float xRot, BlockPos workbayPos) {}

    private static Site site(ExtendedGameTestHelper helper, int tier) {
        return site(helper, tier, 0, 0);
    }

    private static Site site(ExtendedGameTestHelper helper, int tier, int annexPlates, int anchors) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(pos, WBBlocks.WORKBAY.get().defaultBlockState(), Block.UPDATE_ALL);
        WBBlocks.WORKBAY.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
            new ItemStack(WBBlocks.WORKBAY.get()));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        WorkbayBlockEntity workbay = (WorkbayBlockEntity) level.getBlockEntity(pos);
        WorkbayRecord record = workbay.record().orElseThrow();
        WorkbayRecord.Upgrades up = record.upgrades();
        record = record.withUpgrades(new WorkbayRecord.Upgrades(up.expansionPlates(), up.resonators(),
            anchors, annexPlates, tier, up.multichannel(), up.impellers()));
        RoomRegistry.get(level.getServer()).put(record);

        return new Site(player, level.getServer().getLevel(WorkbayDimensions.BACKSHOP), record,
            player.position(), player.getYRot(), player.getXRot(), pos);
    }

    private static RoomRecord room(ExtendedGameTestHelper helper, Site site) {
        RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
        return registry.roomsOf(registry.byId(site.record().id()).orElseThrow()).getFirst();
    }

    // ------------------------------------------------------------------ tests

    /**
     * The whole of step 3: a player is in a room, standing on its floor, and is <b>still there</b>
     * several ticks later.
     *
     * <p>The waiting is the test. SPEC.md §5's standing rule — nobody is in the Backshop without an
     * open screen — sends a player home on the next tick, and a room's occupant has no screen by
     * design. Deleting the room case from {@code BayVisit#tick} puts this straight back in the red.
     */
    @GameTest
    @TestHolder(description = "A player enters a room, stands on its floor, and is still in it ticks later.")
    public static void aRoomIsAPlaceAPlayerCanStandIn(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0),
                "entering room 1 was refused");
            helper.assertTrue(site.player().level().dimension().equals(WorkbayDimensions.BACKSHOP),
                "the player is not in the Backshop after entering a room");

            RoomRecord room = room(helper, site);
            helper.assertTrue(room.builtTier() == 1,
                "the room records tier " + room.builtTier() + " after being built at tier 1");
            helper.assertTrue(RoomGeometry.inside(site.player().blockPosition(), room.region(), 1),
                "the player is at " + site.player().blockPosition() + ", outside the room's interior");

            BlockPos feet = site.player().blockPosition();
            helper.assertTrue(site.backshop().getBlockState(feet.below()).is(Blocks.BEDROCK),
                "the player is standing on " + site.backshop().getBlockState(feet.below())
                    + " instead of the room's bedrock floor");
            helper.assertTrue(site.backshop().getBlockState(feet).isAir()
                && site.backshop().getBlockState(feet.above()).isAir(),
                "the entry pad is not two blocks of air");
            helper.assertTrue(site.backshop().getBlockState(RoomGeometry.exitPos(room.region()))
                .is(WBBlocks.EXIT.get()), "no Exit block on the entry pad");

            // Sixteen ticks: eight times longer than the bay rule's grace, so a room that is going
            // to be evicted has been evicted.
            helper.startSequence()
                .thenIdle(16)
                .thenExecute(() -> {
                    helper.assertTrue(site.player().level().dimension().equals(WorkbayDimensions.BACKSHOP),
                        "the player was thrown out of their room after standing in it for 16 ticks");
                    helper.assertTrue(RoomVisit.isInside(site.player()),
                        "the player is no longer recorded as being in a room");
                })
                .thenSucceed();
        });
    }

    /** The Exit block is the way out, and it puts the player back exactly where they were. */
    @GameTest
    @TestHolder(description = "Right-clicking the Exit block returns the player to the spot and facing they left.")
    public static void theExitBlockPutsThePlayerBackWhereTheyWere(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            BlockPos exit = RoomGeometry.exitPos(room(helper, site).region());

            // The click a player makes, not a call to leave(): the block is the feature.
            site.backshop().getBlockState(exit).useWithoutItem(site.backshop(), site.player(),
                new BlockHitResult(Vec3.atCenterOf(exit), Direction.UP, exit, false));

            helper.assertTrue(site.player().level().dimension().equals(helper.getLevel().dimension()),
                "the player is in " + site.player().level().dimension().location() + ", not back home");
            helper.assertTrue(site.player().position().distanceTo(site.from()) < 0.001,
                "the player came back to " + site.player().position() + " instead of " + site.from());
            helper.assertTrue(site.player().getYRot() == site.yRot()
                && site.player().getXRot() == site.xRot(), "the player came back facing the wrong way");
            helper.assertFalse(RoomVisit.isInside(site.player()),
                "the player is still recorded as being in a room after leaving");
            helper.succeed();
        });
    }

    /**
     * A larger Room Frame grows the room outward and never relocates it. The chest is the point:
     * SPEC.md §0 says a player must never lose a built room to an upgrade.
     */
    @GameTest
    @TestHolder(description = "A bigger Room Frame grows the room outward, keeping what was built in it.")
    public static void aRoomGrowsOutwardWithoutLosingWhatIsInIt(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            int region = room.region();
            BlockPos origin = RoomGeometry.origin(region);

            // A chest against the far wall of the small room, which is where growth would sweep.
            BlockPos chest = origin.offset(RoomGeometry.interior(1), 1, RoomGeometry.interior(1));
            site.backshop().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos oldWall = origin.offset(RoomGeometry.footprint(1) - 1, 1, 1);
            helper.assertTrue(site.backshop().getBlockState(oldWall).is(Blocks.BEDROCK),
                "the tier 1 wall is not bedrock before the upgrade");

            RoomVisit.leave(site.player());
            RoomBuilder.ensure(site.backshop(), room, 2);

            helper.assertTrue(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "the chest was destroyed when the room grew");
            helper.assertTrue(site.backshop().getBlockState(oldWall).isAir(),
                "the old wall is still standing inside the bigger room");
            BlockPos newWall = origin.offset(RoomGeometry.footprint(2) - 1, 1, 1);
            helper.assertTrue(site.backshop().getBlockState(newWall).is(Blocks.BEDROCK),
                "the tier 2 room has no wall at its own footprint");
            helper.assertTrue(site.backshop().getBlockState(RoomGeometry.exitPos(region))
                .is(WBBlocks.EXIT.get()), "the Exit block moved when the room grew");
            helper.succeed();
        });
    }

    /**
     * Anchoring is per room and capped, which is the whole answer to "what does a room cost a
     * server". An Anchor that lit every room the network owns would buy up to thirty-six ticking
     * chunks with one upgrade and no second thought.
     */
    @GameTest
    @TestHolder(description = "Anchoring is switched on per room and refused past the network's cap.")
    public static void anchoringIsPerRoomAndCapped(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1, 2, 1);
            // Two rooms, both opened, so both have a shell and a size to charge for.
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "room 1 refused");
            RoomVisit.leave(site.player());
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            WorkbayRecord current = registry.byId(site.record().id()).orElseThrow();
            helper.assertTrue(RoomVisit.enter(site.player(), current, 1), "room 2 refused");
            RoomVisit.leave(site.player());

            com.neryos.workbay.menu.WorkbayMenu menu = menu(helper, site);
            menu.act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 0,
                java.util.Optional.empty());
            helper.assertTrue(rooms(helper, site).get(0).anchored(),
                "switching room 1's anchor on did nothing");

            // maxAnchoredRoomsPerNetwork defaults to 1: the second must be refused, not silently
            // taken, because the number is what a host is paying.
            menu.act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 1,
                java.util.Optional.empty());
            helper.assertFalse(rooms(helper, site).get(1).anchored(),
                "a second room anchored past the cap of "
                    + com.neryos.workbay.config.WorkbayConfig.SERVER.maxAnchoredRoomsPerNetwork.get());

            // And the cap is a cap, not a lock: switching the first off frees the slot.
            menu.act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 0,
                java.util.Optional.empty());
            menu.act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 1,
                java.util.Optional.empty());
            helper.assertFalse(rooms(helper, site).get(0).anchored(),
                "room 1 is still anchored after being switched off");
            helper.assertTrue(rooms(helper, site).get(1).anchored(),
                "room 2 could not be anchored after room 1 was switched off");
            helper.succeed();
        });
    }

    private static java.util.List<RoomRecord> rooms(ExtendedGameTestHelper helper, Site site) {
        RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
        return registry.roomsOf(registry.byId(site.record().id()).orElseThrow());
    }

    private static com.neryos.workbay.menu.WorkbayMenu menu(ExtendedGameTestHelper helper, Site site) {
        WorkbayBlockEntity workbay = (WorkbayBlockEntity) helper.getLevel()
            .getBlockEntity(site.workbayPos());
        return new com.neryos.workbay.menu.WorkbayMenu(1, site.player().getInventory(), workbay,
            com.neryos.workbay.menu.WorkbayMenu.build(workbay, site.player(), 0));
    }

    /**
     * The number on the room screen has to be the number of tickets, so the footprint has to be a
     * whole number of chunks from a chunk-aligned corner. That is the entire reason the tiers are
     * 14/30/46 and not 9/17/33.
     */
    @GameTest
    @TestHolder(description = "Every room tier is an exact square of chunks, and no two regions overlap.")
    public static void everyRoomTierIsAWholeNumberOfChunks(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            int[] expected = {1, 4, 9};
            for (int tier = 1; tier <= RoomGeometry.MAX_TIER; tier++) {
                int footprint = RoomGeometry.footprint(tier);
                helper.assertTrue(footprint % 16 == 0,
                    "tier " + tier + "'s footprint is " + footprint + ", not a whole number of chunks");
                helper.assertTrue(RoomGeometry.chunkCost(tier) == expected[tier - 1],
                    "tier " + tier + " costs " + RoomGeometry.chunkCost(tier) + " chunks, not "
                        + expected[tier - 1]);
                helper.assertTrue(RoomGeometry.chunks(0, tier).size() == expected[tier - 1],
                    "tier " + tier + " lists the wrong number of chunks to force");
            }
            BlockPos first = RoomGeometry.origin(0);
            BlockPos second = RoomGeometry.origin(1);
            helper.assertTrue(first.getX() % 16 == 0 && first.getZ() % 16 == 0,
                "region 0's origin " + first + " is not chunk-aligned");
            helper.assertTrue(second.getX() - first.getX()
                >= RoomGeometry.footprint(RoomGeometry.MAX_TIER),
                "two regions are closer together than the biggest room is wide");
            helper.succeed();
        });
    }

}
