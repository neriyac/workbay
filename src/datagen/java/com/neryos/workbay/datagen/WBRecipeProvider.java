package com.neryos.workbay.datagen;

import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * SPEC.md §3. All quantities are provisional and all of it is pack-editable JSON, which is the
 * point: a pack rebalancing Workbay never needs us.
 */
public class WBRecipeProvider extends RecipeProvider {

    public WBRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // SPEC.md §3 describes Shopsteel as "iron ingot + amethyst shard + ender pearl, blasting".
        // A blast furnace takes one ingredient, so that cannot be read literally without inventing
        // an intermediate item §2 does not list. Shapeless keeps the three costs exactly.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, WBItems.SHOPSTEEL.get())
            .requires(Items.IRON_INGOT)
            .requires(Items.AMETHYST_SHARD)
            .requires(Items.ENDER_PEARL)
            .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBItems.HOUSING.get())
            .pattern("SOS")
            .pattern("OEO")
            .pattern("SOS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('O', Blocks.OBSIDIAN)
            .define('E', Items.ENDER_EYE)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // Reachable the same evening a player first visits the End, and deliberately cheaper than
        // Mekanism's QIO Drive Array. No Levy anywhere in it: SPEC.md §1's no-circular-dependency
        // rule means the first Workbay must be buildable from vanilla materials alone.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.WORKBAY.get())
            .pattern("GAG")
            .pattern("AHA")
            .pattern("GEG")
            .define('G', Blocks.GLASS)
            .define('A', WBItems.SHOPSTEEL.get())
            .define('H', WBItems.HOUSING.get())
            .define('E', Items.ENDER_EYE)
            .unlockedBy("has_housing", has(WBItems.HOUSING.get()))
            .save(output);
    }
}
