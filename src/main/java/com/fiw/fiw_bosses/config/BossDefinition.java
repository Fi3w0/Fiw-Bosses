package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

public class BossDefinition {
    public String id;
    public String displayName;
    public float health = 200.0f;
    public float armor = 0.0f;
    public float speed = 0.3f;
    public float knockbackResistance = 0.5f;
    public float attackDamage = 10.0f;
    public BossBarConfig bossBar = new BossBarConfig();
    public SkinDefinition skin = new SkinDefinition();
    public EquipmentConfig equipment;
    public List<PhaseDefinition> phases = new ArrayList<>();
    public List<LootEntry> loot = new ArrayList<>();

    // Idle despawn/heal system — triggers when no player is nearby for idleTimeout ticks.
    // idleTimeout <= 0 disables the system entirely (default).
    public int idleTimeout = -1;
    // "despawn" removes the boss entity; "heal" gradually restores HP.
    public String idleAction = "despawn";
    // HP restored per heal interval (only used when idleAction = "heal")
    public float idleHealAmount = 2.0f;
    // Ticks between each heal tick (only used when idleAction = "heal")
    public int idleHealInterval = 40;
}
