package com.fiw.fiw_bosses.schedule;

import com.fiw.fiw_bosses.config.ScheduleConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Decides when schedules announce and spawn. MC-free; called about once a second. */
public final class ScheduleEngine {

    public record Event(ScheduleConfig schedule, boolean spawn, long secondsLeft) {}

    // Announcements already sent for the current due time: schedule id -> due -> seconds.
    private final Map<String, Long> announcedFor = new HashMap<>();
    private final Map<String, Set<Integer>> announced = new HashMap<>();

    /**
     * @param isAlive whether the boss a schedule spawned last is still alive
     * @return events to run now, in order
     */
    public List<Event> tick(List<ScheduleConfig> schedules, ScheduleData data, long now, ZoneId zone,
                            Predicate<ScheduleConfig> isAlive) {
        List<Event> events = new ArrayList<>();
        for (ScheduleConfig s : schedules) {
            long due = due(s, data, now, zone);
            boolean alive = isAlive.test(s);
            if (now >= due) {
                if (!alive) events.add(new Event(s, true, 0));
                advance(s, data, due, now);
                announced.remove(s.id);
                announcedFor.remove(s.id);
                continue;
            }
            if (alive) continue;
            if (!Long.valueOf(due).equals(announcedFor.get(s.id))) {
                announcedFor.put(s.id, due);
                announced.put(s.id, new HashSet<>());
            }
            long left = (due - now + 999) / 1000;
            Set<Integer> sent = announced.get(s.id);
            int best = -1;
            for (Integer a : s.announceSeconds != null ? s.announceSeconds : List.<Integer>of()) {
                if (a == null || a <= 0 || sent.contains(a) || left > a) continue;
                sent.add(a);
                if (best < 0 || a < best) best = a;
            }
            // Only the closest crossed mark is broadcast (no burst of old ones after a restart).
            if (best > 0) events.add(new Event(s, false, left));
        }
        return events;
    }

    /** When the schedule fires next (epoch ms). Interval schedules start one interval after first seen. */
    public static long due(ScheduleConfig s, ScheduleData data, long now, ZoneId zone) {
        if (s.hasInterval()) {
            return data.nextAt.computeIfAbsent(s.id, k -> now + s.intervalMinutes * 60_000L);
        }
        // A slot up to a minute late (lag, restart) still fires once; lastFired stops a repeat.
        long after = Math.max(data.lastFired.getOrDefault(s.id, 0L), now - 60_000L);
        return nextTime(s.times, after, zone);
    }

    /** Forces a schedule's next run to now (admin "trigger"). */
    public static void triggerNow(ScheduleConfig s, ScheduleData data, long now) {
        if (s.hasInterval()) data.nextAt.put(s.id, now);
    }

    private static void advance(ScheduleConfig s, ScheduleData data, long due, long now) {
        if (s.hasInterval()) data.nextAt.put(s.id, now + s.intervalMinutes * 60_000L);
        else data.lastFired.put(s.id, Math.max(due, now));
    }

    /** First daily slot strictly after {@code after}. */
    static long nextTime(List<String> times, long after, ZoneId zone) {
        ZonedDateTime base = Instant.ofEpochMilli(after).atZone(zone);
        long best = Long.MAX_VALUE;
        for (int day = 0; day <= 1; day++) {
            LocalDate date = base.toLocalDate().plusDays(day);
            for (String t : times) {
                LocalTime lt = Schedules.parseTime(t);
                if (lt == null) continue;
                long at = date.atTime(lt).atZone(zone).toInstant().toEpochMilli();
                if (at > after && at < best) best = at;
            }
        }
        return best;
    }
}
