package com.neryos.cleanenv.init;

import com.neryos.cleanenv.CleanEnv;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CECreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CleanEnv.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("cleanenv", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cleanenv"))
            .icon(() -> new ItemStack(CEBlocks.PLACEHOLDER_BLOCK.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> {
                addAll(CEBlocks.ITEMS, parameters, output);
                addAll(CEItems.ITEMS, parameters, output);
            })
            .build());

    public static void register(IEventBus bus) {
        CREATIVE_MODE_TABS.register(bus);
    }

    private static void addAll(DeferredRegister<Item> items, CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        for (var entry : items.getEntries()) {
            output.accept(entry.get());
        }
    }
}
