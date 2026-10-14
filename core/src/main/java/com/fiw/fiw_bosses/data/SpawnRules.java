package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.config.SpawnLimits;
import com.fiw.fiw_bosses.config.SummonEntry;

import java.util.HashMap;

/** maxAlive / global cooldown / per-player cooldown checks for player summons. */
public final class SpawnRules {

    private SpawnRules() {}

    /** Why a summon was refused, with the message already filled in (color codes still raw). */
    public record Refusal(String message) {}

    /** Null when the summon may go ahead. */
    public static Refusal check(SpawnData data, String bossId, String bossName, SpawnLimits limits,
                                SummonEntry entry, int entryIndex, String playerUuid, long now) {
        SpawnLimits l = limits != null ? limits : new SpawnLimits();
        int alive = data.aliveCount(bossId);
        if (l.maxAlive > 0 && alive >= l.maxAlive) {
            return new Refusal(fill(l.aliveMessage, bossName, alive, l.maxAlive, 0));
        }
        Long last = data.lastSummon.get(bossId);
        if (l.cooldownSeconds > 0 && last != null) {
            long left = last + l.cooldownSeconds * 1000L - now;
            if (left > 0) return new Refusal(fill(l.cooldownMessage, bossName, alive, l.maxAlive, left));
        }
        if (entry != null && entry.cooldownSeconds > 0 && playerUuid != null) {
            var players = data.playerSummons.get(key(bossId, entryIndex));
            Long mine = players != null ? players.get(playerUuid) : null;
            if (mine != null) {
                long left = mine + entry.cooldownSeconds * 1000L - now;
                if (left > 0) return new Refusal(fill(l.playerCooldownMessage, bossName, alive, l.maxAlive, left));
            }
        }
        return null;
    }

    /** Records a successful summon for the cooldowns. */
    public static void record(SpawnData data, String bossId, SummonEntry entry, int entryIndex,
                              String playerUuid, long now) {
        data.lastSummon.put(bossId, now);
        if (entry != null && entry.cooldownSeconds > 0 && playerUuid != null) {
            data.playerSummons.computeIfAbsent(key(bossId, entryIndex), k -> new HashMap<>()).put(playerUuid, now);
        }
    }

    /** Forgets cooldowns and tracked living bosses for one boss id (admin reset). */
    public static void reset(SpawnData data, String bossId) {
        data.lastSummon.remove(bossId);
        data.alive.remove(bossId);
        data.playerSummons.keySet().removeIf(k -> k.startsWith(bossId + "#"));
    }

    static String key(String bossId, int entryIndex) {
        return bossId + "#" + entryIndex;
    }

    static String fill(String template, String bossName, int alive, int max, long leftMs) {
        if (template == null) return "";
        return template.replace("{boss}", bossName)
                .replace("{alive}", String.valueOf(alive))
                .replace("{max}", String.valueOf(max))
                .replace("{time}", formatDuration(leftMs));
    }

    /** "1h 05m", "12m 30s", "45s". */
    public static String formatDuration(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%dh %02dm", h, m);
        if (m > 0) return String.format(java.util.Locale.ROOT, "%dm %02ds", m, sec);
        return sec + "s";
    }
}
