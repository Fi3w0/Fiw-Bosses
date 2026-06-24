package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

public class PhaseDefinition {
    public float hpThresholdPercent = 1.0f;
    public float speedMultiplier = 1.0f;
    public float damageMultiplier = 1.0f;
    public List<AbilityEntry> abilities = new ArrayList<>();
    public List<MinionEntry> minions = new ArrayList<>();
    public EquipmentConfig equipment;
    public String transitionMessage;
    public String transitionSound;
    public String transitionParticle;
}
