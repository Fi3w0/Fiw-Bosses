package com.fiw.fiw_bosses.client.skin;

import com.fiw.fiw_bosses.FiwBosses;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class ClientSkinManager {

    private static final Map<Integer, Identifier> skinTextures = new ConcurrentHashMap<>();
    private static final Map<Integer, NativeImageBackedTexture> textureObjects = new ConcurrentHashMap<>();

    public static void registerSkin(int entityId, byte[] pngData) {
        try {
            // Remove old texture if exists
            removeSkin(entityId);

            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngData));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);

            Identifier texId = new Identifier(FiwBosses.MOD_ID, "skin/entity_" + entityId);

            MinecraftClient.getInstance().getTextureManager().registerTexture(texId, texture);
            skinTextures.put(entityId, texId);
            textureObjects.put(entityId, texture);
        } catch (Exception e) {
            FiwBosses.LOGGER.error("Failed to register skin texture for entity {}: {}", entityId, e.getMessage());
        }
    }

    public static Identifier getSkinTexture(int entityId) {
        return skinTextures.get(entityId);
    }

    public static boolean hasSkin(int entityId) {
        return skinTextures.containsKey(entityId);
    }

    public static void removeSkin(int entityId) {
        Identifier old = skinTextures.remove(entityId);
        NativeImageBackedTexture oldTex = textureObjects.remove(entityId);
        if (old != null && oldTex != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
    }
}
