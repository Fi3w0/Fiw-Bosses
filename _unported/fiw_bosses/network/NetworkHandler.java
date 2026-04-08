package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class NetworkHandler {

    public static final Identifier BOSS_SKIN_CHANNEL = new Identifier(FiwBosses.MOD_ID, "boss_skin");

    public static void registerServerPackets() {
        // Server doesn't receive skin packets, only sends them
    }

    public static void sendSkinToPlayer(ServerPlayerEntity player, BossEntity boss) {
        if (boss.getBossId() == null) return;

        SkinData skinData = SkinCache.getSkin(boss.getBossId());
        if (skinData == null) {
            SkinCache.getSkinAsync(boss.getBossId()).thenAccept(data -> {
                if (data != null) {
                    player.server.execute(() -> doSendSkin(player, boss.getId(), data));
                }
            });
            return;
        }

        doSendSkin(player, boss.getId(), skinData);
    }

    private static void doSendSkin(ServerPlayerEntity player, int entityId, SkinData skinData) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeBoolean(skinData.slim);
        buf.writeByteArray(skinData.png);
        ServerPlayNetworking.send(player, BOSS_SKIN_CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BOSS_SKIN_CHANNEL, (client, handler, buf, responseSender) -> {
            int     entityId = buf.readInt();
            boolean slim     = buf.readBoolean();
            byte[]  skinData = buf.readByteArray();

            client.execute(() -> ClientSkinManager.registerSkin(entityId, skinData, slim));
        });
    }
}
