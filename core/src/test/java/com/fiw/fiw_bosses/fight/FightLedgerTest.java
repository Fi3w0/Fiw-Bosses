package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.config.FightSummaryConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FightLedgerTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void eligibilityFiltersLeechers() {
        FightLedger ledger = new FightLedger();
        ledger.recordDealt(A, "Alpha", 900, 10);
        ledger.recordDealt(B, "Bravo", 3, 11);
        ledger.recordTaken(C, "Charlie", 40, 12);
        List<FightLedger.Entry> eligible = ledger.eligible(50);
        assertEquals(1, eligible.size());
        assertEquals("Alpha", eligible.get(0).name);
    }

    @Test
    void encodeDecodeRoundTrips() {
        FightLedger ledger = new FightLedger();
        ledger.recordDealt(A, "Alpha", 12.5, 100);
        ledger.recordTaken(A, "Alpha", 4, 101);
        ledger.recordDeath(B, "Bravo", 102);
        FightLedger copy = FightLedger.decode(ledger.encode());
        assertEquals(ledger.encode(), copy.encode());
        assertEquals(50, copy.durationTicks(150));
        assertTrue(copy.hasDealtDamage(A));
        assertFalse(copy.hasDealtDamage(B));
    }

    @Test
    void decodeSurvivesGarbage() {
        assertTrue(FightLedger.decode("not;a|ledger").isEmpty());
        assertTrue(FightLedger.decode(null).isEmpty());
    }

    @Test
    void summaryOnlyListsRealParticipants() {
        FightLedger ledger = new FightLedger();
        ledger.recordDealt(A, "Alpha", 1500, 0);
        ledger.recordDealt(B, "Bravo", 500, 0);
        ledger.recordTaken(B, "Bravo", 60, 0);
        ledger.recordDeath(B, "Bravo", 0);
        FightSummaryConfig cfg = FightSummaryConfig.defaults();
        cfg.enabled = true;
        Map<String, String> drops = new LinkedHashMap<>();
        drops.put("Alpha", "Excalibur");

        List<String> lines = FightSummary.lines("&cDragon", ledger, cfg, 20 * 134, "Alpha", drops);
        String all = String.join("\n", lines);
        assertTrue(lines.get(0).contains("(2m 14s)"));
        assertTrue(all.contains("Alpha &c1,500 &7(75%)"));
        assertTrue(all.contains("Bravo &9"));
        assertTrue(all.contains("Fallen: &fBravo"));
        assertTrue(all.contains("Excalibur"));
        // Alpha never took damage or died, so they must not appear in those sections.
        assertFalse(all.contains("Alpha &9"));
    }

    @Test
    void summaryTogglesHideSections() {
        FightLedger ledger = new FightLedger();
        ledger.recordDealt(A, "Alpha", 10, 0);
        FightSummaryConfig override = new FightSummaryConfig();
        override.showDamageDealt = false;
        FightSummaryConfig cfg = override.over(FightSummaryConfig.defaults());
        String all = String.join("\n", FightSummary.lines("Boss", ledger, cfg, 0, null, Map.of()));
        assertFalse(all.contains("Top damage"));
    }
}
