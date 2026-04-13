package com.fiw.fiw_bosses.config;

public class MinionEntry {
    // New system: references a minion definition JSON by id.
    public String minionId;

    // Legacy: plain vanilla entity type (e.g. "minecraft:zombie").
    // Kept for backward compatibility — if minionId is null/empty, falls back to this.
    public String entityType;

    public int count = 1;
    public int maxAlive = 4;
    public float spawnRadius = 5.0f;

    public boolean usesDefinition() {
        return minionId != null && !minionId.isEmpty();
    }
}
