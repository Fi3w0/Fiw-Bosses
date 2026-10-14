package com.fiw.fiw_bosses.config;

/**
 * Limits for player-started spawns (summon items and altars). Admin /boss spawn and
 * command-driven spawns are not limited.
 */
public class SpawnLimits {
    // Refuse a summon while this many of this boss are alive. 0 = unlimited.
    public int maxAlive = 0;
    // Server-wide wait between summons of this boss, by anyone. 0 = none.
    public int cooldownSeconds = 0;
    // Refusal messages. Placeholders: {boss}, {alive}, {max}, {time}.
    public String aliveMessage = "&c{boss}&c is already awake.";
    public String cooldownMessage = "&c{boss}&c can be summoned again in {time}.";
    public String playerCooldownMessage = "&cYou can summon {boss}&c again in {time}.";
}
