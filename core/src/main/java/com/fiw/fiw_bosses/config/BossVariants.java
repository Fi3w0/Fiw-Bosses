package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** Builds the per-entity definition for a difficulty variant. */
public final class BossVariants {

    private static final Gson GSON = new Gson();

    private BossVariants() {}

    /** The variant definition, or null when the boss has no variant by that name. */
    public static VariantDefinition find(BossDefinition base, String name) {
        if (base == null || name == null || name.isEmpty() || base.variants == null) return null;
        return base.variants.get(name);
    }

    /**
     * A copy of {@code base} with the variant applied: health, name and loot changed,
     * damage scales stored on the copy. Returns {@code base} itself when the variant
     * is null/unknown, so the shared definition is only copied when something changes.
     */
    public static BossDefinition apply(BossDefinition base, String name) {
        VariantDefinition v = find(base, name);
        if (v == null) return base;

        BossDefinition def = GSON.fromJson(GSON.toJsonTree(base), BossDefinition.class);
        def.variantName = name;
        def.health = base.health * positive(v.healthMultiplier);
        if (v.displayName != null && !v.displayName.isEmpty()) def.displayName = v.displayName;
        def.variantMeleeScale = positive(v.damageMultiplier) * positive(v.meleeDamageMultiplier);
        def.variantAbilityScale = positive(v.damageMultiplier) * positive(v.abilityDamageMultiplier);

        List<LootEntry> loot = v.loot != null ? copyLoot(v.loot) : def.loot;
        if (loot != null && v.lootMultiplier != 1.0f) {
            float m = Math.max(0f, v.lootMultiplier);
            for (LootEntry e : loot) {
                if (e.unique) continue;
                e.count = scale(e.count, m);
                if (e.minCount != null) e.minCount = scale(e.minCount, m);
                if (e.maxCount != null) e.maxCount = scale(e.maxCount, m);
            }
        }
        def.loot = loot;
        return def;
    }

    private static List<LootEntry> copyLoot(List<LootEntry> loot) {
        List<LootEntry> copy = new ArrayList<>();
        for (LootEntry e : loot) copy.add(GSON.fromJson(GSON.toJsonTree(e), LootEntry.class));
        return copy;
    }

    private static int scale(int count, float m) {
        return Math.max(1, Math.round(count * m));
    }

    private static float positive(float f) {
        return f > 0f ? f : 1.0f;
    }
}
