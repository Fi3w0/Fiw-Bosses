package com.fiw.fiw_bosses.skin;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.config.SkinDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCache {

    private static final Map<String, SkinData>                    cache   = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<SkinData>> pending = new ConcurrentHashMap<>();

    public static void fetchAll() {
        cache.clear();
        pending.clear();

        for (Map.Entry<String, BossDefinition> entry : BossConfigLoader.getDefinitions().entrySet()) {
            fetchSkin(entry.getKey(), entry.getValue().skin);
        }

        for (Map.Entry<String, MinionDefinition> entry : MinionConfigLoader.getDefinitions().entrySet()) {
            if (entry.getValue().isCustom()) {
                fetchSkin("minion_" + entry.getKey(), entry.getValue().skin);
            }
        }
    }

    private static void fetchSkin(String id, SkinDefinition skin) {
        if (skin == null) return;

        CompletableFuture<SkinData> future;
        if ("file".equalsIgnoreCase(skin.type)) {
            future = SkinFetcher.fetchLocalSkin(skin.value, skin.slim);
        } else {
            future = SkinFetcher.fetchPlayerSkin(skin.value);
        }

        pending.put(id, future);
        future.thenAccept(data -> {
            if (data != null) {
                cache.put(id, data);
                FiwBosses.LOGGER.info("Cached skin for: {} (slim={})", id, data.slim);
            }
            pending.remove(id);
        });
    }

    public static SkinData getSkin(String bossId) {
        return cache.get(bossId);
    }

    public static boolean hasSkin(String bossId) {
        return cache.containsKey(bossId);
    }

    public static CompletableFuture<SkinData> getSkinAsync(String bossId) {
        SkinData cached = cache.get(bossId);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        CompletableFuture<SkinData> p = pending.get(bossId);
        if (p != null) return p;
        return CompletableFuture.completedFuture(null);
    }
}
