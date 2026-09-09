package com.neryos.workbay.datagen;

import com.neryos.workbay.init.WBDataMaps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.DataMapProvider;

import java.util.concurrent.CompletableFuture;

/**
 * What the Assay pays for each thing. OPEN_ISSUES #34.
 *
 * <p><b>A shipped table, not an empty mechanism.</b> The complaint was that a Levy is sixty-four of
 * anything; a data map with no entries in it leaves that exactly as true as it was. So the vanilla
 * ladder is written down here, and because a data map takes tags, one line covers every mod's
 * copper and every mod's diamond at once.
 *
 * <p><b>Order matters, and it is not this file's order.</b> A later entry wins outright
 * ({@code DataMapValueMerger#defaultMerger} returns the second value), and what "later" means is
 * the order of the keys in the generated JSON object, which the provider sorts. That is safe here
 * rather than lucky: a tag is written {@code #c:ingots} and its narrower children
 * {@code #c:ingots/gold}, so a parent always sorts before its children, and a bare item id has no
 * {@code #} so every item sorts after every tag. Broad floor, then the metals worth more than a
 * floor, then the handful of items in no tag at all — which is exactly the order wanted, from a
 * property of the encoding rather than from a list somebody has to keep sorted.
 *
 * <p>Everything absent stays at one, which is what everything used to be.
 *
 * <p>The numbers are a starting point in the sense SPEC.md means: a pack that disagrees ships its
 * own {@code data/workbay/data_maps/item/levy_value.json} and this file is gone.
 */
public class WBDataMapProvider extends DataMapProvider {

    public WBDataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", path));
    }

    @Override
    protected void gather() {
        var levy = builder(WBDataMaps.LEVY_VALUE)
            // The floor, and what the whole of #workbay:levy_input used to be worth.
            .add(c("dusts"), 1, false)
            .add(c("dyes"), 1, false)
            .add(c("ingots"), 2, false)
            .add(c("gems"), 4, false)
            // A block is nine of whatever it is made of, and the Assay should not be a way to make
            // that arithmetic come out differently in either direction.
            .add(c("storage_blocks"), 18, false)
            .add(c("ingots/copper"), 1, false)
            .add(c("ingots/gold"), 3, false)
            .add(c("gems/diamond"), 12, false)
            .add(c("gems/emerald"), 10, false)
            .add(c("ingots/netherite"), 40, false);
        // Vanilla puts these in no ingot or gem tag at all, and each is worth more than the tag it
        // is nearest to. Written by item because there is nothing else to write them by.
        levy.add(Items.NETHERITE_SCRAP.builtInRegistryHolder().key(), 10, false)
            .add(Items.AMETHYST_SHARD.builtInRegistryHolder().key(), 3, false)
            .add(Items.NETHERITE_BLOCK.builtInRegistryHolder().key(), 360, false)
            .add(Items.DIAMOND_BLOCK.builtInRegistryHolder().key(), 108, false)
            .add(Items.EMERALD_BLOCK.builtInRegistryHolder().key(), 90, false);
    }
}
