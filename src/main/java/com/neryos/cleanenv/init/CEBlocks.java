package com.neryos.cleanenv.init;

import com.neryos.cleanenv.CleanEnv;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class CEBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CleanEnv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CleanEnv.MOD_ID);

    public static final DeferredBlock<Block> PLACEHOLDER_BLOCK = registerWithItem("placeholder_block", Block::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));

    private static <B extends Block> DeferredBlock<B> registerWithItem(String name, Function<BlockBehaviour.Properties, ? extends B> func,
        BlockBehaviour.Properties props) {
        var blockHolder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.registerSimpleBlockItem(blockHolder);
        return blockHolder;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
