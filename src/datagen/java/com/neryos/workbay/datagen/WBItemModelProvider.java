package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
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
        blockItem(WBBlocks.WORKBAY.getId().getPath());

        // Placeholder art. SPEC.md §7 wants these to read as materials rather than tools.
        flatItem(WBItems.SHOPSTEEL.getId().getPath(), "iron_ingot");
        flatItem(WBItems.HOUSING.getId().getPath(), "copper_ingot");
        flatItem(WBItems.EXPANSION_PLATE.getId().getPath(), "iron_nugget");
        flatItem(WBItems.RESONATOR.getId().getPath(), "echo_shard");
        flatItem(WBItems.MULTICHANNEL.getId().getPath(), "prismarine_crystals");
        blockItem(WBBlocks.CONNECTOR.getId().getPath());
        blockItem(WBBlocks.ASSAY.getId().getPath());
    }

    private void blockItem(String name) {
        withExistingParent(name, ResourceLocation.fromNamespaceAndPath(Workbay.MOD_ID, "block/" + name));
    }

    private void flatItem(String name, String vanillaTexture) {
        withExistingParent(name, ResourceLocation.withDefaultNamespace("item/generated"))
            .texture("layer0", ResourceLocation.withDefaultNamespace("item/" + vanillaTexture));
    }
}
