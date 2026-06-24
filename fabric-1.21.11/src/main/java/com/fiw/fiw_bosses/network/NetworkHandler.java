package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
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
        PayloadTypeRegistry.playS2C().register(BossRenderPayload.TYPE, BossRenderPayload.STREAM_CODEC);
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
        ServerPlayNetworking.send(player, new BossSkinPayload(entityId, skinData.slim, skinData.png));
    }

    private static String sendRenderToPlayer(ServerPlayer player, BossEntity boss) {
        String disguise = resolveDisguise(boss);
        FiwBossesCore.LOGGER.info("Sending render payload entity={} bossId={} disguise='{}' to {}",
                boss.getId(), boss.getBossId(), disguise, player.getName().getString());
        ServerPlayNetworking.send(player, new BossRenderPayload(boss.getId(), disguise));
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

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BossRenderPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        ClientDisguiseManager.registerDisguise(payload.entityId(), payload.disguiseEntity())));
        ClientPlayNetworking.registerGlobalReceiver(BossSkinPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        {
                            if (!ClientDisguiseManager.hasDisguise(payload.entityId())) {
                                ClientSkinManager.registerSkin(payload.entityId(), payload.png(), payload.slim());
                            }
                        }));
    }
}
