package com.fiw.fiw_bosses.schedule;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.ScheduleConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static ScheduleConfig interval(int minutes) {
        ScheduleConfig s = new Gson().fromJson("{\"id\":\"s\",\"boss\":\"b\",\"intervalMinutes\":" + minutes
                + ",\"locations\":[{\"x\":0,\"z\":0}]}", ScheduleConfig.class);
        return s;
    }

    @Test
    void intervalAnnouncesClosestMarkThenSpawns() {
        ScheduleConfig s = interval(15);
        ScheduleData d = new ScheduleData();
        ScheduleEngine e = new ScheduleEngine();
        assertTrue(e.tick(List.of(s), d, 0, UTC, x -> false).isEmpty());
        long due = d.nextAt.get("s");
        assertEquals(15 * 60_000L, due);
        var ev = e.tick(List.of(s), d, due - 600_000L, UTC, x -> false);
        assertEquals(1, ev.size());
        assertFalse(ev.get(0).spawn());
        assertEquals(600, ev.get(0).secondsLeft());
        assertTrue(e.tick(List.of(s), d, due - 599_000L, UTC, x -> false).isEmpty());
        // Restart-style jump past 60s and 10s marks: only one broadcast.
        assertEquals(1, e.tick(List.of(s), d, due - 5_000L, UTC, x -> false).size());
        var spawn = e.tick(List.of(s), d, due, UTC, x -> false);
        assertTrue(spawn.get(0).spawn());
        assertEquals(due + 15 * 60_000L, d.nextAt.get("s"));
    }

    @Test
    void aliveBossSkipsAnnouncementsAndSpawn() {
        ScheduleConfig s = interval(1);
        ScheduleData d = new ScheduleData();
        ScheduleEngine e = new ScheduleEngine();
        e.tick(List.of(s), d, 0, UTC, x -> true);
        assertTrue(e.tick(List.of(s), d, 50_000, UTC, x -> true).isEmpty());
        assertTrue(e.tick(List.of(s), d, 60_000, UTC, x -> true).isEmpty());
        assertEquals(120_000L, d.nextAt.get("s"));
    }

    @Test
    void dailyTimesFireOnceEvenAfterRestart() {
        ScheduleConfig s = new Gson().fromJson("{\"id\":\"s\",\"boss\":\"b\",\"times\":[\"18:00\",\"6:30\"],"
                + "\"locations\":[{\"x\":0,\"z\":0}],\"announceSeconds\":[]}", ScheduleConfig.class);
        ScheduleData d = new ScheduleData();
        long sixPm = LocalDate.of(2026, 1, 1).atTime(LocalTime.of(18, 0)).toInstant(ZoneOffset.UTC).toEpochMilli();
        assertEquals(sixPm, ScheduleEngine.due(s, d, sixPm - 3_600_000L, UTC));
        ScheduleEngine e = new ScheduleEngine();
        // Tick lands 1.5 s late: still fires.
        assertTrue(e.tick(List.of(s), d, sixPm + 1_500, UTC, x -> false).get(0).spawn());
        // A fresh engine (restart) a few seconds later does not fire again.
        assertTrue(new ScheduleEngine().tick(List.of(s), d, sixPm + 5_000, UTC, x -> false).isEmpty());
        long nextMorning = LocalDate.of(2026, 1, 2).atTime(LocalTime.of(6, 30)).toInstant(ZoneOffset.UTC).toEpochMilli();
        assertEquals(nextMorning, ScheduleEngine.due(s, d, sixPm + 5_000, UTC));
    }

    @Test
    void collectValidatesAndBossFileWins() {
        Gson g = new Gson();
        BossDefinition a = g.fromJson("{\"id\":\"a\",\"schedule\":{\"intervalMinutes\":30,"
                + "\"locations\":[{\"x\":1,\"z\":2}]}}", BossDefinition.class);
        BossDefinition b = g.fromJson("{\"id\":\"b\"}", BossDefinition.class);
        List<ScheduleConfig> settings = List.of(
                g.fromJson("{\"id\":\"dup\",\"boss\":\"a\",\"intervalMinutes\":5,\"locations\":[{\"x\":0,\"z\":0}]}", ScheduleConfig.class),
                g.fromJson("{\"id\":\"both\",\"boss\":\"b\",\"intervalMinutes\":5,\"times\":[\"10:00\"],\"locations\":[{\"x\":0,\"z\":0}]}", ScheduleConfig.class),
                g.fromJson("{\"id\":\"ok\",\"boss\":\"b\",\"times\":[\"10:00\"],\"locations\":[{\"x\":0,\"z\":0}]}", ScheduleConfig.class),
                g.fromJson("{\"id\":\"ghost\",\"boss\":\"zzz\",\"intervalMinutes\":5,\"locations\":[{\"x\":0,\"z\":0}]}", ScheduleConfig.class));
        Schedules.Result r = Schedules.collect(Map.of("a", a, "b", b), settings);
        assertEquals(List.of("a", "ok"), r.schedules().stream().map(s -> s.id).toList());
        assertEquals(3, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("already has a schedule in its own file"));
        assertTrue(r.warnings().get(1).contains("exactly one"));
        assertTrue(r.warnings().get(2).contains("unknown boss"));
    }
}
