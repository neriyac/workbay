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
        // Two vanilla materials, no ender pearl, two out per craft. SPEC.md §3: the entry is
        // priced to be walked past, not saved up for - the ladder above it is where the cost is,
        // and that cost is Levy, which only exists once the player is already running the mod.
        // The old recipe was a third of an ender pearl per Shopsteel and the first Workbay wanted
        // twenty-three of them, which is twenty minutes of killing endermen before the dial the
        // mod is actually about.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, WBItems.SHOPSTEEL.get(), 2)
            .requires(Items.IRON_INGOT)
            .requires(Items.AMETHYST_SHARD)
            .unlockedBy("has_amethyst", has(Items.AMETHYST_SHARD))
            .save(output);

        // Housing is the upgrade tier's material and nothing else's. It is deliberately the
        // expensive intermediate: SPEC.md §0 prices the upgrades, not the entry, so the obsidian
        // and the ender eye sit above the first Workbay rather than in front of it.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBItems.HOUSING.get())
            .pattern("SOS")
            .pattern("OEO")
            .pattern("SOS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('O', Blocks.OBSIDIAN)
            .define('E', Items.ENDER_EYE)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // Glass, Shopsteel and one ender eye: four iron, four amethyst and the eye. No Housing,
        // because Housing is what the ladder above this block is made of. Reachable the same
        // evening a player first visits the End, and now genuinely in one sitting.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.WORKBAY.get())
            .pattern("GAG")
            .pattern("AEA")
            .pattern("GAG")
            .define('G', Blocks.GLASS)
            .define('A', WBItems.SHOPSTEEL.get())
            .define('E', Items.ENDER_EYE)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // The Connector is deliberately cheap: a link the player will not make because it costs too
        // much is a link they wire with pipes instead, and then the mod has not replaced anything.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.CONNECTOR.get(), 4)
            .pattern("SRS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('R', Items.REDSTONE)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // The mod's only machine, and the first thing a player wants after the Workbay itself -
        // nothing earns a single Levy until it is racked. So it is priced like the Workbay, not
        // like an upgrade: five Shopsteel and the diamond, no Housing.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, WBBlocks.ASSAY.get())
            .pattern("SDS")
            .pattern("SSS")
            .define('S', WBItems.SHOPSTEEL.get())
            .define('D', Items.DIAMOND)
            .unlockedBy("has_shopsteel", has(WBItems.SHOPSTEEL.get()))
            .save(output);

        // The upgrade ladder. Materials here, Levy at install: SPEC.md §1 says the Levy cost
        // *rises* along each line, and a recipe costs the same the tenth time as the first. So the
        // recipe is what the plate is made of and WorkbayUpgrade#levyCost is what it costs to fit.
        upgrade(output, WBItems.EXPANSION_PLATE.get(), Items.IRON_INGOT);
        upgrade(output, WBItems.RESONATOR.get(), Items.ENDER_EYE);
        upgrade(output, WBItems.MULTICHANNEL.get(), Items.AMETHYST_SHARD);
        upgrade(output, WBItems.IMPELLER.get(), Items.BREEZE_ROD);
        // The room ladder's cores climb, and its first rung is not quartz. A private dimension
        // gated behind the block you find in the first nether trip was the cheapest gate in the
        // mod on the most expensive thing in it; the Wide Frame takes the Ancient City trip that
        // used to be nowhere on this ladder, and the ordering stays monotone.
        upgrade(output, WBItems.ROOM_FRAME.get(), Items.DIAMOND);
        upgrade(output, WBItems.WIDE_ROOM_FRAME.get(), Items.ECHO_SHARD);
        // SPEC.md §3: the Vast Frame carries the mod's rarest gate because it buys the rarest
        // capability -- 46 blocks square is where a multiblock you built by hand goes.
        upgrade(output, WBItems.VAST_ROOM_FRAME.get(), Items.NETHER_STAR);
        upgrade(output, WBItems.ANNEX_PLATE.get(), Items.COPPER_INGOT);
        // SPEC.md §3: force loading carries the hardest gate in the mod, and a Heart of the Sea is
        // the vanilla one-time find that a Broken Spawner stands in for in a modded pack.
        upgrade(output, WBItems.ANCHOR.get(), Items.HEART_OF_THE_SEA);
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
