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
import net.minecraft.world.item.Items;
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
        // Powered, because a real one has to be: SPEC.md §9 charges the buffer for every
        // link that is switched on and again for every move, so an unfed Workbay runs
        // nothing and every link on it reads NO_POWER. OPEN_ISSUES #72. A test that is not
        // about the bill pays it up front and says so here.
        workbay.energy().deserializeNBT(null,
            net.minecraft.nbt.IntTag.valueOf(WorkbayBlockEntity.BUFFER_FE));
        WorkbayRecord record = workbay.record().orElseThrow();
        WorkbayRecord.Upgrades up = record.upgrades();
        record = record.withUpgrades(new WorkbayRecord.Upgrades(up.expansionPlates(), up.resonators(),
            anchors, annexPlates, tier, up.impellers()));
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
    /**
     * OPEN_ISSUES #62. Opening a room was a one-way door: a slot spent on a misplaced click was a
     * slot owned for ever, and a network with four rooms and one mistake had three.
     *
     * <p>Both halves, because the refusal is the whole design: a room with something in it is
     * <b>not</b> handed back, because what is in a room is a build and SPEC.md §8 does not delete
     * one to save a player a click. Empty it and the same click works.
     */
    @GameTest
    @TestHolder(description = "A room is handed back only once it is empty, and the slot comes back with it.")
    public static void aRoomIsHandedBackOnlyWhenEmpty(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            ServerLevel backshop = site.backshop();
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0),
                "opening the room failed");
            RoomVisit.leave(site.player());
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            RoomRecord room = room(helper, site);
            helper.assertTrue(room.built(), "the room was not built by entering it");

            // Something of the player's, one block inside the door.
            BlockPos inside = RoomGeometry.origin(room.region()).offset(2, 2, 2);
            backshop.setBlock(inside, Blocks.CHEST.defaultBlockState(), 3);

            WorkbayBlockEntity workbay =
                (WorkbayBlockEntity) helper.getLevel().getBlockEntity(site.workbayPos());
            com.neryos.workbay.menu.WorkbayMenu menu = new com.neryos.workbay.menu.WorkbayMenu(1, site.player().getInventory(), workbay,
                com.neryos.workbay.menu.WorkbayMenu.build(workbay, site.player(), 0));
            menu.act(com.neryos.workbay.menu.WorkbayAction.REMOVE_ROOM, 0, java.util.Optional.empty());
            helper.assertTrue(room(helper, site).built(),
                "a room with a chest in it was handed back anyway, which throws away a build");
            helper.assertTrue(backshop.getBlockState(inside).is(Blocks.CHEST),
                "the chest inside the room");

            backshop.setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            menu.act(com.neryos.workbay.menu.WorkbayAction.REMOVE_ROOM, 0, java.util.Optional.empty());
            helper.assertFalse(room(helper, site).built(),
                "an empty room was still not handed back, so the slot is spent for ever");
            helper.assertTrue(
                backshop.getBlockState(RoomGeometry.origin(room.region())).isAir(),
                "the shell is still standing after the room was handed back");
            helper.succeed();
        });
    }

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

    // ------------------------------------------------- a room is a stage

    /**
     * <b>A room with a Connector in it is loaded exactly while its Workbay's chunk is.</b>
     * <b>A room is never force-loaded off its Workbay, whatever is standing in it.</b>
     *
     * <p>Mirroring used to reach into any room holding a Connector, on the argument that a barrel
     * in a room is as cheap as a barrel on the floor. It is not: one ticket is a five-by-five
     * square of loaded chunks, so a Connector in a Vast room quietly bought forty-nine of them
     * with no upgrade and no way for a host to see it. A machine is what this mod sells and a room
     * is a place you walk to — walking to it is what loads it.
     *
     * <p><b>Neither room is ever built by this test.</b> {@code RoomBuilder} would load the chunk
     * to lay the shell in it and the positive half would then pass whatever the mod did — so both
     * rooms are stamped as built in the registry and the shells are never poured. The only thing
     * in this test that can load a Backshop chunk is the mirroring under test, and the second room
     * is the proof of that: same tier, same registry, no Connector in it, still cold.
     */
    @GameTest
    @TestHolder(description = "Neither a room holding a Connector nor an empty one is force-loaded off the Workbay.")
    public static void aRoomIsNeverForceLoadedOffTheWorkbay(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1, 1, 0);
            ServerLevel backshop = site.backshop();
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());

            // Two rooms, both "built" as far as the registry is concerned and neither poured.
            RoomRecord staged = registry.createRoom().withBuiltTier(1);
            RoomRecord idle = registry.createRoom().withBuiltTier(1);
            registry.putRoom(staged);
            registry.putRoom(idle);
            registry.put(registry.byId(site.record().id()).orElseThrow()
                .withRooms(java.util.List.of(staged.id(), idle.id())));

            net.minecraft.world.level.ChunkPos hot =
                RoomGeometry.chunks(staged.region(), 1).getFirst();
            net.minecraft.world.level.ChunkPos cold =
                RoomGeometry.chunks(idle.region(), 1).getFirst();
            helper.assertFalse(backshop.getChunkSource().hasChunk(hot.x, hot.z),
                "the staged room's chunk was already loaded before anything asked for it, so this "
                    + "test could not tell mirroring from a chunk somebody else pulled in");
            helper.assertFalse(backshop.getChunkSource().hasChunk(cold.x, cold.z),
                "the idle room's chunk was already loaded, so the control proves nothing");

            // A Connector standing in the staged room, pointed at whatever is next to it. Nothing
            // has to be there: the link's existence is what puts the room on the list.
            BlockPos inside = RoomGeometry.origin(staged.region()).offset(4, 1, 4);
            WorkbayBlockEntity workbay =
                (WorkbayBlockEntity) helper.getLevel().getBlockEntity(site.workbayPos());
            workbay.addBus(com.neryos.workbay.bus.BusConfig.create(java.util.UUID.randomUUID(), 0,
                com.neryos.workbay.bus.BusConfig.Resource.ITEM,
                com.neryos.workbay.bus.BusConfig.Mode.INSERT,
                net.minecraft.core.GlobalPos.of(WorkbayDimensions.BACKSHOP, inside),
                net.minecraft.core.GlobalPos.of(WorkbayDimensions.BACKSHOP, inside.east())));

            helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertFalse(backshop.getChunkSource().hasChunk(hot.x, hot.z),
                        "the room holding a Connector was force-loaded off the Workbay, which is "
                            + "twenty-five chunks nobody asked for and no upgrade gates");
                    helper.assertFalse(backshop.getChunkSource().hasChunk(cold.x, cold.z),
                        "a room with nothing in it was loaded, which is a ticket spent on "
                            + "scenery");
                })
                .thenSucceed();
        });
    }

    /**
     * A link into a room is named by the <b>room</b>, not by two coordinates.
     *
     * <p>The Backshop position of a barrel is six figures in a dimension the player cannot walk to,
     * and every stage of a chain holds the same kind of block — so "Barrel, Barrel, Barrel" on the
     * flow map is a map of one thing. The room is the only part of the answer that is different
     * per stage, and the client cannot work it out: only the server can invert a Backshop position
     * into a room, which is why it rides the snapshot.
     */
    @GameTest
    @TestHolder(description = "A link whose Connector stands in a room carries that room's slot on the snapshot.")
    public static void aLinkIntoARoomIsNamedByTheRoom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1, 1, 0);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            RoomRecord first = registry.createRoom().withBuiltTier(1);
            RoomRecord second = registry.createRoom().withBuiltTier(1);
            registry.putRoom(first);
            registry.putRoom(second);
            registry.put(registry.byId(site.record().id()).orElseThrow()
                .withRooms(java.util.List.of(first.id(), second.id())));

            WorkbayBlockEntity workbay =
                (WorkbayBlockEntity) helper.getLevel().getBlockEntity(site.workbayPos());
            BlockPos inSecond = RoomGeometry.origin(second.region()).offset(4, 1, 4);
            BlockPos outside = helper.absolutePos(new BlockPos(2, 1, 2));
            workbay.addBus(com.neryos.workbay.bus.BusConfig.create(java.util.UUID.randomUUID(), 0,
                com.neryos.workbay.bus.BusConfig.Resource.ITEM,
                com.neryos.workbay.bus.BusConfig.Mode.INSERT,
                net.minecraft.core.GlobalPos.of(WorkbayDimensions.BACKSHOP, inSecond),
                net.minecraft.core.GlobalPos.of(WorkbayDimensions.BACKSHOP, inSecond.east())));
            workbay.addBus(com.neryos.workbay.bus.BusConfig.create(java.util.UUID.randomUUID(), 0,
                com.neryos.workbay.bus.BusConfig.Resource.ITEM,
                com.neryos.workbay.bus.BusConfig.Mode.INSERT,
                net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), outside),
                net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), outside.east())));

            com.neryos.workbay.menu.WorkbaySnapshot snapshot =
                com.neryos.workbay.menu.WorkbayMenu.build(workbay, site.player(), 0);
            helper.assertTrue(snapshot.links().size() == 2,
                "expected two links on the snapshot, got " + snapshot.links().size());
            java.util.Optional<Integer> inRoom = snapshot.links().stream()
                .filter(link -> link.config().connector().pos().equals(inSecond))
                .findFirst().orElseThrow().targetRoom();
            helper.assertTrue(inRoom.isPresent() && inRoom.get() == 1,
                "the link into room 2 says " + inRoom + " instead of slot 1, so the row and the "
                    + "flow map fall back to a Backshop coordinate");
            helper.assertFalse(snapshot.links().stream()
                .filter(link -> link.config().connector().pos().equals(outside))
                .findFirst().orElseThrow().targetRoom().isPresent(),
                "a link out in the world was given a room, so every row would claim to be in one");

            // And it is NOT internal, however Backshop its target is. Those two were the same
            // thing right up until a Connector could stand in a room -- the flow map read "target
            // is in the Backshop" as "bay to bay", looked up a bay node that does not exist and
            // dropped the link off the map entirely. Found by drawing the map with one in it.
            helper.assertFalse(snapshot.links().stream()
                .filter(link -> link.config().connector().pos().equals(inSecond))
                .findFirst().orElseThrow().config().internal(),
                "a link into a room calls itself internal, which is what makes it vanish from the "
                    + "flow map");
            helper.succeed();
        });
    }

    /**
     * <b>The whole chain, with a room in the middle of it.</b> Six stages: a chest on the floor, a
     * container in a bay, a second container in a bay, a barrel <em>in a room</em>, a third
     * container in a bay, and a chest back on the floor. Sixteen iron ingots go in one end and this
     * test waits for all sixteen to come out of the other.
     *
     * <p>The room is the stage that did not exist before. Everything else in this chain was already
     * proved by {@code BusTests}: a Connector on a chest, an internal bay-to-bay link, a link into
     * a bay. What is new is that a barrel standing in a room is reachable at all — it needs the
     * room's chunk loaded off the Workbay, a Connector to have been placeable in there, and the
     * link's Backshop-to-Backshop hop to resolve — and the only honest way to show that is to make
     * the item cross it.
     *
     * <p>The bay stages are plain containers rather than machines on purpose: a machine turns the
     * item into a different item, and this test's whole assertion is that <em>the same sixteen
     * ingots</em> came out the far end. {@code fourLinksRunAMekanismLineFromOutsideTheBay} is the
     * one that puts real machines in the bays.
     */
    @GameTest(timeoutTicks = 1200)
    @TestHolder(description = "Sixteen iron cross six stages -- chest, bay, bay, a barrel in a room, bay, chest.")
    public static void anItemCrossesSixStagesIncludingARoom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(7, 5, 7));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel level = helper.getLevel();
            GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            Site site = site(helper, 1);
            ServerLevel backshop = site.backshop();
            RoomRegistry registry = RoomRegistry.get(level.getServer());

            // Three bays, and a container racked in each. Racked directly, the way BusTests racks:
            // an Expansion Plate item is not what this test is about.
            WorkbayRecord record = registry.byId(site.record().id()).orElseThrow();
            registry.put(record.withUpgrades(new WorkbayRecord.Upgrades(2, 0, 0, 0, 1, 0)));
            record = registry.byId(site.record().id()).orElseThrow();
            for (int bay = 0; bay < 3; bay++) {
                com.neryos.workbay.world.BayHosting.rack(backshop, record.bayColumn(), bay,
                    new ItemStack(Blocks.BARREL), player, Direction.NORTH);
            }

            // Stage 1 and stage 6, on the floor beside the Workbay.
            BlockPos source = helper.absolutePos(new BlockPos(4, 1, 0));
            BlockPos sink = helper.absolutePos(new BlockPos(4, 1, 4));
            level.setBlock(source, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sink, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            ((net.minecraft.world.Container) level.getBlockEntity(source))
                .setItem(0, new ItemStack(Items.IRON_INGOT, 16));

            // Stage 4: a barrel standing in the room, exactly where a player would put one.
            helper.assertTrue(RoomVisit.enter(site.player(), record, 0), "opening the room failed");
            RoomVisit.leave(site.player());
            RoomRecord room = room(helper, site);
            BlockPos barrel = RoomGeometry.origin(room.region()).offset(6, 1, 6);
            backshop.setBlock(barrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(backshop.getBlockState(barrel).is(Blocks.BARREL),
                "the barrel is not in the room, so there is no fourth stage");

            WorkbayBlockEntity workbay =
                (WorkbayBlockEntity) level.getBlockEntity(site.workbayPos());

            // 1 -> 2. A Connector on the source chest, pulling into bay 1.
            workbay.addBus(connector(helper, workbay, level, source.above(), Direction.DOWN, 0,
                player).withMode(com.neryos.workbay.bus.BusConfig.Mode.EXTRACT)
                .withRate(8).withSpeed(10));

            // 2 -> 3. Bay to bay, no Connector anywhere.
            player.moveTo(site.workbayPos().getX() + 0.5, site.workbayPos().getY(),
                site.workbayPos().getZ() + 0.5);
            com.neryos.workbay.menu.WorkbayMenu menu = new com.neryos.workbay.menu.WorkbayMenu(1,
                player.getInventory(), workbay,
                com.neryos.workbay.menu.WorkbayMenu.build(workbay, player, 0));
            menu.act(com.neryos.workbay.menu.WorkbayAction.SELECT_BAY, 0, java.util.Optional.empty());
            menu.act(com.neryos.workbay.menu.WorkbayAction.CREATE_INTERNAL_LINK, 1,
                java.util.Optional.empty());
            com.neryos.workbay.bus.BusConfig internal = workbay.buses().stream()
                .filter(com.neryos.workbay.bus.BusConfig::internal).findFirst().orElseThrow();
            helper.assertValueEqual(internal.target().pos(),
                com.neryos.workbay.world.BayGeometry.machinePos(record.bayColumn(), 1),
                "the internal link's target bay");
            workbay.addBus(internal.withEnabled(true).withRate(8).withSpeed(10));

            // 3 -> 4 and 4 -> 5. Two Connectors on the barrel in the room, on two of its faces:
            // one paired to bay 2 pushing in, one paired to bay 3 pulling out. This is the pair of
            // links a room-as-a-stage is made of, and both live in another dimension entirely.
            workbay.addBus(connector(helper, workbay, backshop, barrel.above(), Direction.DOWN, 1,
                player).withMode(com.neryos.workbay.bus.BusConfig.Mode.INSERT)
                .withRate(8).withSpeed(10));
            workbay.addBus(connector(helper, workbay, backshop, barrel.north(), Direction.SOUTH, 2,
                player).withMode(com.neryos.workbay.bus.BusConfig.Mode.EXTRACT)
                .withRate(8).withSpeed(10));

            // 5 -> 6. Back out to a chest on the floor.
            workbay.addBus(connector(helper, workbay, level, sink.above(), Direction.DOWN, 2,
                player).withMode(com.neryos.workbay.bus.BusConfig.Mode.INSERT)
                .withRate(8).withSpeed(10));

            helper.assertValueEqual(workbay.buses().size(), 5, "links on the chain");
            BlockPos bay1 = com.neryos.workbay.world.BayGeometry.machinePos(record.bayColumn(), 0);
            BlockPos bay2 = com.neryos.workbay.world.BayGeometry.machinePos(record.bayColumn(), 1);
            BlockPos bay3 = com.neryos.workbay.world.BayGeometry.machinePos(record.bayColumn(), 2);

            helper.startSequence()
                .thenWaitUntil(() -> {
                    int arrived = count(level, sink);
                    if (arrived < 16) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(
                            "the chain has delivered " + arrived + " of 16 iron. source="
                                + count(level, source) + " bay1=" + count(backshop, bay1)
                                + " bay2=" + count(backshop, bay2)
                                + " room=" + count(backshop, barrel)
                                + " bay3=" + count(backshop, bay3));
                    }
                })
                .thenExecute(() -> helper.assertValueEqual(count(backshop, barrel), 0,
                    "iron left behind in the room's barrel"))
                .thenSucceed();
        });
    }

    /**
     * Pairs a Connector to one bay and sticks it on the block below {@code at}, in whichever level
     * is named — the Backshop for a Connector inside a room, the overworld for one on a chest.
     * Returns the link that placing it created, switched on, the way a player switches one on.
     */
    private static com.neryos.workbay.bus.BusConfig connector(ExtendedGameTestHelper helper,
        WorkbayBlockEntity workbay, ServerLevel where, BlockPos at, Direction facing, int bay,
        GameTestPlayer player) {
        ItemStack held = new ItemStack(WBBlocks.CONNECTOR.get());
        com.neryos.workbay.content.workbay.WorkbayBlock.pair(held, workbay.record().orElseThrow(),
            net.minecraft.core.GlobalPos.of(workbay.getLevel().dimension(), workbay.getBlockPos()),
            bay);
        var state = WBBlocks.CONNECTOR.get().defaultBlockState()
            .setValue(com.neryos.workbay.content.connector.ConnectorBlock.FACING, facing);
        int before = workbay.buses().size();
        where.setBlock(at, state, Block.UPDATE_ALL);
        WBBlocks.CONNECTOR.get().setPlacedBy(where, at, state, player, held);
        helper.assertValueEqual(workbay.buses().size(), before + 1,
            "links after placing a paired Connector at " + at + " in "
                + where.dimension().location());
        return workbay.buses().getLast().withEnabled(true);
    }

    private static int count(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)) {
            return -1;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(Items.IRON_INGOT)) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------ owner and guests

    /**
     * <b>A Workbay is a block anybody may right-click, and its rooms are not.</b>
     *
     * <p>Every other action on the screen checked the owner and this one did not — not even the
     * lock — so standing at somebody's Workbay was standing in every room they owned. Regions are
     * 512 blocks apart, which is nothing to somebody who has just launched himself upward.
     */
    @GameTest
    @TestHolder(description = "A stranger is refused a room they were not invited to; an invited guest is let in.")
    public static void onlyTheOwnerAndTheirGuestsMayEnterARoom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            GameTestPlayer stranger = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            WorkbayRecord record = registry.byId(site.record().id()).orElseThrow();

            helper.assertTrue(RoomVisit.enter(site.player(), record, 0),
                "the owner was refused their own room");
            RoomVisit.leave(site.player());

            record = registry.byId(site.record().id()).orElseThrow();
            helper.assertFalse(RoomVisit.enter(stranger, record, 0),
                "a stranger was let into somebody else's room");
            helper.assertFalse(stranger.level().dimension().equals(WorkbayDimensions.BACKSHOP),
                "a stranger was refused and is standing in the Backshop anyway");

            RoomRecord room = room(helper, site);
            registry.putRoom(room.withGuest(stranger.getUUID(),
                stranger.getGameProfile().getName(), com.neryos.workbay.world.RoomGuest.LOOK));
            record = registry.byId(site.record().id()).orElseThrow();
            helper.assertTrue(RoomVisit.enter(stranger, record, 0),
                "an invited guest was refused the room they were invited to");

            // And the guest list is the owner's alone. Anybody may open an unlocked Workbay, so a
            // snapshot that carried every room's guests would let one guest read who else was
            // invited to every other room -- which is what the door screen already refuses.
            WorkbayBlockEntity workbay =
                (WorkbayBlockEntity) helper.getLevel().getBlockEntity(site.workbayPos());
            helper.assertFalse(com.neryos.workbay.menu.WorkbayMenu
                .build(workbay, stranger, 0).rooms().stream()
                .anyMatch(r -> !r.guests().isEmpty()),
                "a guest's snapshot carries somebody else's guest list");
            helper.assertTrue(com.neryos.workbay.menu.WorkbayMenu
                .build(workbay, site.player(), 0).rooms().stream()
                .anyMatch(r -> !r.guests().isEmpty()),
                "the owner's own snapshot has no guest list on it, so the screen cannot draw one");
            helper.succeed();
        });
    }

    /**
     * The middle level, which kept suggesting itself until it was built. OPEN_ISSUES #51.
     *
     * <p>{@link com.neryos.workbay.world.RoomGuest#USE} is <em>may work what is here, may not
     * change it</em>: a factory has people meant to run it and not rebuild it, and with two levels
     * the only way to let somebody take an ingot out of a barrel was to let them break the barrel.
     *
     * <p>Both halves, because either one alone is a level that means nothing: a USE guest who
     * cannot open the chest is LOOK with extra steps, and one who can break it is BUILD.
     */
    @GameTest
    @TestHolder(description = "A guest at May work can open what is here and still cannot break it.")
    public static void aUseGuestOpensWhatIsHereAndBreaksNothing(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            GameTestPlayer guest = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            registry.putRoom(room.withGuest(guest.getUUID(), guest.getGameProfile().getName(),
                com.neryos.workbay.world.RoomGuest.USE));

            BlockPos chest = RoomGeometry.origin(room.region()).offset(5, 1, 5);
            site.backshop().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            RoomVisit.enter(guest, registry.byId(site.record().id()).orElseThrow(), 0);
            helper.assertTrue(guest.level().dimension().equals(WorkbayDimensions.BACKSHOP),
                "the guest is not in the room, so neither half of this proves anything");

            RoomRecord now = registry.room(room.id()).orElseThrow();
            // Asked of the two guards themselves rather than of one event, because they are what
            // every one of the five handlers routes through and the level is the thing under test.
            helper.assertTrue(RoomVisit.mayUse(registry, guest.getUUID(), now),
                "a guest at May work cannot open a container, which makes it Look only again");
            helper.assertFalse(RoomVisit.mayBuild(registry, guest.getUUID(), now),
                "a guest at May work may build, which makes it May build again");

            // And the break really is refused, through the call a left-click makes.
            guest.gameMode.destroyBlock(chest);
            helper.assertTrue(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "a guest at May work broke a block in somebody else's room");

            // One step round the ring is May build, and then it may.
            registry.putRoom(now.withGuest(guest.getUUID(), guest.getGameProfile().getName(),
                com.neryos.workbay.world.RoomGuest.USE.step()));
            helper.assertTrue(RoomVisit.mayBuild(registry, guest.getUUID(),
                    registry.room(room.id()).orElseThrow()),
                "one step up from May work is not May build, so the ring is in the wrong order");
            helper.succeed();
        });
    }

    /**
     * A {@link com.neryos.workbay.world.RoomGuest#LOOK} guest changes nothing, and the owner does.
     *
     * <p>Both halves, because a guard that refuses everybody is not a permission — it is a room
     * nobody can build in. The chest is broken through {@code ServerPlayerGameMode#destroyBlock},
     * which is the call a real left-click makes and the one that fires the event the guard listens
     * to; asserting on the guard's own method instead would prove only that the method compiles.
     */
    @GameTest
    @TestHolder(description = "A look-only guest cannot break a block in the room; the owner can.")
    public static void aLookOnlyGuestChangesNothingInTheRoom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            GameTestPlayer guest = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            registry.putRoom(room.withGuest(guest.getUUID(), guest.getGameProfile().getName(),
                com.neryos.workbay.world.RoomGuest.LOOK));

            BlockPos chest = RoomGeometry.origin(room.region()).offset(5, 1, 5);
            site.backshop().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "the chest was not placed, so neither half of this test means anything");

            RoomVisit.enter(guest, registry.byId(site.record().id()).orElseThrow(), 0);
            helper.assertTrue(guest.level().dimension().equals(WorkbayDimensions.BACKSHOP),
                "the guest is not in the room, so refusing their break proves nothing");
            guest.gameMode.destroyBlock(chest);
            helper.assertTrue(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "a look-only guest broke a block in somebody else's room");

            // And whatever is standing in the room, not only what is built into it: an item frame
            // smashed off a wall is a change to what is in the room made by somebody invited to
            // look at it. The entity mirror of the two block guards.
            net.minecraft.world.entity.decoration.ItemFrame frame =
                new net.minecraft.world.entity.decoration.ItemFrame(site.backshop(),
                    chest.above(), net.minecraft.core.Direction.NORTH);
            site.backshop().addFreshEntity(frame);
            guest.attack(frame);
            helper.assertTrue(frame.isAlive(),
                "a look-only guest destroyed an item frame in somebody else's room");
            site.player().attack(frame);
            helper.assertFalse(frame.isAlive(),
                "the owner cannot break an item frame in their own room, so that guard refuses "
                    + "everybody too");

            site.player().gameMode.destroyBlock(chest);
            helper.assertFalse(site.backshop().getBlockState(chest).is(Blocks.CHEST),
                "the owner cannot break a block in their own room, so the guard refuses everybody");
            helper.succeed();
        });
    }

    /**
     * <b>Over the wall is out of the room.</b>
     *
     * <p>Regions are 512 blocks apart and a room is at most 46 tall, so an item from another mod
     * that launches a player upward puts them above their own ceiling and pointed at the next
     * region along. The bounds half of the standing rule is what catches that, and nothing else
     * would: the walls cannot, because the player is no longer between them.
     */
    @GameTest
    @TestHolder(description = "A player who leaves his own room's bounds is put back on the next tick.")
    public static void leavingARoomsBoundsSendsYouHome(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            // Straight up, well past a tier-1 room's 14-block ceiling, and still in the Backshop.
            BlockPos over = RoomGeometry.origin(room.region()).offset(8, 60, 8);
            site.player().teleportTo(site.backshop(), over.getX() + 0.5, over.getY(),
                over.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F);
            helper.assertFalse(
                RoomGeometry.inside(site.player().blockPosition(), room.region(), room.builtTier()),
                "the player is still inside the room after being moved 60 blocks up, so this test "
                    + "proves nothing");

            helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertFalse(
                        site.player().level().dimension().equals(WorkbayDimensions.BACKSHOP),
                        "a player who launched himself over his own wall is loose in the Backshop, "
                            + "512 blocks from somebody else's room");
                    helper.assertFalse(RoomVisit.isInside(site.player()),
                        "the player is out but still recorded as being in a room");
                })
                .thenSucceed();
        });
    }

    /**
     * Un-inviting somebody takes effect <b>where they are standing</b>, not the next time they ask.
     *
     * <p>The same check runs on login, which is the case nobody would find by playing: somebody who
     * logs out in a room and is removed from it while they are away must not wake up in it.
     */
    @GameTest
    @TestHolder(description = "A guest removed while standing in a room is put out on the next tick.")
    public static void removingAGuestPutsThemOutOfTheRoom(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            GameTestPlayer guest = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            registry.putRoom(room.withGuest(guest.getUUID(), guest.getGameProfile().getName(),
                com.neryos.workbay.world.RoomGuest.BUILD));
            helper.assertTrue(
                RoomVisit.enter(guest, registry.byId(site.record().id()).orElseThrow(), 0),
                "the guest was refused a room they were invited to");

            helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(guest.level().dimension().equals(WorkbayDimensions.BACKSHOP),
                        "the guest was thrown out while they were still invited");
                    registry.putRoom(registry.room(room.id()).orElseThrow()
                        .withoutGuest(guest.getUUID()));
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertFalse(guest.level().dimension().equals(WorkbayDimensions.BACKSHOP),
                        "a guest who was un-invited is still standing in the room");
                    helper.assertFalse(RoomVisit.isInside(guest),
                        "the guest is out of the Backshop but still recorded as being in a room");
                })
                .thenSucceed();
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
    /**
     * The room screen prints what anchoring costs, and it was printing the wrong number.
     *
     * <p>"Holding 1 chunk loaded" is the count of <b>tickets</b>. A forced chunk drags its
     * neighbours up to loaded as far as the ticket reaches, so one ticket is a square: the
     * footprint is still on the line above, and this is the line that says what a server owner is
     * paying.
     *
     * <p><b>And it is now nine and not twenty-five</b>, because {@code chunkTicketRadius} ships at
     * 1 rather than the 2 NeoForge's controller always asked for (OPEN_ISSUES #60). The shipped
     * radius is asserted first, so a change back to 2 fails here rather than quietly tripling
     * every host's bill, and the formula is checked against the one number nobody can argue with.
     *
     * <p>Counted in the world rather than computed, so the assertion is over what the chunk source
     * answers and not over the formula restated.
     */
    @GameTest(timeoutTicks = 400)
    @TestHolder(description = "An anchored room holds exactly the chunks its tooltip prints.")
    public static void anAnchoredRoomHoldsTheChunksItsTooltipPrints(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            helper.assertValueEqual(com.neryos.workbay.world.WorkbayTickets.radius(), 1,
                "the shipped chunkTicketRadius");
            helper.assertValueEqual(RoomGeometry.anchorChunks(1), 9,
                "chunks one ticket at the shipped radius holds around a one-chunk room");

            Site site = site(helper, 1, 0, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "room refused");
            RoomVisit.leave(site.player());

            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            menu(helper, site).act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 0,
                java.util.Optional.empty());
            RoomRecord room = rooms(helper, site).get(0);
            helper.assertTrue(room.anchored(), "the anchor did not switch on");

            helper.startSequence().thenIdle(20).thenExecute(() ->
                helper.assertValueEqual(held(site, room), RoomGeometry.anchorChunks(room.chunkCost()),
                    "chunks the Backshop really holds for an anchored " + room.chunkCost()
                        + "-chunk room")).thenExecute(() -> registry.putRoom(rooms(helper, site).get(0).withAnchored(false)))
                .thenExecute(() -> com.neryos.workbay.world.RoomAnchors.apply(site.backshop(),
                    rooms(helper, site).get(0)))
                .thenSucceed();
        });
    }

    /**
     * SPEC.md §12's last line, and the bill a server owner is actually afraid of: <b>an Anchor
     * holds nothing while its owner is offline.</b>
     *
     * <p>Counted in the world on both sides of the boundary rather than asserted off the flag,
     * because "anchored" and "loaded now" are two different words on purpose — the room stays
     * anchored the whole way through this test and the chunks do not.
     *
     * <p>The logout is the real one: {@code disconnectGameTest} goes through
     * {@code PlayerList#remove}, which is what fires {@code PlayerLoggedOutEvent} for a real
     * player. The return cannot be, because a disconnected mock player cannot log in again — so the
     * network is handed to a second player who <em>is</em> online and {@code resume} is called by
     * hand, which is the one line {@code onLogin} consists of.
     *
     * <p>{@code anchorGraceMinutes} is set to zero for the test. At the shipped five it would have
     * to idle six thousand ticks to see anything, and the grace itself is arithmetic on a game
     * time, not behaviour.
     */
    @GameTest(timeoutTicks = 600)
    @TestHolder(description = "An anchored room holds no chunks while its owner is offline.")
    public static void anAnchoredRoomHoldsNothingWhileItsOwnerIsOffline(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            var config = com.neryos.workbay.config.WorkbayConfig.SERVER;
            int wasGrace = config.anchorGraceMinutes.get();
            // The shipped default first, so "it released" cannot mean "the grace was always zero".
            helper.assertValueEqual(wasGrace, 5, "the shipped anchor grace");
            config.anchorGraceMinutes.set(0);

            Site site = site(helper, 1, 0, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "room refused");
            RoomVisit.leave(site.player());

            RoomRegistry registry = RoomRegistry.get(helper.getLevel().getServer());
            menu(helper, site).act(com.neryos.workbay.menu.WorkbayAction.TOGGLE_ROOM_ANCHOR, 0,
                java.util.Optional.empty());
            RoomRecord room = rooms(helper, site).get(0);
            helper.assertTrue(room.anchored(), "the anchor did not switch on");
            int cost = RoomGeometry.anchorChunks(room.chunkCost());

            helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(held(site, room), cost,
                    "chunks an anchored room holds while its owner is online"))
                // The logout, and then long enough for the release to propagate and the chunks to
                // actually leave the map -- a dropped ticket is not an unloaded chunk on the same
                // tick.
                .thenExecute(() -> site.player().disconnectGameTest())
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(rooms(helper, site).get(0).anchored(),
                        "the room stopped being anchored, which is not what going offline means");
                    helper.assertValueEqual(held(site, room), 0,
                        "chunks an anchored room still holds after its owner logged out");
                })
                // And back. A second player, handed the network, standing in for the first walking
                // in through the door -- nothing else touched.
                .thenExecute(() -> {
                    GameTestPlayer back =
                        helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                    WorkbayRecord was = registry.byId(site.record().id()).orElseThrow();
                    registry.put(new WorkbayRecord(was.id(), was.code(), back.getUUID(),
                        back.getGameProfile().getName(), was.locked(), was.bayColumn(),
                        was.upgrades(), was.lastKnownPos(), was.bays(), was.rooms(), was.buses(),
                        was.deployedCount()));
                    com.neryos.workbay.world.AnchorPresence.resume(
                        helper.getLevel().getServer(), back.getUUID());
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(held(site, room), cost,
                    "chunks an anchored room holds again once its owner is back"))
                .thenExecute(() -> {
                    registry.putRoom(rooms(helper, site).get(0).withAnchored(false));
                    com.neryos.workbay.world.RoomAnchors.apply(site.backshop(),
                        rooms(helper, site).get(0));
                    config.anchorGraceMinutes.set(wasGrace);
                })
                .thenSucceed();
        });
    }

    /**
     * Chunks the Backshop really holds around a room. Two chunks of slack past the halo on every
     * side, so a wider hold would be seen rather than cropped out by the window this counts in.
     */
    private static int held(Site site, RoomRecord room) {
        net.minecraft.world.level.ChunkPos first =
            new net.minecraft.world.level.ChunkPos(RoomGeometry.origin(room.region()));
        int side = (int) Math.round(Math.sqrt(room.chunkCost()));
        int loaded = 0;
        for (int dx = -4; dx < side + 4; dx++) {
            for (int dz = -4; dz < side + 4; dz++) {
                if (site.backshop().getChunkSource().hasChunk(first.x + dx, first.z + dz)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

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

    /**
     * A room's light has a source. OPEN_ISSUES #45: the shell emits block light off every face, so
     * a room was lit and had nothing in it that was doing the lighting.
     *
     * <p>Three claims, and the third is the one that was actually wrong before. The ceiling carries
     * fixtures on a grid; a column that is not on the grid is plain ceiling, so the grid is a grid
     * and not "every block is a lamp"; and a fixture <b>emits more light than the surface it is set
     * into</b>, which is the difference between a source and a picture of one.
     *
     * <p>Then it takes one out and re-enters, because {@code RoomBuilder.repair} is a three-block
     * probe and every room built before this existed is exactly the case it has to catch.
     */
    @GameTest
    @TestHolder(description = "A room's ceiling carries light fixtures on a grid, and repairs one that is missing.")
    public static void aRoomsCeilingCarriesItsOwnLight(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomRecord room = room(helper, site);
            BlockPos origin = RoomGeometry.origin(room.region());
            int ceiling = RoomGeometry.ceilingY(1);
            int[] axis = RoomGeometry.lightAxis(1);
            helper.assertTrue(axis.length >= 2,
                "a 14-block room got " + axis.length + " fixtures on an axis, not two");

            for (int x : axis) {
                for (int z : axis) {
                    BlockPos at = origin.offset(x, ceiling, z);
                    helper.assertTrue(site.backshop().getBlockState(at)
                            .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART)
                            == com.neryos.workbay.content.room.RoomPart.LIGHT,
                        "no light fixture in the ceiling at " + at);
                }
            }
            // One block off the grid, which must still be plain ceiling: a lamp everywhere is the
            // uniform glow this is meant to replace.
            BlockPos between = origin.offset(axis[0] + 1, ceiling, axis[0]);
            helper.assertTrue(site.backshop().getBlockState(between)
                    .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART)
                    == com.neryos.workbay.content.room.RoomPart.CEILING,
                "the ceiling beside a fixture is a fixture too, so the grid is not a grid");

            BlockPos lamp = origin.offset(axis[0], ceiling, axis[0]);
            BlockPos wall = origin.offset(0, 3, 3);
            int lit = site.backshop().getBlockState(lamp).getLightEmission(site.backshop(), lamp);
            int shell = site.backshop().getBlockState(wall).getLightEmission(site.backshop(), wall);
            helper.assertTrue(lit > shell,
                "a fixture emits " + lit + " and the wall beside it " + shell
                    + ", so the room's light still has no source");

            // A room built before the ceiling had fixtures: right size, right colour, no lamp.
            site.backshop().setBlock(lamp, site.backshop().getBlockState(between),
                Block.UPDATE_CLIENTS);
            RoomBuilder.ensure(site.backshop(), room, 1);
            helper.assertTrue(site.backshop().getBlockState(lamp)
                    .getValue(com.neryos.workbay.content.room.RoomWallBlock.PART)
                    == com.neryos.workbay.content.room.RoomPart.LIGHT,
                "a room whose fixture was missing was not repaired on entry");
            helper.succeed();
        });
    }

    /**
     * A room takes a name. The row derives "Room 1" from its index, so the thing to assert is that
     * an empty name goes <b>back</b> to that rather than being stored as a room called nothing.
     */
    @GameTest
    @TestHolder(description = "A room takes a name of its own, and an empty one falls back to the derived one.")
    public static void aRoomTakesANameAndAnEmptyOneClearsIt(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(3, 3, 3));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            Site site = site(helper, 1);
            helper.assertTrue(RoomVisit.enter(site.player(), site.record(), 0), "entering was refused");
            RoomVisit.leave(site.player());
            com.neryos.workbay.menu.WorkbayMenu menu = menu(helper, site);

            menu.act(com.neryos.workbay.menu.WorkbayAction.SET_ROOM_NAME, 0,
                java.util.Optional.empty(), java.util.Optional.of("  Smeltery  "), false);
            helper.assertTrue(rooms(helper, site).getFirst().name()
                    .equals(java.util.Optional.of("Smeltery")),
                "the room is called " + rooms(helper, site).getFirst().name() + ", not Smeltery");

            menu.act(com.neryos.workbay.menu.WorkbayAction.SET_ROOM_NAME, 0,
                java.util.Optional.empty(), java.util.Optional.of("   "), false);
            helper.assertTrue(rooms(helper, site).getFirst().name().isEmpty(),
                "an empty name was stored as a name instead of clearing it");
            helper.succeed();
        });
    }

}
