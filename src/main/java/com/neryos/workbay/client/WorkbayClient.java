package com.neryos.workbay.client;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.client.screen.WorkbayScreen;
import com.neryos.workbay.init.WBMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half of the mod. Nothing here may be reachable from the server side. */
@EventBusSubscriber(modid = Workbay.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorkbayClient {
    private WorkbayClient() {}

    /** The eight per-link pips on the front of a Workbay. {@link WorkbayPips} says why they are
     *  drawn rather than put in the blockstate. */
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            com.neryos.workbay.init.WBBlockEntities.WORKBAY.get(), WorkbayPips::new);
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(WBMenus.WORKBAY.get(), WorkbayScreen::new);
        event.register(WBMenus.BAY_VIEW.get(),
            com.neryos.workbay.client.screen.BayViewScreen::new);
    }
}
