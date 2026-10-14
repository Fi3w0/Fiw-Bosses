package com.fiw.fiw_bosses.config;

import java.util.HashMap;
import java.util.Map;

/** Anti-cheese options for a boss. Bosses never break blocks; players may build. */
public class AntiCheeseConfig {
    // Opt-in: absent = no stuck detection.
    public StuckConfig stuck;
    // On by default: a boss put in a boat/minecart destroys it immediately.
    public boolean destroyVehicles = true;
    // Optional rage name triggered when that happens.
    public String vehicleRage;
    // Opt-in: cobwebs, powder snow, berry bushes, honey, soul sand... don't slow the boss.
    public SlowBlocks ignoreSlowBlocks;

    public static class SlowBlocks {
        public boolean enabled = true;
        // Per-block override: "minecraft:powder_snow": false keeps that block's slowdown.
        public Map<String, Boolean> blocks = new HashMap<>();

        public boolean ignores(String blockId) {
            if (!enabled) return false;
            Boolean b = blocks != null ? blocks.get(blockId) : null;
            return b == null || b;
        }
    }

    public static AntiCheeseConfig defaults() {
        return new AntiCheeseConfig();
    }
}
