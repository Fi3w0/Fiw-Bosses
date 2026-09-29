package com.fiw.fiw_bosses.config;

public class LootEntry {
    public String item;
    public String nbt;
    /** Optional Fiw Tools item id. When set, takes precedence over {@link #item} and {@link #nbt}. */
    public String toolId;
    public int count = 1;
    // Optional random count range. When either is set it overrides {@link #count};
    // a missing bound defaults to the other one. A rolled 0 drops nothing.
    public Integer minCount;
    public Integer maxCount;
    public float chance = 1.0f;
    // Per-player loot mode only (see RewardsConfig):
    // true = rolled once for the whole raid and given to exactly one eligible player,
    // so there is never a second copy (e.g. a one-of-a-kind Fiw Tools weapon).
    public boolean unique = false;
    // Overrides rewards.damageLuck for this entry (0 = equal odds, 1 = fully damage-weighted).
    public Float damageLuck;
    // Only the top N damage dealers may receive this entry. 0 = every eligible player.
    public int topRanks = 0;
}
