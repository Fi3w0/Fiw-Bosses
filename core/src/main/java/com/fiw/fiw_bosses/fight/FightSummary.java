package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.config.FightSummaryConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the post-fight chat summary as {@code &}-coded lines. Only players with a
 * non-zero value appear in each section, so bystanders never show up as "0 damage".
 */
public final class FightSummary {

    private FightSummary() {}

    /**
     * @param uniqueDrops player name → item display name, for unique loot winners
     * @param killer      name of whoever landed the killing blow, or null
     */
    public static List<String> lines(String bossName, FightLedger ledger, FightSummaryConfig cfg,
                                     long durationTicks, String killer, Map<String, String> uniqueDrops) {
        List<String> out = new ArrayList<>();
        int top = Math.max(1, cfg.topCount != null ? cfg.topCount : 3);

        String header = "&6&l⚔ &r" + bossName + " &6&lhas been defeated!";
        if (Boolean.TRUE.equals(cfg.showDuration) && durationTicks > 0) {
            header += " &7(" + formatDuration(durationTicks) + ")";
        }
        out.add(header);

        if (Boolean.TRUE.equals(cfg.showKillingBlow) && killer != null && !killer.isEmpty()) {
            out.add("&7Killing blow: &f" + killer);
        }

        if (Boolean.TRUE.equals(cfg.showDamageDealt)) {
            List<FightLedger.Entry> dealt = ledger.byDamageDealt();
            double total = ledger.totalDealt();
            if (!dealt.isEmpty()) {
                out.add("&c&lTop damage");
                for (int i = 0; i < Math.min(top, dealt.size()); i++) {
                    FightLedger.Entry e = dealt.get(i);
                    int pct = total > 0 ? (int) Math.round(e.damageDealt * 100.0 / total) : 0;
                    out.add(" &7" + (i + 1) + ". &f" + e.name + " &c" + formatNumber(e.damageDealt)
                            + " &7(" + pct + "%)");
                }
            }
        }

        if (Boolean.TRUE.equals(cfg.showDamageTaken)) {
            List<FightLedger.Entry> taken = ledger.byDamageTaken();
            if (!taken.isEmpty()) {
                out.add("&9&lMost damage taken");
                for (int i = 0; i < Math.min(top, taken.size()); i++) {
                    FightLedger.Entry e = taken.get(i);
                    out.add(" &7" + (i + 1) + ". &f" + e.name + " &9" + formatNumber(e.damageTaken));
                }
            }
        }

        if (Boolean.TRUE.equals(cfg.showDeaths)) {
            List<FightLedger.Entry> deaths = ledger.byDeaths();
            if (!deaths.isEmpty()) {
                StringBuilder sb = new StringBuilder("&4&lFallen: ");
                for (int i = 0; i < deaths.size(); i++) {
                    FightLedger.Entry e = deaths.get(i);
                    if (i > 0) sb.append("&7, ");
                    sb.append("&f").append(e.name);
                    if (e.deaths > 1) sb.append(" &7x").append(e.deaths);
                }
                out.add(sb.toString());
            }
        }

        if (Boolean.TRUE.equals(cfg.showUniqueDrops) && uniqueDrops != null) {
            for (Map.Entry<String, String> drop : uniqueDrops.entrySet()) {
                out.add("&d&l✦ &f" + drop.getKey() + " &7obtained &r" + drop.getValue());
            }
        }
        return out;
    }

    static String formatNumber(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    static String formatDuration(long ticks) {
        long seconds = ticks / 20;
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
