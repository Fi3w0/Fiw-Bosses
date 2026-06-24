package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 1.20.1 Forge networking via {@link SimpleChannel} (no CustomPacketPayload).
 */
public final class NetworkHandler {
    private NetworkHandler() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FiwBossesCore.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, BossSkinMessage.class,
                BossSkinMessage::encode, BossSkinMessage::decode, NetworkHandler::handleClient,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handleClient(BossSkinMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                // Deferred classloading so the dedicated server never touches client classes.
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.fiw.fiw_bosses.client.skin.ClientSkinManager.registerSkin(msg.entityId, msg.png, msg.slim)));
        ctx.setPacketHandled(true);
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BossSkinMessage(entityId, skinData.slim, skinData.png));
    }
}
