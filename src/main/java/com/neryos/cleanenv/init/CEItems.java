package com.neryos.cleanenv.init;

import com.neryos.cleanenv.CleanEnv;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CleanEnv.MOD_ID);

    public static final DeferredItem<Item> PLACEHOLDER_ITEM =
        ITEMS.registerSimpleItem("placeholder_item", new Item.Properties());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
