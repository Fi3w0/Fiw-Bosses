package com.fiw.fiw_bosses.client.skin;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.network.BossSkinPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientSkinManager {

    private static final Map<Integer, ResourceLocation> skinTextures = new ConcurrentHashMap<>();
    private static final Map<Integer, DynamicTexture> textureObjects = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> slimFlags = new ConcurrentHashMap<>();

    public static void handlePayload(BossSkinPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> registerSkin(payload.entityId(), payload.png(), payload.slim()));
    }

    public static void registerSkin(int entityId, byte[] pngData, boolean slim) {
        try {
            removeSkin(entityId);

            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngData));
            DynamicTexture texture = new DynamicTexture(image);

            ResourceLocation texId = ResourceLocation.fromNamespaceAndPath(
                    FiwBosses.MOD_ID, "skin/entity_" + entityId);
            Minecraft.getInstance().getTextureManager().register(texId, texture);

            skinTextures.put(entityId, texId);
            textureObjects.put(entityId, texture);
            slimFlags.put(entityId, slim);
        } catch (Exception e) {
            FiwBosses.LOGGER.error("Failed to register skin texture for entity {}: {}", entityId, e.getMessage());
        }
    }

    public static ResourceLocation getSkinTexture(int entityId) {
        return skinTextures.get(entityId);
    }

    /** Returns true if this entity's skin uses the slim (Alex) arm model. */
    public static boolean isSlim(int entityId) {
        return Boolean.TRUE.equals(slimFlags.get(entityId));
    }

    public static boolean hasSkin(int entityId) {
        return skinTextures.containsKey(entityId);
    }

    public static void removeSkin(int entityId) {
        ResourceLocation old = skinTextures.remove(entityId);
        DynamicTexture oldTex = textureObjects.remove(entityId);
        slimFlags.remove(entityId);
        if (old != null && oldTex != null) {
            Minecraft.getInstance().getTextureManager().release(old);
        }
    }
}
