package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WBItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Workbay.MOD_ID);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
