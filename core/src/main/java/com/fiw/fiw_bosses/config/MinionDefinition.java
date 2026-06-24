package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

public class MinionDefinition {
    public String id;
    public String displayName;

    // "custom" = uses MinionEntity with player model + skin.
    // Any registry id (e.g. "minecraft:zombie") = vanilla mob with stat/equipment overrides.
    public String baseEntity = "custom";

    // Stats
    public float health = 40.0f;
    public float armor = 0.0f;
    public float speed = 0.3f;
    public float knockbackResistance = 0.0f;
    public float attackDamage = 6.0f;

    // Movement: "normal" (chase target), "follow_boss" (escort), "static" (stay in place)
    public String movement = "normal";

    // Skin (only used when baseEntity = "custom")
    public SkinDefinition skin = new SkinDefinition();

    // Equipment (applied to both custom and vanilla base entities)
    public EquipmentConfig equipment;

    // Abilities (only used when baseEntity = "custom")
    public List<AbilityEntry> abilities = new ArrayList<>();

    // Loot dropped on death
    public List<LootEntry> loot = new ArrayList<>();

    public boolean isCustom() {
        return "custom".equalsIgnoreCase(baseEntity);
    }
}
