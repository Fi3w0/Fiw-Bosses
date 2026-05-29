package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class NetworkHandler {

    public record BossSkinPayload(int entityId, boolean slim, byte[] png) implements CustomPayload {
        public static final CustomPayload.Id<BossSkinPayload> ID =
                new CustomPayload.Id<>(Identifier.of(FiwBosses.MOD_ID, "boss_skin"));

        public static final PacketCodec<PacketByteBuf, BossSkinPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeInt(value.entityId);
                    buf.writeBoolean(value.slim);
                    buf.writeByteArray(value.png);
                },
                buf -> new BossSkinPayload(buf.readInt(), buf.readBoolean(), buf.readByteArray())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Called from {@code FiwBosses.onInitialize} — payload types live on both sides. */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(BossSkinPayload.ID, BossSkinPayload.CODEC);
    }

    public static void registerServerPackets() {
        // Server doesn't receive skin packets, only sends them.
    }

    public static void sendSkinToPlayer(ServerPlayerEntity player, BossEntity boss) {
        if (boss.getBossId() == null) return;

        SkinData skinData = SkinCache.getSkin(boss.getBossId());
        if (skinData == null) {
            SkinCache.getSkinAsync(boss.getBossId()).thenAccept(data -> {
                if (data != null) {
                    player.getEntityWorld().getServer().execute(() -> doSendSkin(player, boss.getId(), data));
                }
            });
            return;
        }

        doSendSkin(player, boss.getId(), skinData);
    }

    private static void doSendSkin(ServerPlayerEntity player, int entityId, SkinData skinData) {
        ServerPlayNetworking.send(player, new BossSkinPayload(entityId, skinData.slim, skinData.png));
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BossSkinPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        ClientSkinManager.registerSkin(payload.entityId(), payload.png(), payload.slim())));
    }
}
