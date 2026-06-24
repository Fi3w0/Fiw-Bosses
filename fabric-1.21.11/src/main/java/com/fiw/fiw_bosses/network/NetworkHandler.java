package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(BossSkinPayload.TYPE, BossSkinPayload.STREAM_CODEC);
    }

    public static void sendSkinToPlayer(ServerPlayer player, BossEntity boss) {
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
        ServerPlayNetworking.send(player, new BossSkinPayload(entityId, skinData.slim, skinData.png));
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BossSkinPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        ClientSkinManager.registerSkin(payload.entityId(), payload.png(), payload.slim())));
    }
}
