package com.fiw.fiw_bosses.network;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BossRenderPayload(int entityId, String disguiseEntity) implements CustomPacketPayload {
    public static final Type<BossRenderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "boss_render"));

    public static final StreamCodec<FriendlyByteBuf, BossRenderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossRenderPayload::entityId,
            ByteBufCodecs.STRING_UTF8, BossRenderPayload::disguiseEntity,
            BossRenderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
