package com.neryos.cleanenv.init;

import com.neryos.cleanenv.CleanEnv;
import com.neryos.cleanenv.content.counter.CounterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, CleanEnv.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CounterMenu>> COUNTER =
        MENUS.register("counter", () -> IMenuTypeExtension.create(
            (containerId, playerInventory, buf) -> new CounterMenu(containerId, playerInventory)));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
