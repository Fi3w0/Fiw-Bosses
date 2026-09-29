package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Raid-style reward settings for a boss. When the whole block is absent the boss keeps
 * the classic behavior: its loot table drops once on the ground.
 */
public class RewardsConfig {
    // "shared" (default): loot drops once on the ground, like before.
    // "per_player": every eligible player rolls the loot table on their own and the
    // items go straight into their inventory (overflow drops at their feet, pickable
    // only by them). Entries marked "unique" go to exactly one eligible player.
    public String lootMode = "shared";
    // A player must deal at least this % of the boss's max health to be eligible for
    // per-player loot and participant/MVP commands. Stops one-hit leeching.
    public float minDamagePercent = 5.0f;
    // 0 = every eligible player has the same chances. 1 = chances scale fully with
    // contribution (a player who dealt twice the average damage gets twice the chance).
    // Per-entry "damageLuck" overrides this.
    public float damageLuck = 0.0f;
    // Upper bound for any luck-boosted drop chance.
    public float maxChance = 1.0f;
    // Run once when the boss dies. Placeholders: {boss} {killer} {mvp} {x} {y} {z} {participants}
    public List<String> commands = new ArrayList<>();
    // Run once for every eligible player. Placeholders: {player} {damage} {percent} {rank} {boss}
    public List<String> participantCommands = new ArrayList<>();
    // Run once for the top damage dealer. Same placeholders as participantCommands.
    public List<String> mvpCommands = new ArrayList<>();

    public boolean perPlayer() {
        return "per_player".equalsIgnoreCase(lootMode);
    }
}
