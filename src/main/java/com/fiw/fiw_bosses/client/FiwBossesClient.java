package com.fiw.fiw_bosses.client;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FiwBosses.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class FiwBossesClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossEntityRegistry.BOSS.get(), BossEntityRenderer::new);
    }
}
