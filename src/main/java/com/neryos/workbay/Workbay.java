package com.neryos.workbay;

import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Workbay.MOD_ID)
public class Workbay {
    public static final String MOD_ID = "workbay";

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public Workbay(IEventBus modEventBus, ModContainer modContainer) {
        WBBlocks.register(modEventBus);
        WBItems.register(modEventBus);
    }
}
