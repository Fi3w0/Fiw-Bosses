package com.fiw.fiw_bosses.network;

import net.minecraft.network.FriendlyByteBuf;

/** 1.20.1 Forge S2C disguise/render packet (SimpleChannel-style encode/decode). */
public class BossRenderMessage {
    public final int entityId;
    public final String disguiseEntity;

    public BossRenderMessage(int entityId, String disguiseEntity) {
        this.entityId = entityId;
        this.disguiseEntity = disguiseEntity;
    }

    public static void encode(BossRenderMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.disguiseEntity);
    }

    public static BossRenderMessage decode(FriendlyByteBuf buf) {
        return new BossRenderMessage(buf.readVarInt(), buf.readUtf());
    }
}
