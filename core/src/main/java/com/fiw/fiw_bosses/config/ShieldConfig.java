package com.fiw.fiw_bosses.config;

/**
 * Shield phase: when a phase (or rage) with a shield starts, the boss summons pylons — any
 * FIW custom minion — around itself and takes no damage until all of them are destroyed.
 */
public class ShieldConfig {
    // Minion id from config/fiw_bosses/minions/ (must use "baseEntity": "custom").
    public String minion;
    public int count = 4;
    // Blocks from the boss where the pylons appear, evenly spaced on a ring.
    public double radius = 8.0;
    // Placeholders: {boss}, {count}, {left}. Empty = silent.
    public String message = "&b{boss}&b raises a shield! Destroy the {count} pylons.";
    public String brokenMessage = "&b{boss}&b's shield shatters!";
    public String blockedMessage = "&7The shield absorbs the hit. {left} pylon(s) left.";
}
