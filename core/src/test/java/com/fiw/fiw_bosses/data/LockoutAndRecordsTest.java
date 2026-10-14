package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.config.WebhookConfig;
import com.fiw.fiw_bosses.fight.DiscordEmbed;
import com.fiw.fiw_bosses.fight.FightLedger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockoutAndRecordsTest {

    @Test
    void lockoutKeyIsSharedUnlessPerVariant() {
        assertEquals("b", LockoutData.key("b", "hard", false));
        assertEquals("b#hard", LockoutData.key("b", "hard", true));
        assertEquals("b", LockoutData.key("b", null, true));
    }

    @Test
    void lockoutExpiresAndPrunes() {
        LockoutData d = new LockoutData();
        d.lock("b", "p", 0, 2f);
        assertEquals(7_200_000L, d.remaining("b", "p", 0));
        assertEquals(0, d.remaining("b", "other", 0));
        assertEquals(0, d.remaining("b", "p", 7_200_000L));
        d.prune(7_200_000L);
        assertTrue(d.until.isEmpty());
        d.lock("b", "p", 0, 0f);
        assertTrue(d.until.isEmpty());
    }

    private static FightLedger ledger() {
        FightLedger l = new FightLedger();
        l.recordDealt(new UUID(0, 1), "Big_Steve", 300, 0);
        l.recordDealt(new UUID(0, 2), "Alex", 100, 20);
        return l;
    }

    @Test
    void recordsTrackKillsFastestAndTopDamage() {
        KillRecords r = new KillRecords();
        List<FightLedger.Entry> fight = ledger().byDamageDealt();
        assertTrue(r.record("b", 2400, fight));
        assertFalse(r.record("b", 3000, fight));
        assertTrue(r.record("b", 1200, fight.subList(1, 2)));
        KillRecords.BossRecord b = r.bosses.get("b");
        assertEquals(3, b.kills);
        assertEquals(1200, b.fastestTicks);
        assertEquals(List.of("Alex"), b.fastestParty);
        assertEquals(300, b.topDamage);
        assertEquals("Big_Steve", b.topDamageName);
        assertEquals("Alex", KillRecords.topKillers(b, 1).get(0).name);
        assertEquals(3, KillRecords.topKillers(b, 1).get(0).kills);
    }

    @Test
    void webhookResolvesBossOverGlobal() {
        WebhookConfig global = new WebhookConfig();
        global.discord = "https://discord.com/api/webhooks/global";
        WebhookConfig boss = new WebhookConfig();
        assertEquals(global.discord, WebhookConfig.resolve(null, global));
        assertEquals(global.discord, WebhookConfig.resolve(boss, global));
        boss.discord = "https://discord.com/api/webhooks/boss";
        assertEquals(boss.discord, WebhookConfig.resolve(boss, global));
        boss.enabled = false;
        assertNull(WebhookConfig.resolve(boss, global));
        assertNull(WebhookConfig.resolve(null, new WebhookConfig()));
    }

    @Test
    void embedHasFieldsWithoutColorCodes() {
        FightLedger l = ledger();
        String json = DiscordEmbed.build(new DiscordEmbed.Kill("&6&lEmber Warden", "hard", "Big_Steve",
                2500, l.byDamageDealt(), l.totalDealt(), Map.of("Alex", "&bExcalibur"), true,
                DiscordEmbed.colorFor("RED"), 3));
        JsonObject embed = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Ember Warden defeated", embed.get("title").getAsString());
        assertEquals(0xE74C3C, embed.get("color").getAsInt());
        var fields = embed.getAsJsonArray("fields");
        assertEquals("Big\\_Steve", fields.get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("2m 05s", fields.get(1).getAsJsonObject().get("value").getAsString());
        assertEquals("hard", fields.get(2).getAsJsonObject().get("value").getAsString());
        assertTrue(fields.get(3).getAsJsonObject().get("value").getAsString().startsWith("1. Big\\_Steve — 300 (75%)"));
        assertEquals("Excalibur → Alex", fields.get(4).getAsJsonObject().get("value").getAsString());
        assertEquals("New fastest kill!", embed.getAsJsonObject("footer").get("text").getAsString());
    }
}
