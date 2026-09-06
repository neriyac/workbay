package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WBItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Workbay.MOD_ID);

    /** Intermediate. Everything craftable in the mod is built on these two. SPEC.md §3. */
    public static final DeferredItem<Item> SHOPSTEEL =
        ITEMS.registerSimpleItem("shopsteel", new Item.Properties());

    public static final DeferredItem<Item> HOUSING =
        ITEMS.registerSimpleItem("housing", new Item.Properties());

    // No Levy item. The Assay has no faces (SPEC.md §2), so there is nowhere for it to hand a
    // physical token to and nothing that could pipe one out; Levy is a balance on the network,
    // banked by the Assay and spent on the upgrades screen. An item nothing produces and nothing
    // consumes is a lie in the creative tab.

    // Consumed on install. SPEC.md §1: counters on the Workbay, no inventory and no removal path.
    public static final DeferredItem<Item> EXPANSION_PLATE =
        ITEMS.registerSimpleItem("expansion_plate", new Item.Properties());

    public static final DeferredItem<Item> RESONATOR =
        ITEMS.registerSimpleItem("resonator", new Item.Properties());

    public static final DeferredItem<Item> MULTICHANNEL =
        ITEMS.registerSimpleItem("multichannel", new Item.Properties());

    public static final DeferredItem<Item> IMPELLER =
        ITEMS.registerSimpleItem("impeller", new Item.Properties());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
