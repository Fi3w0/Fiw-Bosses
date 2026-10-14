package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.fight.FightLedger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persisted kill records per boss id, data/records.json. Fed on every rewarded boss death. */
public class KillRecords {
    public Map<String, BossRecord> bosses = new HashMap<>();

    public static class BossRecord {
        public int kills;
        // Fastest kill in ticks (-1 = none yet) and who was in that fight.
        public long fastestTicks = -1;
        public List<String> fastestParty = new ArrayList<>();
        // Most damage one player dealt in a single fight.
        public double topDamage;
        public String topDamageName;
        // Player UUID -> kills they took part in.
        public Map<String, PlayerKills> players = new HashMap<>();
    }

    public static class PlayerKills {
        public String name;
        public int kills;
    }

    /** Records one kill. Returns true when it set a new fastest-kill record. */
    public boolean record(String bossId, long durationTicks, List<FightLedger.Entry> participants) {
        BossRecord r = bosses.computeIfAbsent(bossId, k -> new BossRecord());
        r.kills++;
        boolean fastest = false;
        if (durationTicks > 0 && (r.fastestTicks < 0 || durationTicks < r.fastestTicks)) {
            r.fastestTicks = durationTicks;
            r.fastestParty = new ArrayList<>();
            for (FightLedger.Entry e : participants) r.fastestParty.add(e.name);
            fastest = true;
        }
        for (FightLedger.Entry e : participants) {
            PlayerKills p = r.players.computeIfAbsent(e.id.toString(), k -> new PlayerKills());
            p.name = e.name;
            p.kills++;
            if (e.damageDealt > r.topDamage) {
                r.topDamage = e.damageDealt;
                r.topDamageName = e.name;
            }
        }
        return fastest;
    }

    /** Players by kills, most first. */
    public static List<PlayerKills> topKillers(BossRecord r, int limit) {
        List<PlayerKills> list = new ArrayList<>(r.players.values());
        list.sort((a, b) -> Integer.compare(b.kills, a.kills));
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
