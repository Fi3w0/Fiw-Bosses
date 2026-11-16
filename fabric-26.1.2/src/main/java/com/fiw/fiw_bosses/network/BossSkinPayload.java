package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BossSkinPayload(int entityId, boolean slim, byte[] png) implements CustomPacketPayload {
    public static final Type<BossSkinPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "boss_skin"));

    public static final StreamCodec<FriendlyByteBuf, BossSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossSkinPayload::entityId,
            ByteBufCodecs.BOOL, BossSkinPayload::slim,
            ByteBufCodecs.byteArray(262144), BossSkinPayload::png,
            BossSkinPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
