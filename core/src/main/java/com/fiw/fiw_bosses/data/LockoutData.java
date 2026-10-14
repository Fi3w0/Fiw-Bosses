package com.fiw.fiw_bosses.data;

import java.util.HashMap;
import java.util.Map;

/** Persisted loot lockouts, data/lockouts.json: key ("bossId" or "bossId#variant") -> player UUID -> until (epoch ms). */
public class LockoutData {
    public Map<String, Map<String, Long>> until = new HashMap<>();

    public static String key(String bossId, String variant, boolean perVariant) {
        return perVariant && variant != null && !variant.isEmpty() ? bossId + "#" + variant : bossId;
    }

    /** Milliseconds left, or 0 when not locked out. */
    public long remaining(String key, String uuid, long now) {
        Map<String, Long> players = until.get(key);
        Long t = players != null ? players.get(uuid) : null;
        return t != null && t > now ? t - now : 0;
    }

    public void lock(String key, String uuid, long now, float hours) {
        if (hours <= 0) return;
        until.computeIfAbsent(key, k -> new HashMap<>()).put(uuid, now + (long) (hours * 3_600_000L));
    }

    /** Drops expired entries so the file doesn't grow forever. */
    public void prune(long now) {
        until.values().forEach(m -> m.values().removeIf(t -> t <= now));
        until.values().removeIf(Map::isEmpty);
    }
}
