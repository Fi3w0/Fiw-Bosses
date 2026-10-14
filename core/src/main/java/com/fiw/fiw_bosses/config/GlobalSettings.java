package com.fiw.fiw_bosses.config;

/** Server-wide settings from config/fiw_bosses/settings.json. */
public class GlobalSettings {
    // Default post-fight summary for every boss; a boss's own fightSummary overrides it.
    public FightSummaryConfig fightSummary = FightSummaryConfig.defaults();
    // A player only counts as being in a fight (damage taken, deaths) while within this
    // many blocks of the boss and actually engaged: they hit it, or it is targeting them.
    public int combatTrackingRange = 48;
    // Default for every boss's bossBar.showHealth: append real HP to the boss bar title.
    public boolean showBossBarHealth = false;
    // Discord webhook that gets a fight summary embed on every boss kill. Empty = off.
    // A boss's own "webhook" overrides or disables it. Never share this URL.
    public WebhookConfig webhooks = new WebhookConfig();

    /** Fills fields a partial or old settings.json left null. */
    void normalize() {
        FightSummaryConfig defaults = FightSummaryConfig.defaults();
        fightSummary = fightSummary == null ? defaults : fightSummary.over(defaults);
        if (combatTrackingRange <= 0) combatTrackingRange = 48;
        if (webhooks == null) webhooks = new WebhookConfig();
    }
}
