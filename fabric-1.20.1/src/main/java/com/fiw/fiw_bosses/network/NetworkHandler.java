package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.20.1 networking: pre-CustomPayload, so a plain S2C channel id + manual
 * {@link FriendlyByteBuf} encode/decode is used instead of a packet codec.
 */
public final class NetworkHandler {
    private NetworkHandler() {}

    public static final ResourceLocation BOSS_SKIN =
            new ResourceLocation(FiwBossesCore.MOD_ID, "boss_skin");
    public static final ResourceLocation BOSS_RENDER =
            new ResourceLocation(FiwBossesCore.MOD_ID, "boss_render");

    /** No global registration needed on 1.20.1; kept for parity with the loader init call. */
    public static void registerPayloadTypes() {
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
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(entityId);
        buf.writeBoolean(skinData.slim);
        buf.writeByteArray(skinData.png);
        ServerPlayNetworking.send(player, BOSS_SKIN, buf);
    }

    private static String sendRenderToPlayer(ServerPlayer player, BossEntity boss) {
        String disguise = resolveDisguise(boss);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(boss.getId());
        buf.writeUtf(disguise);
        ServerPlayNetworking.send(player, BOSS_RENDER, buf);
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
        ClientPlayNetworking.registerGlobalReceiver(BOSS_RENDER, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String disguise = buf.readUtf();
            client.execute(() -> ClientDisguiseManager.registerDisguise(entityId, disguise));
        });
        ClientPlayNetworking.registerGlobalReceiver(BOSS_SKIN, (client, handler, buf, responseSender) -> {
            // Read off the network thread, then apply on the client thread.
            int entityId = buf.readVarInt();
            boolean slim = buf.readBoolean();
            byte[] png = buf.readByteArray();
            client.execute(() -> {
                if (!ClientDisguiseManager.hasDisguise(entityId)) {
                    ClientSkinManager.registerSkin(entityId, png, slim);
                }
            });
        });
    }
}
