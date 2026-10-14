package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.config.BossConfigLoader;

import java.nio.file.Path;

/** Persistent state files under config/fiw_bosses/data/. */
public final class BossData {

    public static final JsonStore<SpawnData> SPAWNS = new JsonStore<>("spawns.json", SpawnData.class, SpawnData::new);
    public static final JsonStore<LockoutData> LOCKOUTS = new JsonStore<>("lockouts.json", LockoutData.class, LockoutData::new);
    public static final JsonStore<KillRecords> RECORDS = new JsonStore<>("records.json", KillRecords.class, KillRecords::new);
    public static final JsonStore<com.fiw.fiw_bosses.schedule.ScheduleData> SCHEDULES = new JsonStore<>(
            "schedules.json", com.fiw.fiw_bosses.schedule.ScheduleData.class, com.fiw.fiw_bosses.schedule.ScheduleData::new);

    private BossData() {}

    public static Path dir() {
        return BossConfigLoader.getConfigDir().resolve("data");
    }

    /** Called once on server start. */
    public static void loadAll() {
        SPAWNS.load(dir());
        SpawnData s = SPAWNS.get();
        if (s.lastSummon == null) s.lastSummon = new java.util.HashMap<>();
        if (s.playerSummons == null) s.playerSummons = new java.util.HashMap<>();
        if (s.alive == null) s.alive = new java.util.HashMap<>();
        LOCKOUTS.load(dir());
        if (LOCKOUTS.get().until == null) LOCKOUTS.get().until = new java.util.HashMap<>();
        LOCKOUTS.get().prune(System.currentTimeMillis());
        RECORDS.load(dir());
        if (RECORDS.get().bosses == null) RECORDS.get().bosses = new java.util.HashMap<>();
        SCHEDULES.load(dir());
        var sd = SCHEDULES.get();
        if (sd.nextAt == null) sd.nextAt = new java.util.HashMap<>();
        if (sd.lastFired == null) sd.lastFired = new java.util.HashMap<>();
        if (sd.liveBoss == null) sd.liveBoss = new java.util.HashMap<>();
    }
}
