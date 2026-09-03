package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.content.assay.AssayBlock;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * What the Assay takes a cut of. SPEC.md §3.
 *
 * <p><b>Populated from tags, not from a list of items.</b> A hardcoded list of three things turns a
 * tax on your factory into a fetch quest wearing a theme, and it would say nothing at all about a
 * mod we have never seen. Every entry here is a convention tag every modded material already joins,
 * so the skim works out of the box in any pack, and a pack that disagrees ships one JSON file.
 *
 * <p>Deliberately <em>processed</em> materials only. Raw ore and mob drops are not in it: the tax is
 * meant to be paid by a factory that is already running, so a bigger floor really does unlock a
 * bigger floor.
 */
public class WBItemTagProvider extends ItemTagsProvider {

    public WBItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
        CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(output, registries, blockTags, Workbay.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(AssayBlock.LEVY_INPUT)
            .addTag(Tags.Items.INGOTS)
            .addTag(Tags.Items.GEMS)
            .addTag(Tags.Items.DUSTS)
            .addTag(Tags.Items.DYES)
            .addTag(Tags.Items.STORAGE_BLOCKS);
    }
}
