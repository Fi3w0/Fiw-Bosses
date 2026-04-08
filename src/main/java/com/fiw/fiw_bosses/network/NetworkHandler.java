package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FiwBosses.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {
    private NetworkHandler() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(
                    BossSkinPayload.TYPE,
                    BossSkinPayload.STREAM_CODEC,
                    com.fiw.fiw_bosses.client.skin.ClientSkinManager::handlePayload
            );
        } else {
            registrar.playToClient(
                    BossSkinPayload.TYPE,
                    BossSkinPayload.STREAM_CODEC,
                    (payload, ctx) -> {}
            );
        }
    }

    public static void sendSkinToPlayer(ServerPlayer player, BossEntity boss) {
        if (boss.getBossId() == null) return;

        SkinData skinData = SkinCache.getSkin(boss.getBossId());
        if (skinData == null) {
            SkinCache.getSkinAsync(boss.getBossId()).thenAccept(data -> {
                if (data != null) {
                    player.server.execute(() -> doSend(player, boss.getId(), data));
                }
            });
            return;
        }
        doSend(player, boss.getId(), skinData);
    }

    private static void doSend(ServerPlayer player, int entityId, SkinData skinData) {
        PacketDistributor.sendToPlayer(player, new BossSkinPayload(entityId, skinData.slim, skinData.png));
    }
}
