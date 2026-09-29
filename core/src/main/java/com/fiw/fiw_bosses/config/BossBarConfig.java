package com.fiw.fiw_bosses.config;

public class BossBarConfig {
    public String color = "RED";
    public String overlay = "PROGRESS";
    // Append the boss's real HP ("1,500/1,500") to the bar title. Null = use the
    // showBossBarHealth default from settings.json. Shows configured health even above
    // the 1024 attribute cap (health mods like Jade only see the capped value).
    public Boolean showHealth;
}
