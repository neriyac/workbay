package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.menu.WorkbayMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WBMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Workbay.MOD_ID);

    /**
     * {@code IMenuTypeExtension.create} rather than {@code MenuType::new}: it is what lets the whole
     * initial {@link com.neryos.workbay.menu.WorkbaySnapshot} ride the menu-open buffer, so the
     * screen is correct on frame 1 instead of flashing defaults (SPEC.md §4).
     */
    public static final DeferredHolder<MenuType<?>, MenuType<WorkbayMenu>> WORKBAY =
        MENUS.register("workbay", () -> IMenuTypeExtension.create(WorkbayMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
