package com.neryos.cleanenv.datagen;

import com.neryos.cleanenv.init.CEBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.stream.Collectors;

/** Without a loot table a block simply vanishes when broken. Every block needs one. */
public class CELootTableProvider extends BlockLootSubProvider {

    public CELootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(CEBlocks.PLACEHOLDER_BLOCK.get());
        dropSelf(CEBlocks.COUNTER_BLOCK.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return CEBlocks.BLOCKS.getEntries().stream().map(e -> (Block) e.value()).collect(Collectors.toList());
    }
}
