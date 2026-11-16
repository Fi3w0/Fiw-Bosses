package com.fiw.fiw_bosses.config;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Spawn affixes. A boss with an {@code affixes} block rolls them once when it spawns; the
 * rolled ids are saved with the entity and applied again on load, after its variant.
 * Applying works like a variant: a per-entity copy of the definition with changed health,
 * speed, armor, damage scales, loot, abilities and name. The shared definition never changes.
 */
public final class BossAffixes {

    private static final Gson GSON = new Gson();

    private BossAffixes() {}

    /**
     * The affix with this id for a boss: its pool entry over the settings.json library entry.
     * Null when neither defines it.
     */
    public static AffixDefinition resolve(BossDefinition def, String id, Map<String, AffixDefinition> library) {
        if (id == null || id.isEmpty()) return null;
        AffixDefinition own = null;
        if (def != null && def.affixes != null && def.affixes.pool != null) {
            for (AffixDefinition e : def.affixes.pool) {
                if (e != null && id.equals(e.id)) { own = e; break; }
            }
        }
        AffixDefinition shared = library != null ? library.get(id) : null;
        if (own == null && shared == null) return null;
        return merge(id, own, shared);
    }

    /** Pool entry fields win; missing ones come from the library entry. */
    static AffixDefinition merge(String id, AffixDefinition own, AffixDefinition shared) {
        AffixDefinition o = own != null ? own : new AffixDefinition();
        AffixDefinition s = shared != null ? shared : new AffixDefinition();
        AffixDefinition m = new AffixDefinition();
        m.id = id;
        m.name = o.name != null ? o.name : s.name;
        m.weight = o.weight != null ? o.weight : s.weight;
        m.healthMultiplier = o.healthMultiplier != null ? o.healthMultiplier : s.healthMultiplier;
        m.damageMultiplier = o.damageMultiplier != null ? o.damageMultiplier : s.damageMultiplier;
        m.meleeDamageMultiplier = o.meleeDamageMultiplier != null ? o.meleeDamageMultiplier : s.meleeDamageMultiplier;
        m.abilityDamageMultiplier = o.abilityDamageMultiplier != null ? o.abilityDamageMultiplier : s.abilityDamageMultiplier;
        m.speedMultiplier = o.speedMultiplier != null ? o.speedMultiplier : s.speedMultiplier;
        m.armorBonus = o.armorBonus != null ? o.armorBonus : s.armorBonus;
        m.lootMultiplier = o.lootMultiplier != null ? o.lootMultiplier : s.lootMultiplier;
        m.abilities = o.abilities != null ? o.abilities : s.abilities;
        return m;
    }

    /**
     * Picks up to {@code count} different affix ids from the boss's pool by weight. Empty when the
     * boss has no affixes, the {@code chance} roll fails or nothing in the pool can be picked.
     */
    public static List<String> roll(BossDefinition def, Map<String, AffixDefinition> library, Random random) {
        List<String> picked = new ArrayList<>();
        if (def == null || def.affixes == null || def.affixes.pool == null) return picked;
        AffixConfig cfg = def.affixes;
        if (cfg.count <= 0 || random.nextFloat() >= cfg.chance) return picked;

        Map<String, Float> candidates = new LinkedHashMap<>();
        for (AffixDefinition entry : cfg.pool) {
            // Saved ids are comma separated: an id with a comma could not be restored, so it never rolls.
            if (entry == null || entry.id == null || entry.id.contains(",") || candidates.containsKey(entry.id)) continue;
            AffixDefinition a = resolve(def, entry.id, library);
            float w = a != null && a.weight != null ? a.weight : 1.0f;
            if (a != null && !isEmpty(a) && w > 0f) candidates.put(entry.id, w);
        }
        while (picked.size() < cfg.count && !candidates.isEmpty()) {
            double total = 0;
            for (float w : candidates.values()) total += w;
            double r = random.nextDouble() * total;
            String chosen = null;
            for (Map.Entry<String, Float> c : candidates.entrySet()) {
                chosen = c.getKey();
                r -= c.getValue();
                if (r < 0) break;
            }
            picked.add(chosen);
            candidates.remove(chosen);
        }
        return picked;
    }

    /**
     * A copy of {@code def} with these affixes applied, in order. Ids neither the pool nor the
     * library knows any more are skipped with a warning. The copy remembers the applied ids
     * ({@link BossDefinition#affixIds}) so it is never rolled again.
     */
    public static BossDefinition apply(BossDefinition def, List<String> ids, Map<String, AffixDefinition> library) {
        if (def == null) return null;
        BossDefinition copy = GSON.fromJson(GSON.toJsonTree(def), BossDefinition.class);
        // Transient fields are not part of the JSON copy.
        copy.variantName = def.variantName;
        copy.variantMeleeScale = def.variantMeleeScale;
        copy.variantAbilityScale = def.variantAbilityScale;

        List<String> applied = new ArrayList<>();
        List<String> names = new ArrayList<>();
        float lootMultiplier = 1.0f;
        for (String id : ids) {
            AffixDefinition a = resolve(def, id, library);
            if (a == null) {
                FiwBossesCore.LOGGER.warn("Boss '{}' had affix '{}', which is no longer configured; skipped", def.id, id);
                continue;
            }
            applied.add(id);
            if (a.name != null && !a.name.isBlank()) names.add(a.name);
            copy.health *= positive(a.healthMultiplier);
            float damage = positive(a.damageMultiplier);
            copy.variantMeleeScale *= damage * positive(a.meleeDamageMultiplier);
            copy.variantAbilityScale *= damage * positive(a.abilityDamageMultiplier);
            copy.speed *= positive(a.speedMultiplier);
            if (a.armorBonus != null) copy.armor = Math.max(0f, copy.armor + a.armorBonus);
            lootMultiplier *= a.lootMultiplier != null ? Math.max(0f, a.lootMultiplier) : 1.0f;
            addAbilities(copy, a.abilities);
        }
        if (lootMultiplier != 1.0f) BossVariants.scaleLoot(copy.loot, lootMultiplier);
        if (!names.isEmpty()) copy.displayName = name(def, names);
        copy.affixIds = List.copyOf(applied);
        return copy;
    }

    /**
     * Spawn time: rolls and applies the boss's affixes once. Returns {@code def} itself when the
     * boss has no affixes block, and an already rolled definition unchanged.
     */
    public static BossDefinition rollAndApply(BossDefinition def, Random random) {
        if (def == null || def.affixes == null || def.affixIds != null) return def;
        Map<String, AffixDefinition> library = SettingsLoader.get().affixes;
        return apply(def, roll(def, library, random), library);
    }

    /** Load time: the saved ids (comma separated) applied again; {@code def} when none were saved. */
    public static BossDefinition restore(BossDefinition def, String saved) {
        List<String> ids = decode(saved);
        if (def == null || ids.isEmpty()) return def;
        return apply(def, ids, SettingsLoader.get().affixes);
    }

    /** True when an affix has no name and changes nothing (only an id and maybe a weight). */
    public static boolean isEmpty(AffixDefinition a) {
        return a.name == null && a.healthMultiplier == null && a.damageMultiplier == null
                && a.meleeDamageMultiplier == null && a.abilityDamageMultiplier == null
                && a.speedMultiplier == null && a.armorBonus == null && a.lootMultiplier == null
                && (a.abilities == null || a.abilities.isEmpty());
    }

    public static String encode(List<String> ids) {
        return ids == null ? "" : String.join(",", ids);
    }

    public static List<String> decode(String saved) {
        List<String> ids = new ArrayList<>();
        if (saved == null || saved.isBlank()) return ids;
        for (String id : saved.split(",")) {
            if (!id.isBlank()) ids.add(id.trim());
        }
        return ids;
    }

    private static String name(BossDefinition def, List<String> names) {
        String base = def.displayName != null ? def.displayName : def.id;
        String format = def.affixes != null && def.affixes.nameFormat != null && !def.affixes.nameFormat.isBlank()
                ? def.affixes.nameFormat : "{affixes} {name}";
        return format.replace("{affixes}", String.join(" ", names)).replace("{name}", base).trim();
    }

    private static void addAbilities(BossDefinition copy, List<AbilityEntry> extra) {
        if (extra == null || extra.isEmpty()) return;
        if (copy.phases != null) {
            for (PhaseDefinition p : copy.phases) {
                if (p == null) continue;
                if (p.abilities == null) p.abilities = new ArrayList<>();
                for (AbilityEntry e : extra) p.abilities.add(GSON.fromJson(GSON.toJsonTree(e), AbilityEntry.class));
            }
        }
        // A rage that lists its own abilities replaces the phase's; give it the extras too.
        if (copy.rages != null) {
            for (JsonObject rage : copy.rages.values()) {
                if (rage == null || !rage.has("abilities") || !rage.get("abilities").isJsonArray()) continue;
                JsonArray list = rage.getAsJsonArray("abilities");
                for (AbilityEntry e : extra) {
                    JsonElement json = GSON.toJsonTree(e);
                    list.add(json);
                }
            }
        }
    }

    private static float positive(Float f) {
        return f != null && f > 0f ? f : 1.0f;
    }
}
