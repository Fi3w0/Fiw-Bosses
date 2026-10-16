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
    // Custom minions only. Size multiplier for model and hitbox: 1 = normal, 2 = twice as big (0.0625-16).
    // The hitbox starts from renderEntity's mob size when a disguise is set.
    public float scale = 1.0f;
    // Extra melee reach in blocks (0-16). Vanilla reach already grows with the hitbox.
    public float reach = 0.0f;

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

    // ── Factions (only used when baseEntity = "custom") ──────────────────────
    // Optional faction id. Entities sharing a non-empty faction are allies.
    public String faction;
    // Whether this minion's attacks/abilities can damage same-faction allies.
    public boolean damageFactionAllies = false;
    // Whether this minion's AI may target/retaliate against same-faction allies.
    public boolean targetFactionAllies = false;
    // Whether this minion's attacks can damage its own group
    // (owner boss and sibling minions).
    public boolean damageOwnGroup = false;

    // ── Water / lava behavior (only used when baseEntity = "custom") ────────
    // Optional. See FluidSettings: drownImmune, fireImmune, floats, swimSpeed,
    // pushedByFluids, canSwim. Absent = vanilla behavior.
    public FluidSettings fluid;

    public boolean isCustom() {
        return "custom".equalsIgnoreCase(baseEntity);
    }

    // ── Anti-cheese ──────────────────────────────────────────────────────────
    // On by default: a minion that makes no progress toward its target for stuckSeconds
    // (blocked, boxed, running in circles) teleports next to it; with no target it
    // teleports back to its owner boss when far away.
    public boolean teleportWhenStuck = true;
    // Optional: "vanilla" or "shared" path finding; absent = settings.json "pathfinding".
    public String pathfinding;
    public int stuckSeconds = 5;
}
