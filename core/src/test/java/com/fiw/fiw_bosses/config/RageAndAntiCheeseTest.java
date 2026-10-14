package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RageAndAntiCheeseTest {

    private static BossDefinition parse(String json) {
        return new Gson().fromJson(json, BossDefinition.class);
    }

    @Test
    void defaultsAreMixed() {
        BossDefinition def = parse("{\"id\":\"b\"}");
        assertTrue(def.antiCheese.destroyVehicles);
        assertNull(def.antiCheese.stuck);
        assertNull(def.antiCheese.ignoreSlowBlocks);
        assertNull(def.arena);
        // A partial block keeps the other defaults.
        BossDefinition partial = parse("{\"id\":\"b\",\"antiCheese\":{\"stuck\":{\"seconds\":5}}}");
        assertTrue(partial.antiCheese.destroyVehicles);
        assertEquals(5, partial.antiCheese.stuck.seconds);
        assertEquals(List.of("teleport_to_target"), partial.antiCheese.stuck.actions);
    }

    @Test
    void escalationPicksPerOccurrenceAndRepeatsLast() {
        BossDefinition def = parse("{\"id\":\"b\",\"antiCheese\":{\"stuck\":{"
                + "\"escalation\":[[\"teleport_to_target\"],[\"rage:trapped\"],[\"despawn\"]]}}}");
        StuckConfig s = def.antiCheese.stuck;
        assertEquals(List.of("teleport_to_target"), s.actionsFor(1));
        assertEquals(List.of("rage:trapped"), s.actionsFor(2));
        assertEquals(List.of("despawn"), s.actionsFor(3));
        assertEquals(List.of("despawn"), s.actionsFor(9));
    }

    @Test
    void slowBlockOverrides() {
        BossDefinition def = parse("{\"id\":\"b\",\"antiCheese\":{\"ignoreSlowBlocks\":"
                + "{\"blocks\":{\"minecraft:powder_snow\":false}}}}");
        assertTrue(def.antiCheese.ignoreSlowBlocks.ignores("minecraft:cobweb"));
        assertFalse(def.antiCheese.ignoreSlowBlocks.ignores("minecraft:powder_snow"));
    }

    @Test
    void rageOverridesOnlyTheKeysItSets() {
        BossDefinition def = parse("{\"id\":\"b\",\"phases\":[{\"hpThresholdPercent\":0.5,"
                + "\"speedMultiplier\":1.2,\"damageMultiplier\":1.1,"
                + "\"abilities\":[{\"type\":\"beam\"}],\"transitionMessage\":\"phase\"}],"
                + "\"rages\":{\"timer\":{\"afterSeconds\":300,\"damageMultiplier\":2,"
                + "\"abilities\":[{\"type\":\"meteor\"},{\"type\":\"heal\"}],\"bossBarColor\":\"RED\","
                + "\"hpThresholdPercent\":0.1}}}");
        PhaseDefinition merged = PhaseOverlay.merge(def.phases.get(0), def.rages.get("timer"));
        assertEquals(2f, merged.damageMultiplier);
        assertEquals(1.2f, merged.speedMultiplier);
        assertEquals(0.5f, merged.hpThresholdPercent);
        assertEquals("phase", merged.transitionMessage);
        assertEquals(2, merged.abilities.size());
        assertEquals("meteor", merged.abilities.get(0).type);
        RageDefinition r = PhaseOverlay.rageFields(def.rages.get("timer"));
        assertEquals(300, r.afterSeconds);
        assertEquals("RED", r.bossBarColor);
        assertEquals(0, r.durationSeconds);
        // The HP phase itself is untouched.
        assertEquals(1.1f, def.phases.get(0).damageMultiplier);
    }

    @Test
    void variantCopyKeepsRagesAndArena() {
        BossDefinition def = parse("{\"id\":\"b\",\"arena\":{\"radius\":30},"
                + "\"rages\":{\"r\":{\"healPercent\":10}},\"variants\":{\"hard\":{}}}");
        BossDefinition hard = BossVariants.apply(def, "hard");
        assertEquals(30, hard.arena.radius);
        assertEquals(10f, PhaseOverlay.rageFields(hard.rages.get("r")).healPercent);
    }
}
