package com.fiw.fiw_bosses.schedule;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.ScheduleConfig;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects and validates schedules from boss files and settings.json. */
public final class Schedules {

    private Schedules() {}

    public record Result(List<ScheduleConfig> schedules, List<String> warnings) {}

    public static Result collect(Map<String, BossDefinition> bosses, List<ScheduleConfig> fromSettings) {
        List<String> warnings = new ArrayList<>();
        Map<String, ScheduleConfig> byId = new LinkedHashMap<>();
        Set<String> scheduledInBossFile = new HashSet<>();

        for (BossDefinition def : bosses.values()) {
            ScheduleConfig s = def.schedule;
            if (s == null) continue;
            if (s.boss != null && !s.boss.equals(def.id)) {
                warnings.add("Boss '" + def.id + "': schedule.boss is ignored in a boss file (it always spawns this boss)");
            }
            s.boss = def.id;
            if (s.id == null || s.id.isBlank()) s.id = def.id;
            if (add(s, bosses, byId, warnings, "boss file " + def.id)) scheduledInBossFile.add(def.id);
        }
        if (fromSettings != null) {
            for (ScheduleConfig s : fromSettings) {
                if (s == null) continue;
                String where = "settings.json schedule '" + s.id + "'";
                if (s.id == null || s.id.isBlank() || s.boss == null || s.boss.isBlank()) {
                    warnings.add("A settings.json schedule needs both \"id\" and \"boss\"; skipped");
                    continue;
                }
                if (scheduledInBossFile.contains(s.boss)) {
                    warnings.add(where + " is ignored: boss '" + s.boss + "' already has a schedule in its own file");
                    continue;
                }
                add(s, bosses, byId, warnings, where);
            }
        }
        return new Result(new ArrayList<>(byId.values()), warnings);
    }

    private static boolean add(ScheduleConfig s, Map<String, BossDefinition> bosses,
                               Map<String, ScheduleConfig> byId, List<String> warnings, String where) {
        if (!s.enabled) return false;
        if (!bosses.containsKey(s.boss)) {
            warnings.add(where + ": unknown boss '" + s.boss + "'; disabled");
            return false;
        }
        if (s.hasInterval() == s.hasTimes()) {
            warnings.add(where + ": set exactly one of \"intervalMinutes\" or \"times\"; disabled");
            return false;
        }
        if (s.hasTimes()) {
            for (String t : s.times) {
                if (parseTime(t) == null) {
                    warnings.add(where + ": bad time '" + t + "' (use HH:mm); disabled");
                    return false;
                }
            }
        }
        if (s.locations == null || s.locations.isEmpty()) {
            warnings.add(where + ": no \"locations\"; disabled");
            return false;
        }
        if (byId.containsKey(s.id)) {
            warnings.add(where + ": duplicate schedule id '" + s.id + "'; disabled");
            return false;
        }
        byId.put(s.id, s);
        return true;
    }

    static LocalTime parseTime(String t) {
        try {
            return t != null ? LocalTime.parse(t.trim().length() == 4 ? "0" + t.trim() : t.trim()) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
