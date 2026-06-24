package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies the MC-free config POJOs deserialize from JSON the same way the loader does. */
class ConfigParseTest {

    private static final Gson GSON = new Gson();

    private static final String BOSS_JSON = """
        {
          "id": "test_boss",
          "displayName": "&6&lTest",
          "health": 300.0,
          "armor": 8.0,
          "phases": [
            {
              "hpThresholdPercent": 0.5,
              "speedMultiplier": 1.3,
              "abilities": [
                { "type": "melee_slash", "cooldownTicks": 40, "params": { "damage": 12.0 } }
              ]
            }
          ],
          "loot": [
            { "item": "minecraft:diamond", "count": 5, "chance": 1.0 }
          ]
        }
        """;

    @Test
    void parsesRootFields() {
        BossDefinition def = GSON.fromJson(BOSS_JSON, BossDefinition.class);
        assertEquals("test_boss", def.id);
        assertEquals("&6&lTest", def.displayName);
        assertEquals(300.0f, def.health);
        assertEquals(8.0f, def.armor);
    }

    @Test
    void parsesNestedPhaseAbilityParams() {
        BossDefinition def = GSON.fromJson(BOSS_JSON, BossDefinition.class);
        assertEquals(1, def.phases.size());
        PhaseDefinition phase = def.phases.get(0);
        assertEquals(0.5f, phase.hpThresholdPercent);
        assertEquals(1, phase.abilities.size());
        AbilityEntry ability = phase.abilities.get(0);
        assertEquals("melee_slash", ability.type);
        assertEquals(40, ability.cooldownTicks);
        assertNotNull(ability.params);
        assertEquals(12.0f, ability.params.get("damage").getAsFloat());
    }

    @Test
    void parsesLoot() {
        BossDefinition def = GSON.fromJson(BOSS_JSON, BossDefinition.class);
        assertEquals(1, def.loot.size());
        LootEntry loot = def.loot.get(0);
        assertEquals("minecraft:diamond", loot.item);
        assertEquals(5, loot.count);
        assertEquals(1.0f, loot.chance);
    }
}
