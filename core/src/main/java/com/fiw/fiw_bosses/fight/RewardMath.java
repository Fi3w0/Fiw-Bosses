package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.config.RewardsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Decides who gets which loot entry in per-player mode. Pure logic: randomness comes in
 * as a {@link DoubleSupplier} of values in [0, 1), so the rules are unit-testable.
 */
public final class RewardMath {

    /** One awarded loot entry (index into the boss's loot list) for one player. */
    public record Award(int entryIndex, UUID player) {}

    private RewardMath() {}

    /**
     * Chance for one player to receive an entry. {@code share} is the player's fraction of
     * the candidates' total damage, {@code n} the number of candidates. With luck 0 everyone
     * gets {@code base}; with luck 1 it scales by contribution relative to the average.
     * Luck can lift a chance up to {@code maxChance} but never caps the configured base.
     */
    public static double chance(double base, double luck, double share, int n, double maxChance) {
        luck = clamp01(luck);
        double relative = n > 0 ? share * n : 1.0;
        double c = base * ((1.0 - luck) + luck * relative);
        return Math.max(0.0, Math.min(c, Math.max(base, maxChance)));
    }

    /** Weight for picking the single winner of a unique entry. */
    public static double winnerWeight(double luck, double share, int n) {
        luck = clamp01(luck);
        return (1.0 - luck) / Math.max(1, n) + luck * share;
    }

    /** Weighted pick; returns an index, or -1 when every weight is zero. */
    public static int pickWeighted(double[] weights, DoubleSupplier rng) {
        double total = 0;
        for (double w : weights) total += Math.max(0, w);
        if (total <= 0) return -1;
        double r = rng.getAsDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r < 0) return i;
        }
        return weights.length - 1;
    }

    /**
     * Rolls the whole loot table for a raid. {@code eligible} must be sorted by damage
     * dealt, highest first (as {@link FightLedger#eligible} returns it).
     * Normal entries roll independently per player; unique entries roll once and go to
     * exactly one player, so they can never be duplicated.
     */
    public static List<Award> allocate(List<LootEntry> loot, List<FightLedger.Entry> eligible,
                                       RewardsConfig cfg, DoubleSupplier rng) {
        List<Award> awards = new ArrayList<>();
        if (loot == null || eligible == null || eligible.isEmpty()) return awards;

        for (int i = 0; i < loot.size(); i++) {
            LootEntry entry = loot.get(i);
            if (entry == null) continue;
            List<FightLedger.Entry> candidates = entry.topRanks > 0 && entry.topRanks < eligible.size()
                    ? eligible.subList(0, entry.topRanks) : eligible;

            double total = 0;
            for (FightLedger.Entry c : candidates) total += c.damageDealt;
            int n = candidates.size();
            double luck = entry.damageLuck != null ? entry.damageLuck : cfg.damageLuck;

            if (entry.unique) {
                if (rng.getAsDouble() >= entry.chance) continue;
                double[] weights = new double[n];
                for (int k = 0; k < n; k++) {
                    double share = total > 0 ? candidates.get(k).damageDealt / total : 1.0 / n;
                    weights[k] = winnerWeight(luck, share, n);
                }
                int winner = pickWeighted(weights, rng);
                if (winner >= 0) awards.add(new Award(i, candidates.get(winner).id));
            } else {
                for (FightLedger.Entry c : candidates) {
                    double share = total > 0 ? c.damageDealt / total : 1.0 / n;
                    if (rng.getAsDouble() < chance(entry.chance, luck, share, n, cfg.maxChance)) {
                        awards.add(new Award(i, c.id));
                    }
                }
            }
        }
        return awards;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
