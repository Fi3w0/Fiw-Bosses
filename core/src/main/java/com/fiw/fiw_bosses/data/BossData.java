package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.config.BossConfigLoader;

import java.nio.file.Path;

/** Persistent state files under config/fiw_bosses/data/. */
public final class BossData {

    public static final JsonStore<SpawnData> SPAWNS = new JsonStore<>("spawns.json", SpawnData.class, SpawnData::new);

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
    }
}
