package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the vanilla-mob disguise fields ({@code baseEntity}/{@code renderEntity}) and the
 * parameter parsing of the abilities added in 1.1.0, all on the MC-free config POJOs.
 */
class DisguiseAndAbilityConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void bossDefaultsToCustomBaseEntityWithNoDisguise() {
        BossDefinition def = GSON.fromJson("{ \"id\": \"b\" }", BossDefinition.class);
        assertEquals("custom", def.baseEntity);
        assertNull(def.renderEntity);
    }

    @Test
    void bossParsesRenderEntityDisguise() {
        String json = """
            { "id": "b", "baseEntity": "custom", "renderEntity": "minecraft:wither_skeleton" }
            """;
        BossDefinition def = GSON.fromJson(json, BossDefinition.class);
        assertEquals("custom", def.baseEntity);
        assertEquals("minecraft:wither_skeleton", def.renderEntity);
    }

    @Test
    void minionParsesDisguiseAndCustomFlag() {
        String json = """
            { "id": "m", "baseEntity": "custom", "renderEntity": "minecraft:zombie" }
            """;
        MinionDefinition def = GSON.fromJson(json, MinionDefinition.class);
        assertTrue(def.isCustom());
        assertEquals("minecraft:zombie", def.renderEntity);
    }

    @Test
    void vanillaBaseMinionIsNotCustom() {
        MinionDefinition def = GSON.fromJson("{ \"id\": \"m\", \"baseEntity\": \"minecraft:zombie\" }",
                MinionDefinition.class);
        assertFalse(def.isCustom());
        assertEquals("minecraft:zombie", def.baseEntity);
    }

    @Test
    void parsesNewAbilityParams() {
        String json = """
            {
              "id": "b",
              "phases": [
                {
                  "hpThresholdPercent": 1.0,
                  "abilities": [
                    { "type": "rift_cleave", "cooldownTicks": 200, "params": { "damage": 9.0, "width": 2.0, "range": 12.0, "knockback": 1.5 } },
                    { "type": "fear_burst", "cooldownTicks": 300, "params": { "darknessSeconds": 6, "knockback": 2.0 } },
                    { "type": "wither_crown", "cooldownTicks": 240, "params": { "skulls": 6 } },
                    { "type": "rewind", "cooldownTicks": 1200, "params": { "delaySeconds": 4 } },
                    { "type": "adaptation", "cooldownTicks": 0, "params": { "maxResistance": 0.5 } },
                    { "type": "second_wind", "cooldownTicks": 2400, "params": { "healPercent": 0.4 } },
                    { "type": "cleanse", "cooldownTicks": 400, "params": { "blockSeconds": 3 } }
                  ]
                }
              ]
            }
            """;
        BossDefinition def = GSON.fromJson(json, BossDefinition.class);
        var abilities = def.phases.get(0).abilities;
        assertEquals(7, abilities.size());

        assertEquals("rift_cleave", abilities.get(0).type);
        assertEquals(200, abilities.get(0).cooldownTicks);
        assertEquals(9.0f, abilities.get(0).params.get("damage").getAsFloat());
        assertEquals(2.0f, abilities.get(0).params.get("width").getAsFloat());

        assertEquals("fear_burst", abilities.get(1).type);
        assertEquals(6, abilities.get(1).params.get("darknessSeconds").getAsInt());

        assertEquals("wither_crown", abilities.get(2).type);
        assertEquals(6, abilities.get(2).params.get("skulls").getAsInt());

        assertEquals("rewind", abilities.get(3).type);
        assertEquals(1200, abilities.get(3).cooldownTicks);

        assertEquals("adaptation", abilities.get(4).type);
        assertEquals(0.5f, abilities.get(4).params.get("maxResistance").getAsFloat());

        assertEquals("second_wind", abilities.get(5).type);
        assertEquals(0.4f, abilities.get(5).params.get("healPercent").getAsFloat());

        assertEquals("cleanse", abilities.get(6).type);
        assertEquals(3, abilities.get(6).params.get("blockSeconds").getAsInt());
    }

    @Test
    void abilityEntryDefaultsAreSane() {
        // No params / cooldown specified -> non-null params object and a default cooldown.
        AbilityEntry entry = GSON.fromJson("{ \"type\": \"melee_slash\" }", AbilityEntry.class);
        assertEquals("melee_slash", entry.type);
        assertNotNull(entry.params);
        assertEquals(60, entry.cooldownTicks);
    }
}
