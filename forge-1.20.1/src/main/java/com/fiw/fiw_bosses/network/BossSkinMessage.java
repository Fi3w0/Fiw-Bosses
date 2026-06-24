package com.fiw.fiw_bosses.network;

import net.minecraft.network.FriendlyByteBuf;

/** 1.20.1 Forge S2C skin packet (SimpleChannel-style encode/decode). */
public class BossSkinMessage {
    public final int entityId;
    public final boolean slim;
    public final byte[] png;

    public BossSkinMessage(int entityId, boolean slim, byte[] png) {
        this.entityId = entityId;
        this.slim = slim;
        this.png = png;
    }

    public static void encode(BossSkinMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.slim);
        buf.writeByteArray(msg.png);
    }

    public static BossSkinMessage decode(FriendlyByteBuf buf) {
        return new BossSkinMessage(buf.readVarInt(), buf.readBoolean(), buf.readByteArray());
    }
}
