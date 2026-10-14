package com.fiw.fiw_bosses.schedule;

import java.util.HashMap;
import java.util.Map;

/** Persisted schedule state, data/schedules.json. Times are epoch millis. */
public class ScheduleData {
    // Interval schedules: when the next spawn is due.
    public Map<String, Long> nextAt = new HashMap<>();
    // Daily-time schedules: the last slot that fired, so a restart can't fire it twice.
    public Map<String, Long> lastFired = new HashMap<>();
    // Schedule id -> "bossId|uuid" of the boss it spawned last.
    public Map<String, String> liveBoss = new HashMap<>();
}
