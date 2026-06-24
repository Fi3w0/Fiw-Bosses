package com.fiw.fiw_bosses.client;

import com.fiw.fiw_bosses.client.renderer.BossEntityRenderer;
import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.client.renderer.MinionEntityRenderer;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = FiwBossesCore.MOD_ID, value = Dist.CLIENT)
public final class FiwBossesClient {

    private FiwBossesClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossEntityRegistry.BOSS.get(), BossEntityRenderer::new);
        event.registerEntityRenderer(BossEntityRegistry.MINION.get(), MinionEntityRenderer::new);
    }

    // Drop the disguise/skin maps when a boss or minion unloads on the client so a
    // reused entity id can't inherit a stale disguise. BossEntity is the parent of
    // MinionEntity, so this covers both. Gated to client-side levels.
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof BossEntity boss) {
            ClientDisguiseManager.removeDisguise(boss.getId());
            ClientSkinManager.removeSkin(boss.getId());
        }
    }
}
