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
            helper.assertTrue(site.backshop().getBlockState(feet.below())
                    .is(WBBlocks.ROOM_WALL.get()),
                "the player is standing on " + site.backshop().getBlockState(feet.below())
                    + " instead of the room's own floor");
            helper.assertTrue(site.backshop().getBlockState(feet).isAir()
                && site.backshop().getBlockState(feet.above()).isAir(),
                "the entry pad is not two blocks of air");
            RoomGeometry.doors(room.region(), room.builtTier()).forEach((at, part) ->
                helper.assertTrue(site.backshop().getBlockState(at)
                        .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART) == part,
                    "the wall at " + at + " is not the " + part + " of a door"));

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

    /**
     * <b>The door is the way out, and nothing else in the shell is.</b>
     *
     * <p>Three shapes were tried and this test has asserted two of them, so the assertion is
     * written both ways round on purpose. A single Exit block could be lost and had to be walked
     * back to across a 46-block room. Every block of the shell opening the screen fixed that and
     * broke something else: a room is a place you build in, and a wall that opens a menu whenever
     * you right-click near it is a wall you cannot work against. Four doors, one dead centre on
     * each wall, unbreakable — near enough to reach and quiet everywhere else.
     *
     * <p>The plain wall is clicked <em>first</em>, so a pass on the door cannot be "the menu was
     * already open", and the wall's part is asserted before it is clicked, so the negative half
     * cannot go vacuous by aiming at a door by accident.
     */
    @GameTest
    @TestHolder(description = "A door opens the way out and returns the player where they left; a plain wall does nothing.")
    public static void onlyADoorIsTheWayOut(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            BlockPos origin = RoomGeometry.origin(room.region());
            // High on the west wall: shell, and nowhere near the door drawn at y 1-2 mid-wall.
            BlockPos plain = origin.offset(0, 6, 3);
            BlockPos floor = origin.offset(4, 0, 4);
            BlockPos door = RoomGeometry.doors(room.region(), room.builtTier())
                .keySet().iterator().next();
            helper.assertTrue(site.backshop().getBlockState(plain)
                    .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART)
                    == com.neryos.workbay.content.room.RoomPart.WALL,
                "the block under test is not a plain wall, so this test proves nothing");

            // Both of the pieces that must stay quiet first, so a pass on the door cannot be
            // "the menu was already open".
            click(site, floor);
            helper.assertFalse(site.player().containerMenu
                    instanceof com.neryos.workbay.menu.RoomDoorMenu,
                "the floor opened the way out; building on it would fight the door");
            click(site, plain);
            helper.assertFalse(site.player().containerMenu
                    instanceof com.neryos.workbay.menu.RoomDoorMenu,
                "a plain wall opened the way out; a room is a place you build in, and a wall that "
                    + "opens a menu is a wall you cannot work against");

            click(site, door);
            helper.assertTrue(site.player().containerMenu
                    instanceof com.neryos.workbay.menu.RoomDoorMenu,
                "clicking a door opened " + site.player().containerMenu.getClass().getSimpleName()
                    + " instead of the way out");

            ((com.neryos.workbay.menu.RoomDoorMenu) site.player().containerMenu)
                .act(com.neryos.workbay.menu.WorkbayAction.LEAVE_ROOM, 0, site.player());

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

    /** The right-click a player makes, not a call to the menu: the block is the feature. */
    private static void click(Site site, BlockPos at) {
        site.backshop().getBlockState(at).useWithoutItem(site.backshop(), site.player(),
            new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
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
            helper.assertTrue(site.backshop().getBlockState(oldWall).is(WBBlocks.ROOM_WALL.get()),
                "the tier 1 wall is not shell before the upgrade");

            RoomVisit.leave(site.player());
            RoomBuilder.ensure(site.backshop(), room, 2);

            helper.assertTrue(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "the chest was destroyed when the room grew");
            helper.assertTrue(site.backshop().getBlockState(oldWall).isAir(),
                "the old wall is still standing inside the bigger room");
            BlockPos newWall = origin.offset(RoomGeometry.footprint(2) - 1, 1, 1);
            helper.assertTrue(site.backshop().getBlockState(newWall).is(WBBlocks.ROOM_WALL.get()),
                "the tier 2 room has no wall at its own footprint");
            RoomGeometry.doors(region, 2).forEach((at, part) ->
                helper.assertTrue(site.backshop().getBlockState(at)
                        .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART) == part,
                    "the grown room has no door panel at " + at));
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

    /**
     * The half of the room design a player actually asked for: grass or snow when a machine needs
     * it. What a machine asks is the <b>biome</b>, so the assertion below is not "the record says
     * snowy_plains" but "the level answers cold enough to snow at a block inside the room" -- and
     * it checks every chunk, because a room is one, four or nine of them and writing only the first
     * would look right from the entry pad and be wrong three walls away.
     *
     * <p>It grows the room afterwards for the same reason: tier 2 reaches chunks tier 1 never
     * wrote, and they would otherwise carry whatever the Backshop generates.
     */
    @GameTest
    @TestHolder(description = "A room's biome is written over every one of its chunks, and survives growth.")
    public static void aRoomsBiomeReachesEveryChunkAndSurvivesGrowth(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomVisit.leave(site.player());
            RoomRecord room = room(helper, site);
            BlockPos inside = RoomGeometry.origin(room.region()).offset(2, 2, 2);

            // Plains by default, and the assertion that keeps the next one from being vacuous: a
            // room that was already cold would pass the whole test without anything being written.
            helper.assertTrue(!site.backshop().getBiome(inside).value().coldEnoughToSnow(inside),
                "a fresh room is already cold enough to snow, so the test below proves nothing");

            com.neryos.workbay.menu.WorkbayMenu menu = menu(helper, site);
            menu.act(com.neryos.workbay.menu.WorkbayAction.CYCLE_ROOM_BIOME, 0,
                java.util.Optional.empty());
            RoomRecord cold = room(helper, site);
            helper.assertTrue(!cold.effectiveBiome().equals(net.minecraft.world.level.biome.Biomes.PLAINS),
                "cycling the biome left the room on plains");
            helper.assertTrue(site.backshop().getBiome(inside).value().coldEnoughToSnow(inside),
                "the room reads " + site.backshop().getBiome(inside).value().getBaseTemperature()
                    + " after being switched to " + cold.effectiveBiome().location());

            for (net.minecraft.world.level.ChunkPos pos : RoomGeometry.chunks(cold.region(), 1)) {
                BlockPos middle = new BlockPos(pos.getMiddleBlockX(), 2, pos.getMiddleBlockZ());
                helper.assertTrue(site.backshop().getBiome(middle).value().coldEnoughToSnow(middle),
                    "chunk " + pos + " of the room kept its old biome");
            }

            RoomBuilder.ensure(site.backshop(), cold, 2);
            for (net.minecraft.world.level.ChunkPos pos : RoomGeometry.chunks(cold.region(), 2)) {
                BlockPos middle = new BlockPos(pos.getMiddleBlockX(), 2, pos.getMiddleBlockZ());
                helper.assertTrue(site.backshop().getBiome(middle).value().coldEnoughToSnow(middle),
                    "chunk " + pos + " was added by growth and never got the room's biome");
            }
            helper.succeed();
        });
    }

    /**
     * The one failure a room must never have is "sealed in with no way out", and -1 hardness does
     * not answer it: hardness stops a pick, not a creative click, which breaks bedrock too. Every
     * block of the shell refuses the destroy, so there is no single block to lose.
     */
    @GameTest
    @TestHolder(description = "A room's shell cannot be broken, in creative either.")
    public static void aRoomCanNeverBeSealedWithThePlayerInside(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomVisit.leave(site.player());
            RoomRecord room = room(helper, site);
            BlockPos door = RoomGeometry.doors(room.region(), room.builtTier())
                .keySet().iterator().next();
            BlockPos floor = RoomGeometry.origin(room.region()).offset(3, 0, 3);

            // The creative path, which is the one that broke bedrock: ServerPlayerGameMode.
            site.player().setGameMode(GameType.CREATIVE);
            for (BlockPos at : java.util.List.of(door, floor)) {
                site.player().gameMode.destroyBlock(at);
                helper.assertTrue(site.backshop().getBlockState(at).is(WBBlocks.ROOM_WALL.get()),
                    "a creative click destroyed the shell at " + at);
            }

            // And a room whose shell is wrong -- an older save built out of bedrock, say -- is put
            // right by the visit that finds it, because that is the visit somebody is trying to end.
            site.backshop().setBlock(door, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            helper.assertTrue(RoomVisit.enter(site.player(),
                registry.byId(site.record().id()).orElseThrow(), 0), "re-entering was refused");
            RoomVisit.leave(site.player());
            helper.assertTrue(site.backshop().getBlockState(door).is(WBBlocks.ROOM_WALL.get())
                    && site.backshop().getBlockState(door)
                        .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART)
                        != com.neryos.workbay.content.room.RoomPart.WALL,
                "a shell left with a bedrock hole in it was not put right on the next visit");
            helper.succeed();
        });
    }

    /**
     * The door is <b>centred</b>, and that is why it is two blocks wide: a wall is a whole number
     * of chunks across and sixteen has no middle block, so a one-block doorway sits off-centre by
     * half a block. The assertion is the symmetry, because that is the thing that would silently
     * break if the footprints ever changed.
     */
    @GameTest
    @TestHolder(description = "Each wall's door is two blocks wide and exactly centred.")
    public static void everyDoorIsCentredOnItsWall(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            for (int tier = 1; tier <= RoomGeometry.MAX_TIER; tier++) {
                int side = RoomGeometry.footprint(tier);
                var doors = RoomGeometry.doors(0, tier);
                helper.assertTrue(doors.size() == 16,
                    "tier " + tier + " has " + doors.size() + " door blocks, not four 2x2 doors");
                BlockPos origin = RoomGeometry.origin(0);
                for (BlockPos at : doors.keySet()) {
                    int dx = at.getX() - origin.getX();
                    int dz = at.getZ() - origin.getZ();
                    // On a wall, the run along it must straddle the middle: the two columns either
                    // side of the seam are side/2 - 1 and side/2, and they mirror each other.
                    int along = (dx == 0 || dx == side - 1) ? dz : dx;
                    helper.assertTrue(along + (side - 1 - along) == side - 1
                            && (along == side / 2 - 1 || along == side / 2),
                        "a door block at " + at + " is not centred on its wall of " + side);
                    helper.assertTrue(at.getY() == 1 || at.getY() == 2,
                        "a door block at " + at + " is not on the floor");
                }
            }
            helper.succeed();
        });
    }

    /**
     * A door's two leaves meet in the middle, on every wall.
     *
     * <p>They did not. The parts were laid out "looking into the room", which is the one side of a
     * wall nobody is ever on — a player stands inside and looks <em>out</em> — so all four doors
     * came out mirrored: both handles against the outer edges, a hinge stile down the centre of
     * each leaf. It survived a build, a datagen run and a hundred gametests, and cost one glance at
     * the first screenshot of a room.
     *
     * <p>The assertion is geometric rather than a table of coordinates: the viewer inside faces
     * along the wall's outward normal, so their left is {@code up × outward}, and the LEFT leaf
     * must sit further that way than the RIGHT one. That holds whatever the footprints become.
     */
    @GameTest
    @TestHolder(description = "A door's left leaf is on the left of somebody standing inside.")
    public static void aDoorsLeavesMeetInTheMiddle(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            for (int tier = 1; tier <= RoomGeometry.MAX_TIER; tier++) {
                int side = RoomGeometry.footprint(tier);
                BlockPos origin = RoomGeometry.origin(0);
                var doors = RoomGeometry.doors(0, tier);
                int walls = 0;
                for (Direction outward : Direction.Plane.HORIZONTAL) {
                    // The wall this direction faces out of, and the axis that runs along it.
                    Direction left = outward.getCounterClockWise();
                    BlockPos leftLeaf = null;
                    BlockPos rightLeaf = null;
                    for (var door : doors.entrySet()) {
                        BlockPos at = door.getKey();
                        int dx = at.getX() - origin.getX();
                        int dz = at.getZ() - origin.getZ();
                        boolean onThisWall = switch (outward) {
                            case NORTH -> dz == 0;
                            case SOUTH -> dz == side - 1;
                            case WEST -> dx == 0;
                            default -> dx == side - 1;
                        };
                        if (!onThisWall || at.getY() != 1) {
                            continue;
                        }
                        if (door.getValue() == com.neryos.workbay.content.room.RoomPart
                                .DOOR_BOTTOM_LEFT) {
                            leftLeaf = at;
                        } else if (door.getValue() == com.neryos.workbay.content.room.RoomPart
                                .DOOR_BOTTOM_RIGHT) {
                            rightLeaf = at;
                        }
                    }
                    helper.assertTrue(leftLeaf != null && rightLeaf != null,
                        "the " + outward + " wall of a tier " + tier
                            + " room has no bottom pair of door leaves");
                    // Further along the viewer's left is a bigger dot product with that vector.
                    int leftness = (leftLeaf.getX() - rightLeaf.getX()) * left.getStepX()
                        + (leftLeaf.getZ() - rightLeaf.getZ()) * left.getStepZ();
                    helper.assertTrue(leftness > 0,
                        "on the " + outward + " wall the left leaf at " + leftLeaf
                            + " is not to the left of the right leaf at " + rightLeaf
                            + " for somebody standing inside; the door reads as two single doors"
                            + " hung backwards");
                    walls++;
                }
                helper.assertTrue(walls == 4, "a room has " + walls + " walls with doors, not 4");
            }
            helper.succeed();
        });
    }

    /**
     * Every tier is a cube. Height costs no chunks, which is why the <em>price</em> is the
     * footprint -- but a free axis is not a reason to make a 14-wide room 32 tall, which reads as
     * a shaft. The assertion is the proportion, not the number, so changing the ladder cannot
     * silently un-cube it.
     */
    @GameTest
    @TestHolder(description = "Every room tier is a cube, walls and ceiling included.")
    public static void everyRoomIsACube(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomVisit.leave(site.player());
            RoomRecord room = room(helper, site);
            for (int tier = 1; tier <= RoomGeometry.MAX_TIER; tier++) {
                helper.assertTrue(RoomGeometry.height(tier) == RoomGeometry.interior(tier),
                    "tier " + tier + " is " + RoomGeometry.interior(tier) + " across and "
                        + RoomGeometry.height(tier) + " tall");
            }
            // And the shell in the world agrees: a ceiling one above the interior's top, air below.
            BlockPos origin = RoomGeometry.origin(room.region());
            int top = RoomGeometry.height(1) + 1;
            helper.assertTrue(site.backshop().getBlockState(origin.offset(3, top, 3))
                    .is(WBBlocks.ROOM_WALL.get()),
                "there is no ceiling at y " + top);
            helper.assertTrue(site.backshop().getBlockState(origin.offset(3, top - 1, 3)).isAir(),
                "the block under the ceiling is not air");
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
