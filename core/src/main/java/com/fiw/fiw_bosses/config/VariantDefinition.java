package com.fiw.fiw_bosses.config;

import java.util.List;

/** A difficulty variant (e.g. "hard"), picked by the summon item or /boss spawn. */
public class VariantDefinition {
    // Optional name override for the nameplate and boss bar.
    public String displayName;
    public float healthMultiplier = 1.0f;
    // All damage the boss deals.
    public float damageMultiplier = 1.0f;
    // Melee hits only.
    public float meleeDamageMultiplier = 1.0f;
    // Ability hits only.
    public float abilityDamageMultiplier = 1.0f;
    // Multiplies loot counts (count / minCount / maxCount). Unique drops stay at one.
    public float lootMultiplier = 1.0f;
    // Optional loot table that replaces the boss's own loot for this variant.
    public List<LootEntry> loot;
}
