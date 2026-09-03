package com.neryos.workbay.gametests;

import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.UUID;

/**
 * The same proof as {@link HostedMachineTests}, but with a machine nobody here wrote. A vanilla
 * furnace shares Minecraft's own block entity plumbing; a Mekanism machine brings its own
 * registration, its own capability providers, an owner UUID recorded in {@code setPlacedBy} and its
 * own serialization, and those are what the mod's central claim actually rests on.
 *
 * <p>Mekanism is {@code gametestRuntimeOnly}, so nothing here compiles against it. Everything goes
 * through the block registry and the standard capability interfaces — which is also exactly how
 * Workbay reaches a hosted machine, so this tests the real path rather than a friendlier one.
 *
 * <p>The load-bearing finding is {@link #foreignMachineExposesOnlyItsNullSide}, and it is a problem:
 * a Mekanism machine placed with {@code setBlock} + {@code setPlacedBy} exposes nothing on any of
 * its six faces. It is <em>not</em> a Backshop problem — the same placement in the overworld reads
 * exactly the same, which was the one comparison that mattered. OPEN_ISSUES #18.
 */
@ForEachTest(groups = "foreign_machine")
public class ForeignMachineTests {

    private static final ResourceLocation MACHINE = ResourceLocation.parse("mekanism:enrichment_chamber");

    private static final UUID STATE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000ba81");
    private static final UUID SIDE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000ba82");

    private static final ChunkPos STATE_CHUNK = new ChunkPos(2048, 192);
    private static final ChunkPos SIDE_CHUNK = new ChunkPos(2048, 256);

    private static ServerLevel backshop(ExtendedGameTestHelper helper) {
        ServerLevel backshop = helper.getLevel().getServer().getLevel(WorkbayDimensions.BACKSHOP);
        helper.assertNotNull(backshop, "getLevel(workbay:backshop) returned null");
        return backshop;
    }

    private static Block foreignMachine(ExtendedGameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.get(MACHINE);
        if (block == Blocks.AIR) {
            helper.fail(MACHINE + " is not registered. This test exists to prove Workbay hosts other "
                + "mods' machines, so a missing partner mod is a failure, never a skip. Check the "
                + "gametestRuntimeOnly Mekanism dependency in build.gradle.");
        }
        return block;
    }

    /**
     * Places the machine the way SPEC.md §10 requires rather than with a bare setBlock:
     * {@code setPlacedBy} is what records the owner UUID every Mekanism machine keeps, and skipping
     * it leaves the machine owner-less and locked. It is wrapped because a foreign block throwing
     * here is a real outcome the mod has to survive, not a test error.
     */
    private static BlockPos placeForeign(ExtendedGameTestHelper helper, ServerLevel backshop,
        UUID owner, ChunkPos chunk, Block machine) {
        WorkbayTickets.force(backshop, owner, chunk);
        BlockPos pos = chunk.getMiddleBlockPosition(16);
        backshop.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState state = machine.defaultBlockState();
        backshop.setBlock(pos, state, Block.UPDATE_ALL);

        // The test framework's own mock, not GameTestHelper#makeMockServerPlayerInLevel: that one
        // has a connection the framework's CLIENT_SYNC feature then sends test-status packets down,
        // which throws straight out of the server tick loop.
        GameTestPlayer placer = helper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
        try {
            machine.setPlacedBy(backshop, pos, state, placer, new ItemStack(machine));
        } catch (Exception e) {
            helper.fail("setPlacedBy threw for " + MACHINE + " in the Backshop: " + e);
        }
        backshop.invalidateCapabilities(pos);
        return pos;
    }

    /** Every capability that resolves, as a readable line for a failure message. */
    private static String describeFaces(ServerLevel level, BlockPos pos) {
        StringBuilder found = new StringBuilder();
        for (Direction side : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null) {
                found.append(side).append(":item ");
            }
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null) {
                found.append(side).append(":energy ");
            }
            if (level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null) {
                found.append(side).append(":fluid ");
            }
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null) {
            found.append("NULLSIDE:item ");
        }
        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null) {
            found.append("NULLSIDE:energy ");
        }
        return found.toString().trim();
    }

    private static int realFaceCount(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Direction side : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null
                || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * The proof that matters: a machine from another mod, hosted in the Backshop, keeps its own
     * state across a real chunk save and load. Compared as serialized NBT rather than through any
     * Mekanism API, so it covers everything the machine chose to persist — owner, security, energy,
     * side configuration — without compiling against a line of it.
     */
    @GameTest(timeoutTicks = 900)
    @TestHolder(description = "A hosted Mekanism machine keeps its own state across a Backshop chunk save and load.")
    public static void foreignMachineStateSurvivesSaveAndLoad(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel backshop = backshop(helper);
            Block machine = foreignMachine(helper);
            BlockPos pos = placeForeign(helper, backshop, STATE_OWNER, STATE_CHUNK, machine);

            BlockEntity be = backshop.getBlockEntity(pos);
            helper.assertNotNull(be, "no block entity for " + MACHINE + " in the Backshop");
            if (!be.getClass().getName().startsWith("mekanism.")) {
                helper.fail("expected a Mekanism block entity, got " + be.getClass().getName());
            }

            CompoundTag[] frozen = { null };

            helper.startSequence()
                .thenIdle(5) // let the machine run a few of its own ticks first
                .thenExecute(() -> {
                    WorkbayTickets.release(backshop, STATE_OWNER, STATE_CHUNK);
                    backshop.getChunkSource().save(false);
                })
                .thenWaitUntil(() -> {
                    if (!be.isRemoved()) {
                        throw new GameTestAssertException("Backshop chunk has not unloaded yet");
                    }
                })
                .thenExecute(() -> frozen[0] = be.saveWithFullMetadata(backshop.registryAccess()))
                .thenExecute(() -> {
                    backshop.getChunk(STATE_CHUNK.x, STATE_CHUNK.z);
                    BlockEntity reloaded = backshop.getBlockEntity(pos);
                    if (reloaded == be) {
                        helper.fail("the same block entity object came back - the chunk never really "
                            + "unloaded, so this proves nothing about saving");
                        return;
                    }
                    helper.assertNotNull(reloaded, "the hosted " + MACHINE + " did not come back");

                    // A block entity with nothing to say writes id/x/y/z and little else. If the tag
                    // were that small, matching tags would prove nothing about a modded machine's
                    // own fields, which is the whole reason Mekanism is here rather than a chest.
                    if (frozen[0].size() < 6) {
                        helper.fail("the hosted machine only persisted " + frozen[0].size()
                            + " tags (" + frozen[0].getAllKeys() + "), too little to be a real test");
                        return;
                    }
                    CompoundTag after = reloaded.saveWithFullMetadata(backshop.registryAccess());
                    if (!frozen[0].equals(after)) {
                        helper.fail("the hosted " + MACHINE + " came back different. saved=" + frozen[0]
                            + " loaded=" + after);
                    }
                })
                .thenExecute(() -> WorkbayTickets.release(backshop, STATE_OWNER, STATE_CHUNK))
                .thenSucceed();
        });
    }

    /**
     * Pins a finding, not a feature. A Mekanism machine written into the world with
     * {@code setBlock} + {@code setPlacedBy} exposes item and energy handlers <em>only</em> for the
     * null side — the one side SPEC.md §9 forbids a bus from using, because Mekanism's null-side
     * handler is read-only and fails silently. All six real faces answer null, before and after it
     * has ticked, in the Backshop and in the overworld alike.
     *
     * <p>So SPEC.md §11's {@code no_ports} check and §4's probe would mark every Mekanism machine
     * inert today. Something in Mekanism's own placement path is not being run by this order;
     * finding it is OPEN_ISSUES #18, and it blocks driving Mekanism machines from a bus.
     *
     * <p><b>When this test goes red, that is good news.</b> It means a real face started answering.
     * Read #18, delete this test, and assert the real behaviour instead.
     */
    @GameTest(timeoutTicks = 600)
    @TestHolder(description = "Pins OPEN_ISSUES #18: a programmatically placed Mekanism machine answers only on its null side.")
    public static void foreignMachineExposesOnlyItsNullSide(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            ServerLevel backshop = backshop(helper);
            Block machine = foreignMachine(helper);
            BlockPos pos = placeForeign(helper, backshop, SIDE_OWNER, SIDE_CHUNK, machine);

            helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    String faces = describeFaces(backshop, pos);
                    if (backshop.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) {
                        helper.fail("the hosted " + MACHINE + " exposes nothing at all, not even a "
                            + "null-side handler. faces=[" + faces + "]");
                        return;
                    }
                    if (realFaceCount(backshop, pos) != 0) {
                        helper.fail("a real face of the hosted " + MACHINE + " now answers: faces=["
                            + faces + "]. That is the good outcome - read OPEN_ISSUES #18, delete "
                            + "this test and assert the real behaviour instead.");
                    }
                })
                .thenExecute(() -> WorkbayTickets.release(backshop, SIDE_OWNER, SIDE_CHUNK))
                .thenSucceed();
        });
    }
}
