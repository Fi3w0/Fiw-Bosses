package com.fiw.fiw_bosses.client;

import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus client events: release the dynamic skin texture when a boss/minion
 * unloads, so GPU textures and the skin maps don't leak. BossEntity is the parent
 * of MinionEntity, so this covers both.
 */
@Mod.EventBusSubscriber(modid = FiwBossesCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FiwBossesForgeClientEvents {

    private FiwBossesForgeClientEvents() {}

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof BossEntity boss) {
            ClientDisguiseManager.removeDisguise(boss.getId());
            ClientSkinManager.removeSkin(boss.getId());
        }
    }
}
