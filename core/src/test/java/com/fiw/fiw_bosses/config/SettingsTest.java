package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void partialSettingsFallBackToDefaults() {
        GlobalSettings s = SettingsLoader.parse(new StringReader("{\"fightSummary\":{\"enabled\":true}}"));
        assertTrue(s.fightSummary.enabled);
        assertTrue(s.fightSummary.showDamageDealt);
        assertEquals(3, s.fightSummary.topCount);
        assertEquals(48, s.combatTrackingRange);
    }

    @Test
    void bossOverrideWinsKeyByKey() {
        BossDefinition def = new Gson().fromJson(
                "{\"id\":\"b\",\"fightSummary\":{\"showDeaths\":false,\"topCount\":5},"
                        + "\"rewards\":{\"lootMode\":\"per_player\",\"damageLuck\":0.5},"
                        + "\"loot\":[{\"toolId\":\"excalibur\",\"unique\":true,\"topRanks\":3}]}",
                BossDefinition.class);
        FightSummaryConfig global = FightSummaryConfig.defaults();
        global.enabled = true;
        FightSummaryConfig r = def.fightSummary.over(global);
        assertTrue(r.enabled);
        assertFalse(r.showDeaths);
        assertEquals(5, r.topCount);
        assertTrue(def.rewards.perPlayer());
        assertEquals(0.5f, def.rewards.damageLuck);
        assertTrue(def.loot.get(0).unique);
        assertEquals(3, def.loot.get(0).topRanks);
    }
}
