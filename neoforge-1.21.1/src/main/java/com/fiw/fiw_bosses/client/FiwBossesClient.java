package com.fiw.fiw_bosses.client;

import com.fiw.fiw_bosses.client.renderer.BossEntityRenderer;
import com.fiw.fiw_bosses.client.renderer.MinionEntityRenderer;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FiwBossesCore.MOD_ID, value = Dist.CLIENT)
public final class FiwBossesClient {

    private FiwBossesClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossEntityRegistry.BOSS.get(), BossEntityRenderer::new);
        event.registerEntityRenderer(BossEntityRegistry.MINION.get(), MinionEntityRenderer::new);
    }
}
