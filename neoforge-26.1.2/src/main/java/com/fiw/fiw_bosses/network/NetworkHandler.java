package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {

    private NetworkHandler() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registrar.playToClient(
                    BossRenderPayload.TYPE,
                    BossRenderPayload.STREAM_CODEC,
                    com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager::handlePayload
            );
            registrar.playToClient(
                    BossSkinPayload.TYPE,
                    BossSkinPayload.STREAM_CODEC,
                    com.fiw.fiw_bosses.client.skin.ClientSkinManager::handlePayload
            );
        } else {
            registrar.playToClient(BossRenderPayload.TYPE, BossRenderPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(BossSkinPayload.TYPE, BossSkinPayload.STREAM_CODEC, (payload, context) -> {});
        }
    }

    public static void sendSkinToPlayer(ServerPlayer player, BossEntity boss) {
        String disguise = sendRenderToPlayer(player, boss);
        if (!disguise.isBlank()) return;

        if (boss.getBossId() == null) return;

        SkinData skinData = SkinCache.getSkin(boss.getBossId());
        if (skinData == null) {
            SkinCache.getSkinAsync(boss.getBossId()).thenAccept(data -> {
                if (data != null) {
                    player.level().getServer().execute(() -> doSend(player, boss.getId(), data));
                }
            });
            return;
        }

        doSend(player, boss.getId(), skinData);
    }

    private static void doSend(ServerPlayer player, int entityId, SkinData skinData) {
        PacketDistributor.sendToPlayer(player, new BossSkinPayload(entityId, skinData.slim, skinData.png));
    }

    private static String sendRenderToPlayer(ServerPlayer player, BossEntity boss) {
        String disguise = resolveDisguise(boss);
        PacketDistributor.sendToPlayer(player, new BossRenderPayload(boss.getId(), disguise));
        return disguise;
    }

    private static String resolveDisguise(BossEntity boss) {
        String disguise = boss.getDisguiseEntity();
        if (disguise != null && !disguise.isBlank()) return disguise;

        BossDefinition def = boss.getDefinition();
        if (def == null && boss.getBossId() != null) {
            def = BossConfigLoader.getDefinition(boss.getBossId());
        }
        if (def == null) return "";
        String value = def.renderEntity != null && !def.renderEntity.isBlank() ? def.renderEntity : def.baseEntity;
        return value != null && !"custom".equalsIgnoreCase(value) ? value : "";
    }
}
