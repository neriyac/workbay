package com.neryos.cleanenv.gametests;

import com.neryos.cleanenv.content.counter.CounterBlockEntity;
import com.neryos.cleanenv.init.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

@ForEachTest(groups = "counter_block")
public class CounterBlockEntityTests {

    /**
     * The Phase 1 gate: the block entity must not lose its state across a save/load. This
     * goes through the same saveWithFullMetadata/loadStatic pair the world save uses, so
     * deleting either saveAdditional or loadAdditional fails it.
     */
    @GameTest
    @TestHolder(description = "The counter block entity keeps its count across a save and load.")
    public static void testCounterSurvivesSaveAndLoad(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, CEBlocks.COUNTER_BLOCK.get().defaultBlockState()));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            BlockPos pos = new BlockPos(0, 1, 0);
            var registries = helper.getLevel().registryAccess();

            if (!(helper.getBlockEntity(pos) instanceof CounterBlockEntity be)) {
                helper.fail("no CounterBlockEntity at " + pos);
                return;
            }

            be.setCount(4242);

            CompoundTag saved = be.saveWithFullMetadata(registries);
            BlockEntity reloaded = BlockEntity.loadStatic(
                be.getBlockPos(), be.getBlockState(), saved, registries);

            if (!(reloaded instanceof CounterBlockEntity reloadedCounter)) {
                helper.fail("reload produced " + reloaded + ", not a CounterBlockEntity");
                return;
            }

            helper.assertValueEqual(reloadedCounter.getCount(), 4242, "count after save/load");
            helper.succeed();
        });
    }

    /** The counter is supposed to advance on its own; a dead ticker should be caught too. */
    @GameTest(timeoutTicks = 200)
    @TestHolder(description = "The counter block entity ticks its count upward.")
    public static void testCounterTicks(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, CEBlocks.COUNTER_BLOCK.get().defaultBlockState()));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            BlockPos pos = new BlockPos(0, 1, 0);
            if (!(helper.getBlockEntity(pos) instanceof CounterBlockEntity be)) {
                helper.fail("no CounterBlockEntity at " + pos);
                return;
            }
            be.setCount(0);
            helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    if (be.getCount() < 1) {
                        helper.fail("counter did not advance in 45 ticks, still " + be.getCount());
                    }
                })
                .thenSucceed();
        });
    }
}
