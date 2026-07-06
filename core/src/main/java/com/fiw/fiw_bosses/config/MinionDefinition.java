package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MinionDefinition {
    public String id;
    public String displayName;

    // "custom" = uses MinionEntity with player model + skin.
    // Any registry id (e.g. "minecraft:zombie") = vanilla mob with stat/equipment overrides.
    public String baseEntity = "custom";
    // Optional visual disguise for custom minions. Example: "minecraft:zombie".
    public String renderEntity;

    // Stats
    public float health = 40.0f;
    public float armor = 0.0f;
    public float speed = 0.3f;
    public float knockbackResistance = 0.0f;
    public float attackDamage = 6.0f;

    // Movement: "normal"/"side" (chase + strafe), "vanilla" (no smart movement),
    // "follow_boss" (escort), "static" (stay in place), or one of the boss movement modes.
    public String movement = "normal";

    // Skin (only used when baseEntity = "custom")
    public SkinDefinition skin = new SkinDefinition();

    // Equipment (applied to both custom and vanilla base entities)
    public EquipmentConfig equipment;

    // Abilities (only used when baseEntity = "custom")
    public List<AbilityEntry> abilities = new ArrayList<>();

    // Loot dropped on death
    public List<LootEntry> loot = new ArrayList<>();

    // Incoming damage multipliers (same key format as BossDefinition.protection).
    // Only used when baseEntity = "custom".
    public Map<String, Float> protection;

    public boolean isCustom() {
        return "custom".equalsIgnoreCase(baseEntity);
    }
}
