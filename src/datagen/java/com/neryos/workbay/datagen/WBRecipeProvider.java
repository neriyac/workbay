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

        // The Connector is deliberately cheap: a link the player will not make because it costs too
        // much is a link they wire with pipes instead, and then the mod has not replaced anything.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.CONNECTOR.get(), 4)
            .pattern("SRS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('R', Items.REDSTONE)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // The mod's only machine. SPEC.md §3: Housing + Shopsteel + a diamond, and it does nothing
        // at all until it is racked in a bay.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.ASSAY.get())
            .pattern("SDS")
            .pattern("SHS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('D', Items.DIAMOND)
            .define('H', WBItems.HOUSING.get())
            .unlockedBy("has_housing", has(WBItems.HOUSING.get()))
            .save(output);

        // The upgrade ladder. Materials here, Levy at install: SPEC.md §1 says the Levy cost
        // *rises* along each line, and a recipe costs the same the tenth time as the first. So the
        // recipe is what the plate is made of and WorkbayUpgrade#levyCost is what it costs to fit.
        upgrade(output, WBItems.EXPANSION_PLATE.get(), Items.IRON_INGOT);
        upgrade(output, WBItems.RESONATOR.get(), Items.ENDER_EYE);
        upgrade(output, WBItems.MULTICHANNEL.get(), Items.AMETHYST_SHARD);
    }

    private void upgrade(RecipeOutput output, net.minecraft.world.item.Item result,
        net.minecraft.world.item.Item core) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
            .pattern(" S ")
            .pattern("SCS")
            .pattern(" H ")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('H', WBItems.HOUSING.get())
            .define('C', core)
            .unlockedBy("has_housing", has(WBItems.HOUSING.get()))
            .save(output);
    }
}
