package com.fiw.fiw_bosses.config;

/**
 * Post-fight chat summary. The global defaults live in settings.json; a boss may carry
 * its own "fightSummary" block where every field is optional and overrides the global
 * value key-by-key (a missing/null field falls back to the global setting).
 */
public class FightSummaryConfig {
    public Boolean enabled;
    public Boolean showDamageDealt;
    public Boolean showDamageTaken;
    public Boolean showDeaths;
    public Boolean showUniqueDrops;
    public Boolean showKillingBlow;
    public Boolean showDuration;
    // Rows shown per ranking.
    public Integer topCount;
    // Besides every participant, players within this many blocks of the boss see the summary.
    public Integer broadcastRange;

    /** Built-in defaults used when settings.json has no fightSummary block. */
    public static FightSummaryConfig defaults() {
        FightSummaryConfig c = new FightSummaryConfig();
        c.enabled = false;
        c.showDamageDealt = true;
        c.showDamageTaken = true;
        c.showDeaths = true;
        c.showUniqueDrops = true;
        c.showKillingBlow = true;
        c.showDuration = true;
        c.topCount = 3;
        c.broadcastRange = 64;
        return c;
    }

    /** This config's non-null fields over {@code base}; base must be fully populated. */
    public FightSummaryConfig over(FightSummaryConfig base) {
        FightSummaryConfig r = new FightSummaryConfig();
        r.enabled         = enabled != null ? enabled : base.enabled;
        r.showDamageDealt = showDamageDealt != null ? showDamageDealt : base.showDamageDealt;
        r.showDamageTaken = showDamageTaken != null ? showDamageTaken : base.showDamageTaken;
        r.showDeaths      = showDeaths != null ? showDeaths : base.showDeaths;
        r.showUniqueDrops = showUniqueDrops != null ? showUniqueDrops : base.showUniqueDrops;
        r.showKillingBlow = showKillingBlow != null ? showKillingBlow : base.showKillingBlow;
        r.showDuration    = showDuration != null ? showDuration : base.showDuration;
        r.topCount        = topCount != null ? topCount : base.topCount;
        r.broadcastRange  = broadcastRange != null ? broadcastRange : base.broadcastRange;
        return r;
    }
}
