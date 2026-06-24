package com.fiw.fiw_bosses.client.skin;

import com.fiw.fiw_bosses.client.renderer.ClientDisguiseManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.network.BossSkinPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientSkinManager {

    private static final Map<Integer, ResourceLocation> SKIN_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<Integer, DynamicTexture> TEXTURE_OBJECTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> SLIM_FLAGS = new ConcurrentHashMap<>();

    private ClientSkinManager() {}

    public static void handlePayload(BossSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!ClientDisguiseManager.hasDisguise(payload.entityId())) {
                registerSkin(payload.entityId(), payload.png(), payload.slim());
            }
        });
    }

    public static void registerSkin(int entityId, byte[] pngData, boolean slim) {
        try {
            removeSkin(entityId);

            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngData));
            DynamicTexture texture = new DynamicTexture(() -> "fiw_bosses skin " + entityId, image);
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "skin/entity_" + entityId);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);

            SKIN_TEXTURES.put(entityId, textureId);
            TEXTURE_OBJECTS.put(entityId, texture);
            SLIM_FLAGS.put(entityId, slim);
        } catch (Exception e) {
            FiwBossesCore.LOGGER.error("Failed to register skin texture for entity {}: {}", entityId, e.getMessage());
        }
    }

    public static ResourceLocation getSkinTexture(int entityId) {
        return SKIN_TEXTURES.get(entityId);
    }

    public static boolean isSlim(int entityId) {
        return Boolean.TRUE.equals(SLIM_FLAGS.get(entityId));
    }

    public static void removeSkin(int entityId) {
        ResourceLocation old = SKIN_TEXTURES.remove(entityId);
        DynamicTexture oldTexture = TEXTURE_OBJECTS.remove(entityId);
        SLIM_FLAGS.remove(entityId);
        if (old != null && oldTexture != null) {
            Minecraft.getInstance().getTextureManager().release(old);
        }
    }
}
