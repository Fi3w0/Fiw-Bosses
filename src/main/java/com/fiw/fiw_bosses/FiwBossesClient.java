package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.client.renderer.BossEntityRenderer;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class FiwBossesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BossEntityRegistry.BOSS_TYPE, BossEntityRenderer::new);
        NetworkHandler.registerClientReceivers();
    }
}
