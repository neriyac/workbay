package com.neryos.cleanenv.gametests;

import com.neryos.cleanenv.init.CEBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

@ForEachTest(groups = "placeholder_block")
public class PlaceholderBlockTests {
    @GameTest
    @TestHolder(description = "Ensures the placeholder block is registered and can be placed in the world.")
    public static void testPlaceholderBlockIsPlaceable(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, CEBlocks.PLACEHOLDER_BLOCK.get().defaultBlockState()));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            helper.assertBlockPresent(CEBlocks.PLACEHOLDER_BLOCK.get(), 0, 1, 0);
            helper.succeed();
        });
    }
}
