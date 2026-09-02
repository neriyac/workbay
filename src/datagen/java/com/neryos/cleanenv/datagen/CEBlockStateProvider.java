package com.neryos.cleanenv.datagen;

import com.neryos.cleanenv.CleanEnv;
import com.neryos.cleanenv.init.CEBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class CEBlockStateProvider extends BlockStateProvider {

    public CEBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CleanEnv.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Both placeholders borrow vanilla textures. Replace when real art exists.
        simpleBlockWithTexture(CEBlocks.PLACEHOLDER_BLOCK.get(), mcBlock("stone"));
        simpleBlockWithTexture(CEBlocks.COUNTER_BLOCK.get(), mcBlock("polished_andesite"));
    }

    private void simpleBlockWithTexture(net.minecraft.world.level.block.Block block, ResourceLocation texture) {
        simpleBlock(block, models().cubeAll(name(block), texture));
    }

    private static String name(net.minecraft.world.level.block.Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private static ResourceLocation mcBlock(String path) {
        return ResourceLocation.withDefaultNamespace("block/" + path);
    }
}
