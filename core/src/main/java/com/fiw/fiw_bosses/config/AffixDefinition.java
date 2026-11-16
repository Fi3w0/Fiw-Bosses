package com.fiw.fiw_bosses.config;

import java.util.List;

/**
 * One spawn affix: a name prefix plus stat changes a boss may roll when it spawns.
 * Every field is optional. In a boss's pool an entry that only sets {@code id} (and maybe
 * {@code weight}) takes everything else from the settings.json {@code affixes} library entry with
 * that id; fields the pool entry does set win over the library.
 */
public class AffixDefinition {
    // Affix id. Required in a boss's pool; in the settings.json library the key is the id.
    public String id;
    // Shown before the boss name, with & colour codes, e.g. "&bSwift".
    public String name;
    // Relative chance to be picked from the pool. Default 1; 0 never picks it.
    public Float weight;
    // Multiplies the boss's health.
    public Float healthMultiplier;
    // Multiplies all damage the boss deals.
    public Float damageMultiplier;
    // Multiplies melee hits only.
    public Float meleeDamageMultiplier;
    // Multiplies ability hits only.
    public Float abilityDamageMultiplier;
    // Multiplies movement speed.
    public Float speedMultiplier;
    // Added to the boss's armor.
    public Float armorBonus;
    // Multiplies loot counts (count / minCount / maxCount). Unique drops stay at one.
    public Float lootMultiplier;
    // Extra abilities the boss uses in every phase and every rage that lists its own abilities.
    public List<AbilityEntry> abilities;
}
