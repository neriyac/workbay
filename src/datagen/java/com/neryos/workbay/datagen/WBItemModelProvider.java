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

        // SPEC.md §7: Shopsteel reads as a material rather than a tool, Plates are flat and
        // stacked, Frames are open squares. tools/make-art.py draws all six.
        flatItem(WBItems.SHOPSTEEL.getId().getPath());
        flatItem(WBItems.HOUSING.getId().getPath());
        flatItem(WBItems.EXPANSION_PLATE.getId().getPath());
        flatItem(WBItems.ROOM_FRAME.getId().getPath());
        flatItem(WBItems.WIDE_ROOM_FRAME.getId().getPath());
        flatItem(WBItems.VAST_ROOM_FRAME.getId().getPath());
        flatItem(WBItems.ANNEX_PLATE.getId().getPath());
        flatItem(WBItems.ANCHOR.getId().getPath());
        flatItem(WBItems.RESONATOR.getId().getPath());
        flatItem(WBItems.MULTICHANNEL.getId().getPath());
        flatItem(WBItems.IMPELLER.getId().getPath());
        blockItem(WBBlocks.CONNECTOR.getId().getPath());
        blockItem(WBBlocks.ASSAY.getId().getPath());
    }

    private void blockItem(String name) {
        withExistingParent(name, ResourceLocation.fromNamespaceAndPath(Workbay.MOD_ID, "block/" + name));
    }

    private void flatItem(String name) {
        withExistingParent(name, ResourceLocation.withDefaultNamespace("item/generated"))
            .texture("layer0", ResourceLocation.fromNamespaceAndPath(Workbay.MOD_ID, "item/" + name));
    }
}
