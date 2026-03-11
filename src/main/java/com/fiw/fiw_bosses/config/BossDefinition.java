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
}
