package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.config.RewardsConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardMathTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static List<FightLedger.Entry> raid(double a, double b, double c) {
        FightLedger ledger = new FightLedger();
        ledger.recordDealt(A, "Alpha", a, 0);
        ledger.recordDealt(B, "Bravo", b, 0);
        ledger.recordDealt(C, "Charlie", c, 0);
        return ledger.eligible(1);
    }

    private static LootEntry entry(float chance) {
        LootEntry e = new LootEntry();
        e.item = "minecraft:diamond";
        e.chance = chance;
        return e;
    }

    @Test
    void zeroLuckGivesEveryoneTheBaseChance() {
        assertEquals(0.5, RewardMath.chance(0.5, 0, 0.9, 3, 1), 1e-9);
        assertEquals(0.5, RewardMath.chance(0.5, 0, 0.05, 3, 1), 1e-9);
    }

    @Test
    void fullLuckScalesByContributionAndRespectsMaxChance() {
        // share 2/3 of 3 players = 2x average -> 2x chance
        assertEquals(0.4, RewardMath.chance(0.2, 1, 2.0 / 3, 3, 1), 1e-9);
        // capped by maxChance, but never below the configured base
        assertEquals(0.6, RewardMath.chance(0.5, 1, 0.9, 3, 0.6), 1e-9);
        assertEquals(0.5, RewardMath.chance(0.5, 1, 0.9, 3, 0.1), 1e-9);
    }

    @Test
    void guaranteedEntryGoesToEveryEligiblePlayer() {
        List<RewardMath.Award> awards = RewardMath.allocate(List.of(entry(1f)), raid(100, 50, 10),
                new RewardsConfig(), new Random(1)::nextDouble);
        assertEquals(3, awards.size());
    }

    @Test
    void uniqueEntryIsNeverDuplicated() {
        LootEntry sword = entry(1f);
        sword.unique = true;
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            List<RewardMath.Award> awards = RewardMath.allocate(List.of(sword), raid(100, 50, 10),
                    new RewardsConfig(), rng::nextDouble);
            assertEquals(1, awards.size());
        }
    }

    @Test
    void uniqueWinnerFollowsDamageWhenLucky() {
        LootEntry sword = entry(1f);
        sword.unique = true;
        sword.damageLuck = 1f;
        Random rng = new Random(7);
        int alphaWins = 0;
        int runs = 20000;
        for (int i = 0; i < runs; i++) {
            if (RewardMath.allocate(List.of(sword), raid(80, 15, 5), new RewardsConfig(), rng::nextDouble)
                    .get(0).player().equals(A)) alphaWins++;
        }
        double rate = alphaWins / (double) runs;
        assertTrue(rate > 0.77 && rate < 0.83, "alpha win rate " + rate);
    }

    @Test
    void topRanksLimitsCandidates() {
        LootEntry crown = entry(1f);
        crown.topRanks = 1;
        List<RewardMath.Award> awards = RewardMath.allocate(List.of(crown), raid(10, 90, 5),
                new RewardsConfig(), new Random(3)::nextDouble);
        assertEquals(1, awards.size());
        assertEquals(B, awards.get(0).player());
    }

    @Test
    void noEligiblePlayersMeansNoLoot() {
        assertTrue(RewardMath.allocate(List.of(entry(1f)), new ArrayList<>(),
                new RewardsConfig(), () -> 0.0).isEmpty());
    }
}
