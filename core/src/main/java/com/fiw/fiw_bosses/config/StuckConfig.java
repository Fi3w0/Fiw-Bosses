package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Stuck detection: the boss has a target out of melee reach but stayed within
 * {@code radius} blocks for {@code seconds}. Not checked for guard_point/static movement,
 * before the fight starts, or during pre-death dialogue.
 */
public class StuckConfig {
    public boolean enabled = true;
    public int seconds = 8;
    public double radius = 1.5;
    // Run every time the boss gets stuck: teleport_to_target, teleport_attack, teleport_home,
    // heal, despawn, reset, rage:<name>.
    public List<String> actions = new ArrayList<>(List.of("teleport_to_target"));
    // Optional: actions per occurrence (1st, 2nd, ...); the last entry repeats. Overrides actions.
    public List<List<String>> escalation;
    // Melee damage multiplier for teleport_attack's strike.
    public float teleportAttackMultiplier = 1.0f;
    // Sent to nearby players when the despawn action runs. Placeholder: {boss}.
    public String despawnMessage = "&c{boss}&c was stuck and vanished.";

    /** Actions for the n-th stuck occurrence (1-based). */
    public List<String> actionsFor(int occurrence) {
        if (escalation != null && !escalation.isEmpty()) {
            int i = Math.max(0, Math.min(occurrence, escalation.size()) - 1);
            List<String> step = escalation.get(i);
            return step != null ? step : List.of();
        }
        return actions != null ? actions : List.of();
    }
}
