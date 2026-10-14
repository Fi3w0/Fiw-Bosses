package com.fiw.fiw_bosses.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persisted spawn state, data/spawns.json. Times are wall-clock epoch millis so they survive restarts. */
public class SpawnData {
    // bossId -> last successful player summon.
    public Map<String, Long> lastSummon = new HashMap<>();
    // "bossId#entryIndex" -> player UUID -> last summon by that player with that entry.
    public Map<String, Map<String, Long>> playerSummons = new HashMap<>();
    // bossId -> UUIDs of living bosses, including ones in unloaded chunks.
    public Map<String, Set<String>> alive = new HashMap<>();

    public int aliveCount(String bossId) {
        Set<String> set = alive.get(bossId);
        return set == null ? 0 : set.size();
    }

    /** Returns true when the set changed. */
    public boolean addAlive(String bossId, String uuid) {
        return alive.computeIfAbsent(bossId, k -> new LinkedHashSet<>()).add(uuid);
    }

    /** Returns true when the set changed. */
    public boolean removeAlive(String bossId, String uuid) {
        Set<String> set = alive.get(bossId);
        if (set == null || !set.remove(uuid)) return false;
        if (set.isEmpty()) alive.remove(bossId);
        return true;
    }
}
