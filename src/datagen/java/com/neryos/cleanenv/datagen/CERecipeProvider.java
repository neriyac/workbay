package com.neryos.cleanenv.datagen;

import com.neryos.cleanenv.init.CEBlocks;
import com.neryos.cleanenv.init.CEItems;
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

public class CERecipeProvider extends RecipeProvider {

    public CERecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, CEBlocks.COUNTER_BLOCK.get())
            .pattern("SSS")
            .pattern("SRS")
            .pattern("SSS")
            .define('S', Blocks.STONE)
            .define('R', Items.REDSTONE)
            .unlockedBy("has_redstone", has(Items.REDSTONE))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, CEItems.PLACEHOLDER_ITEM.get())
            .requires(Items.BRICK)
            .unlockedBy("has_brick", has(Items.BRICK))
            .save(output);
    }
}
