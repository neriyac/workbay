package com.neryos.cleanenv.datagen;

import com.neryos.cleanenv.CleanEnv;
import com.neryos.cleanenv.init.CEBlocks;
import com.neryos.cleanenv.init.CEItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class CEItemModelProvider extends ItemModelProvider {

    public CEItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CleanEnv.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Block items just reuse the block model.
        blockItem(CEBlocks.PLACEHOLDER_BLOCK.getId().getPath());
        blockItem(CEBlocks.COUNTER_BLOCK.getId().getPath());

        // Plain item, borrowing a vanilla texture until it has one of its own.
        withExistingParent(CEItems.PLACEHOLDER_ITEM.getId().getPath(), mcItem("generated"))
            .texture("layer0", ResourceLocation.withDefaultNamespace("item/brick"));
    }

    private void blockItem(String name) {
        withExistingParent(name, ResourceLocation.fromNamespaceAndPath(CleanEnv.MOD_ID, "block/" + name));
    }

    private static ResourceLocation mcItem(String path) {
        return ResourceLocation.withDefaultNamespace("item/" + path);
    }
}
