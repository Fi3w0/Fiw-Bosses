package com.fiw.fiw_bosses.config;

/**
 * A behavior-triggered phase. Its JSON holds any phase field (abilities, multipliers,
 * equipment, protection, transition message/sound/particle) that overrides the current
 * HP phase while active, plus these rage-only fields. Triggers: stuck action
 * "rage:<name>", antiCheese.vehicleRage, and the enrage timer (afterSeconds).
 */
public class RageDefinition {
    // How long the rage lasts. 0 = until the fight ends or the arena leash resets the boss.
    public int durationSeconds = 0;
    // Enrage timer: trigger automatically this long after the fight started. 0 = off.
    public int afterSeconds = 0;
    // Raises max health by this percent while active (current health rises by the same amount).
    public float extraHealthPercent = 0;
    // Heals this percent of max health when the rage starts.
    public float healPercent = 0;
    // Boss bar color while active (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE).
    public String bossBarColor;
}
