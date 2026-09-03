package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.init.WBBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class WBBlockStateProvider extends BlockStateProvider {

    public WBBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Workbay.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Placeholder art on vanilla textures. SPEC.md §7 wants a dark cabinet with a glass front
        // and three lit states; that is Phase 3, and every state currently draws the same model.
        ModelFile model = models().orientable("workbay",
            mcBlock("polished_deepslate"), mcBlock("blast_furnace_front"), mcBlock("polished_deepslate"));

        getVariantBuilder(WBBlocks.WORKBAY.get()).forAllStates(state -> ConfiguredModel.builder()
            .modelFile(model)
            .rotationY((int) state.getValue(HorizontalDirectionalBlock.FACING).toYRot())
            .build());
    }

    private static ResourceLocation mcBlock(String path) {
        return ResourceLocation.withDefaultNamespace("block/" + path);
    }
}
