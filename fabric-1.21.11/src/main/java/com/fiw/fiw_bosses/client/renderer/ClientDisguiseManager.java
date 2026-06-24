package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDisguiseManager {
    private static final Map<Integer, String> DISGUISES = new ConcurrentHashMap<>();

    private ClientDisguiseManager() {}

    public static void registerDisguise(int entityId, String disguiseEntity) {
        if (disguiseEntity == null || disguiseEntity.isBlank()) {
            FiwBossesCore.LOGGER.info("Client cleared disguise for entity {}", entityId);
            DISGUISES.remove(entityId);
        } else {
            FiwBossesCore.LOGGER.info("Client registered disguise for entity {}: {}", entityId, disguiseEntity);
            DISGUISES.put(entityId, disguiseEntity);
            ClientSkinManager.removeSkin(entityId);
        }
    }

    public static String getDisguise(int entityId) {
        return DISGUISES.get(entityId);
    }

    public static boolean hasDisguise(int entityId) {
        return DISGUISES.containsKey(entityId);
    }

    public static void removeDisguise(int entityId) {
        DISGUISES.remove(entityId);
    }
}
