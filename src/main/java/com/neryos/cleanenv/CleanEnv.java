package com.neryos.cleanenv;

import com.neryos.cleanenv.init.CEBlockEntities;
import com.neryos.cleanenv.init.CEBlocks;
import com.neryos.cleanenv.init.CECreativeTabs;
import com.neryos.cleanenv.init.CEItems;
import com.neryos.cleanenv.init.CEMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CleanEnv.MOD_ID)
public class CleanEnv {
    public static final String MOD_ID = "cleanenv";

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public CleanEnv(IEventBus modEventBus, ModContainer modContainer) {
        CEBlocks.register(modEventBus);
        CEBlockEntities.register(modEventBus);
        CEItems.register(modEventBus);
        CEMenus.register(modEventBus);
        CECreativeTabs.register(modEventBus);
    }
}
