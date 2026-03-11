package com.fiw.fiw_bosses.skin;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.SkinDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCache {

    private static final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();

    public static void fetchAll() {
        cache.clear();
        pending.clear();

        for (Map.Entry<String, BossDefinition> entry : BossConfigLoader.getDefinitions().entrySet()) {
            String bossId = entry.getKey();
            SkinDefinition skin = entry.getValue().skin;
            if (skin == null) continue;

            CompletableFuture<byte[]> future;
            if ("file".equalsIgnoreCase(skin.type)) {
                future = SkinFetcher.fetchLocalSkin(skin.value);
            } else {
                future = SkinFetcher.fetchPlayerSkin(skin.value);
            }

            pending.put(bossId, future);
            future.thenAccept(bytes -> {
                if (bytes != null) {
                    cache.put(bossId, bytes);
                    FiwBosses.LOGGER.info("Cached skin for boss: {}", bossId);
                }
                pending.remove(bossId);
            });
        }
    }

    public static byte[] getSkin(String bossId) {
        return cache.get(bossId);
    }

    public static boolean hasSkin(String bossId) {
        return cache.containsKey(bossId);
    }

    public static CompletableFuture<byte[]> getSkinAsync(String bossId) {
        byte[] cached = cache.get(bossId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<byte[]> p = pending.get(bossId);
        if (p != null) {
            return p;
        }
        return CompletableFuture.completedFuture(null);
    }
}
