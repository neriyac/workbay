package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class WBBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Workbay.MOD_ID);
    /** Block items. Plain items live in {@link WBItems}. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Workbay.MOD_ID);

    public static final DeferredBlock<WorkbayBlock> WORKBAY = registerWithItem("workbay", WorkbayBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .mapColor(MapColor.COLOR_BLACK)
            .sound(SoundType.METAL)
            .strength(3.5F)
            .noOcclusion());

    static <B extends Block> DeferredBlock<B> registerWithItem(String name,
        Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
        var blockHolder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.registerSimpleBlockItem(blockHolder);
        return blockHolder;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
