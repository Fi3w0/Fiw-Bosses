package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
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
        // Server doesn't receive packets for skins, only sends
    }

    public static void sendSkinToPlayer(ServerPlayerEntity player, BossEntity boss) {
        if (boss.getBossId() == null) return;

        byte[] skinData = SkinCache.getSkin(boss.getBossId());
        if (skinData == null) {
            // Skin might still be loading, try async
            SkinCache.getSkinAsync(boss.getBossId()).thenAccept(bytes -> {
                if (bytes != null) {
                    player.server.execute(() -> doSendSkin(player, boss.getId(), bytes));
                }
            });
            return;
        }

        doSendSkin(player, boss.getId(), skinData);
    }

    private static void doSendSkin(ServerPlayerEntity player, int entityId, byte[] skinData) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeByteArray(skinData);
        ServerPlayNetworking.send(player, BOSS_SKIN_CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BOSS_SKIN_CHANNEL, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            byte[] skinData = buf.readByteArray();

            client.execute(() -> {
                ClientSkinManager.registerSkin(entityId, skinData);
            });
        });
    }
}
