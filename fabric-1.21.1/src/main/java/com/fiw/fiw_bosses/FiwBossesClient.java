package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.client.renderer.BossEntityRenderer;
import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.client.renderer.MinionEntityRenderer;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class FiwBossesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BossEntityRegistry.BOSS_TYPE, BossEntityRenderer::new);
        EntityRendererRegistry.register(BossEntityRegistry.MINION_TYPE, MinionEntityRenderer::new);
        NetworkHandler.registerClientReceivers();

        // Drop the disguise/skin maps when a boss or minion unloads so a reused
        // entity id can't inherit a stale disguise. BossEntity is the parent of
        // MinionEntity, so this covers both.
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof BossEntity) {
                ClientDisguiseManager.removeDisguise(entity.getId());
                ClientSkinManager.removeSkin(entity.getId());
            }
        });
    }
}
