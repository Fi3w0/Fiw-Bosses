package com.fiw.fiw_bosses.summon;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.BossVariants;
import com.fiw.fiw_bosses.config.ScheduleConfig;
import com.fiw.fiw_bosses.config.SettingsLoader;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.data.BossData;
import com.fiw.fiw_bosses.data.SpawnRules;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.schedule.ScheduleData;
import com.fiw.fiw_bosses.schedule.ScheduleEngine;
import com.fiw.fiw_bosses.schedule.Schedules;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Runs scheduled world spawns: countdown broadcasts, then the spawn. Ticked by the loaders. */
public final class ScheduleRunner {

    private static final ScheduleEngine ENGINE = new ScheduleEngine();
    private static List<ScheduleConfig> schedules = List.of();
    private static MinecraftServer loadedFor;
    private static int ticks;
    // Location picked when the first countdown went out, so the spawn matches the broadcast.
    private static final Map<String, ScheduleConfig.Location> pending = new HashMap<>();

    private ScheduleRunner() {}

    /** Re-reads and validates schedules (server start, /boss reload). Returns the warnings. */
    public static List<String> reload() {
        Schedules.Result r = Schedules.collect(BossConfigLoader.getDefinitions(), SettingsLoader.get().schedules);
        schedules = r.schedules();
        pending.keySet().removeIf(id -> schedules.stream().noneMatch(s -> s.id.equals(id)));
        r.warnings().forEach(w -> FiwBossesCore.LOGGER.warn("Schedule: {}", w));
        return r.warnings();
    }

    public static List<ScheduleConfig> schedules() {
        return schedules;
    }

    /** Called every server tick; does its work once a second. */
    public static void onServerTick(MinecraftServer server) {
        if (server != loadedFor) {
            loadedFor = server;
            reload();
        }
        if (++ticks % 20 != 0 || schedules.isEmpty()) return;
        ScheduleData data = BossData.SCHEDULES.get();
        long now = System.currentTimeMillis();
        List<ScheduleEngine.Event> events = ENGINE.tick(schedules, data, now, ZoneId.systemDefault(),
                s -> isAlive(s, data));
        for (ScheduleEngine.Event e : events) {
            if (e.spawn()) spawn(server, e.schedule(), data);
            else announce(server, e.schedule(), e.secondsLeft());
        }
        // Next-run times change without events too (first sight, skipped runs); save now and then.
        if (!events.isEmpty() || ticks % 1200 == 0) BossData.SCHEDULES.save();
    }

    /** True while the boss this schedule spawned last is alive (also in unloaded chunks). */
    public static boolean isAlive(ScheduleConfig s, ScheduleData data) {
        String live = data.liveBoss.get(s.id);
        if (live == null) return false;
        int sep = live.indexOf('|');
        if (sep < 0) return false;
        Set<String> alive = BossData.SPAWNS.get().alive.get(live.substring(0, sep));
        return alive != null && alive.contains(live.substring(sep + 1));
    }

    /** Spawns a schedule's boss now (admin trigger). False when its last boss is still alive. */
    public static boolean trigger(MinecraftServer server, ScheduleConfig s) {
        ScheduleData data = BossData.SCHEDULES.get();
        if (isAlive(s, data)) return false;
        spawn(server, s, data);
        BossData.SCHEDULES.save();
        return true;
    }

    private static void announce(MinecraftServer server, ScheduleConfig s, long secondsLeft) {
        ScheduleConfig.Location loc = pending.computeIfAbsent(s.id, id -> pick(s));
        broadcast(server, s.announceMessage, s, loc, loc.y, secondsLeft);
    }

    private static void spawn(MinecraftServer server, ScheduleConfig s, ScheduleData data) {
        ScheduleConfig.Location loc = pending.remove(s.id);
        if (loc == null) loc = pick(s);
        BossDefinition base = BossConfigLoader.getDefinition(s.boss);
        ResourceLocation dimId = ResourceLocation.tryParse(loc.dimension);
        ServerLevel level = base == null || dimId == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            FiwBossesCore.LOGGER.warn("Schedule '{}': unknown boss or dimension '{}'; nothing spawned", s.id, loc.dimension);
            return;
        }
        int bx = Mth.floor(loc.x), bz = Mth.floor(loc.z);
        // Reading the height loads the chunk; a boss spawned there is saved with it if nobody is near.
        double y = loc.y != null ? loc.y : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BossDefinition def = BossVariants.apply(base, s.variant);
        double roomY = BossSpawner.roomFor(level, def, loc.x, y, loc.z);
        if (Double.isNaN(roomY)) {
            FiwBossesCore.LOGGER.warn("Schedule '{}': not enough room for boss '{}' ({} blocks) at {} {} {}; nothing spawned",
                    s.id, s.boss, BossSpawner.describeSize(def), loc.x, y, loc.z);
            return;
        }
        y = roomY;
        BossEntity boss = BossSpawner.spawn(level, def, loc.x, y, loc.z,
                MobSpawnType.EVENT);
        if (boss == null) return;
        data.liveBoss.put(s.id, s.boss + "|" + boss.getStringUUID());
        broadcast(server, s.spawnMessage, s, loc, y, 0);
    }

    private static ScheduleConfig.Location pick(ScheduleConfig s) {
        return s.locations.get(ThreadLocalRandom.current().nextInt(s.locations.size()));
    }

    private static void broadcast(MinecraftServer server, String template, ScheduleConfig s,
                                  ScheduleConfig.Location loc, Double y, long secondsLeft) {
        if (template == null || template.isEmpty()) return;
        BossDefinition def = BossVariants.apply(BossConfigLoader.getDefinition(s.boss), s.variant);
        String name = def != null && def.displayName != null ? def.displayName : s.boss;
        String text = template.replace("{boss}", name)
                .replace("{time}", SpawnRules.formatDuration(secondsLeft * 1000L))
                .replace("{x}", String.valueOf(Mth.floor(loc.x)))
                .replace("{y}", y != null ? String.valueOf(Mth.floor(y)) : "~")
                .replace("{z}", String.valueOf(Mth.floor(loc.z)))
                .replace("{dimension}", loc.dimension);
        server.getPlayerList().broadcastSystemMessage(TextUtil.parseColorCodes(text), false);
    }

    /** Milliseconds until a schedule's next run (for /boss schedule list). */
    public static long millisUntil(ScheduleConfig s) {
        return ScheduleEngine.due(s, BossData.SCHEDULES.get(), System.currentTimeMillis(), ZoneId.systemDefault())
                - System.currentTimeMillis();
    }
}
