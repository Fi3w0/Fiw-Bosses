package com.fiw.fiw_bosses.config;

/**
 * Arena leash: a boss pulled further than {@code radius} blocks from where it spawned
 * resets — back home, full health, phase 1, fight ledger cleared (no loot for that attempt).
 */
public class ArenaConfig {
    // Blocks from the spawn point. 0 or less disables the leash.
    public double radius = 0;
    // Sent to players near the boss on reset. Placeholder: {boss}. Empty = silent.
    public String resetMessage = "&7{boss}&7 retreats to its lair and recovers.";
    // What happens to the boss's own minions on reset: "despawn" or "teleport" (to the boss).
    public String minionsOnReset = "despawn";

    public boolean enabled() {
        return radius > 0;
    }

    public boolean teleportMinions() {
        return "teleport".equalsIgnoreCase(minionsOnReset);
    }
}
