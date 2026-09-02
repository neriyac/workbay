package com.neryos.workbay.datagen;

import com.neryos.workbay.init.WBBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.stream.Collectors;

/** Without a loot table a block simply vanishes when broken. Every droppable block needs one. */
public class WBLootTableProvider extends BlockLootSubProvider {

    public WBLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return WBBlocks.BLOCKS.getEntries().stream().map(e -> (Block) e.value()).collect(Collectors.toList());
    }
}
