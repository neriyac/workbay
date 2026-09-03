package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.host.HostChecks;
import com.neryos.workbay.init.WBBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * The compatibility boundary as data. SPEC.md §11: this is a tag rather than config precisely so a
 * pack, or a third-party mod with no dependency on us, can change it without waiting for a release.
 */
public class WBBlockTagProvider extends BlockTagsProvider {

    public WBBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
        ExistingFileHelper existingFileHelper) {
        super(output, registries, Workbay.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(HostChecks.HOST_DENIED)
            .addTag(BlockTags.BEDS)
            .addTag(BlockTags.DOORS)
            .add(Blocks.TRIAL_SPAWNER)
            .add(Blocks.VAULT)
            // A Workbay inside a Workbay. The recursion check in the mod's constructor catches it
            // first and gives a better message; this is here so the rule survives that check being
            // removed, and so it reads correctly to anyone inspecting the tag.
            .add(WBBlocks.WORKBAY.get());

        // Ships empty. It exists so a pack author has somewhere to put a block one of our
        // heuristics is wrong about, and an entry here beats every rule below it.
        tag(HostChecks.HOST_ALLOWED);
    }
}
