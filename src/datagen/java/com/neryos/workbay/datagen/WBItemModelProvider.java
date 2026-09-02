package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class WBItemModelProvider extends ItemModelProvider {

    public WBItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Workbay.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
    }

    /** Block items just reuse the block model. */
    void blockItem(String name) {
        withExistingParent(name, ResourceLocation.fromNamespaceAndPath(Workbay.MOD_ID, "block/" + name));
    }
}
