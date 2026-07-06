package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossDefinition {
    public String id;
    public String displayName;
    public float health = 200.0f;
    public float armor = 0.0f;
    public float speed = 0.3f;
    public float knockbackResistance = 0.5f;
    public float attackDamage = 10.0f;
    public String movement = "side";
    public String baseEntity = "custom";
    public String renderEntity;
    public BossBarConfig bossBar = new BossBarConfig();
    public SkinDefinition skin = new SkinDefinition();
    public EquipmentConfig equipment;
    public List<PhaseDefinition> phases = new ArrayList<>();
    public List<LootEntry> loot = new ArrayList<>();

    // ── Damage protection ────────────────────────────────────────────────────
    // Incoming damage multipliers. Keys: weapon item id ("minecraft:mace"),
    // damage type id ("minecraft:mace_smash"), or category ("melee", "projectile",
    // "magic", "fire", "explosion", "fall", "lightning", "freezing", "drowning").
    // Value 0.3 = takes 30% damage, 0 = immune. Precedence: item > type > category.
    public Map<String, Float> protection;

    // ── Factions ─────────────────────────────────────────────────────────────
    // Optional faction id. Entities sharing a non-empty faction are allies.
    public String faction;
    // Whether this entity's attacks/abilities can damage same-faction allies.
    public boolean damageFactionAllies = false;
    // Whether this entity's AI may target/retaliate against same-faction allies.
    public boolean targetFactionAllies = false;
    // Whether this entity's attacks can damage its own minion group
    // (boss -> its summoned minions; minion -> its owner boss and siblings).
    public boolean damageOwnGroup = false;

    // ── Idle system ──────────────────────────────────────────────────────────
    // Triggers when no player is nearby for idleTimeout ticks. <= 0 disables.
    public int idleTimeout = -1;
    public String idleAction = "despawn";  // "despawn" or "heal"
    public float idleHealAmount = 2.0f;
    public int idleHealInterval = 40;

    // ── Pre-fight activation & dialogue ─────────────────────────────────────
    // When non-empty the boss starts INACTIVE (passive, immortal, no boss bar).
    // A player right-clicks the boss to activate it; the boss speaks each line
    // with dialogueLineDelay ticks between them, then the fight begins.
    // When empty (default) the boss starts fighting immediately on spawn.
    public List<String> preFightDialogue = new ArrayList<>();
    // Ticks between each dialogue line (60 = 3 s).
    public int dialogueLineDelay = 60;

    // ── Pre-death dialogue ───────────────────────────────────────────────────
    // When non-empty the boss is held at 1 HP on what would be a lethal hit,
    // becomes invulnerable, speaks each line, then actually dies.
    // When empty (default) the boss dies normally.
    public List<String> preDeathDialogue = new ArrayList<>();
    // Ticks between each pre-death dialogue line (40 = 2 s).
    public int preDeathDialogueDelay = 40;
}
