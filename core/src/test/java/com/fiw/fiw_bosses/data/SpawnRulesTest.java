package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.config.SpawnLimits;
import com.fiw.fiw_bosses.config.SummonEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnRulesTest {

    private static SpawnLimits limits(int maxAlive, int cooldown) {
        SpawnLimits l = new SpawnLimits();
        l.maxAlive = maxAlive;
        l.cooldownSeconds = cooldown;
        return l;
    }

    @Test
    void noLimitsAlwaysAllowed() {
        SpawnData d = new SpawnData();
        d.addAlive("b", "u1");
        assertNull(SpawnRules.check(d, "b", "Boss", null, null, 0, "p", 0));
    }

    @Test
    void maxAliveRefusesUntilOneDies() {
        SpawnData d = new SpawnData();
        SpawnLimits l = limits(1, 0);
        assertNull(SpawnRules.check(d, "b", "Boss", l, null, 0, "p", 0));
        assertTrue(d.addAlive("b", "u1"));
        assertFalse(d.addAlive("b", "u1"));
        SpawnRules.Refusal r = SpawnRules.check(d, "b", "Boss", l, null, 0, "p", 0);
        assertNotNull(r);
        assertEquals("&cBoss&c is already awake.", r.message());
        d.removeAlive("b", "u1");
        assertNull(SpawnRules.check(d, "b", "Boss", l, null, 0, "p", 0));
        assertFalse(d.alive.containsKey("b"));
    }

    @Test
    void globalCooldownCountsFromLastSummon() {
        SpawnData d = new SpawnData();
        SpawnLimits l = limits(0, 600);
        SpawnRules.record(d, "b", null, 0, "p", 1_000);
        SpawnRules.Refusal r = SpawnRules.check(d, "b", "Boss", l, null, 0, "other", 1_000 + 30_000);
        assertNotNull(r);
        assertEquals("&cBoss&c can be summoned again in 9m 30s.", r.message());
        assertNull(SpawnRules.check(d, "b", "Boss", l, null, 0, "other", 1_000 + 600_000));
    }

    @Test
    void playerCooldownIsPerPlayerAndPerEntry() {
        SpawnData d = new SpawnData();
        SummonEntry e = new SummonEntry();
        e.cooldownSeconds = 60;
        SpawnRules.record(d, "b", e, 0, "p1", 0);
        assertNotNull(SpawnRules.check(d, "b", "Boss", null, e, 0, "p1", 10_000));
        assertNull(SpawnRules.check(d, "b", "Boss", null, e, 0, "p2", 10_000));
        assertNull(SpawnRules.check(d, "b", "Boss", null, e, 1, "p1", 10_000));
        assertNull(SpawnRules.check(d, "b", "Boss", null, e, 0, "p1", 60_000));
    }

    @Test
    void resetClearsOneBossOnly() {
        SpawnData d = new SpawnData();
        SummonEntry e = new SummonEntry();
        e.cooldownSeconds = 60;
        SpawnRules.record(d, "b", e, 0, "p", 0);
        SpawnRules.record(d, "bb", e, 0, "p", 0);
        d.addAlive("b", "u");
        SpawnRules.reset(d, "b");
        assertEquals(0, d.aliveCount("b"));
        assertFalse(d.lastSummon.containsKey("b"));
        assertTrue(d.lastSummon.containsKey("bb"));
        assertTrue(d.playerSummons.containsKey("bb#0"));
        assertFalse(d.playerSummons.containsKey("b#0"));
    }

    @Test
    void durationsFormat() {
        assertEquals("45s", SpawnRules.formatDuration(44_200));
        assertEquals("2m 05s", SpawnRules.formatDuration(125_000));
        assertEquals("1h 05m", SpawnRules.formatDuration(3_900_000));
    }
}
