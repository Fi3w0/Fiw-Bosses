package com.fiw.fiw_bosses.client;

import com.fiw.fiw_bosses.client.renderer.BossEntityRenderer;
import com.fiw.fiw_bosses.client.renderer.MinionEntityRenderer;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client setup: entity renderers. */
@Mod.EventBusSubscriber(modid = FiwBossesCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FiwBossesForgeClient {

    private FiwBossesForgeClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossEntityRegistry.BOSS.get(), BossEntityRenderer::new);
        event.registerEntityRenderer(BossEntityRegistry.MINION.get(), MinionEntityRenderer::new);
    }
}
