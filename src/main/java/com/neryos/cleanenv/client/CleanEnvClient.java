package com.neryos.cleanenv.client;

import com.neryos.cleanenv.CleanEnv;
import com.neryos.cleanenv.client.content.counter.CounterScreen;
import com.neryos.cleanenv.init.CEMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CleanEnv.MOD_ID, value = Dist.CLIENT)
public class CleanEnvClient {
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CEMenus.COUNTER.get(), CounterScreen::new);
    }
}
