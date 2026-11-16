package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossAffixesTest {

    private static final Gson GSON = new Gson();

    private static BossDefinition boss(String affixes) {
        return GSON.fromJson("{\"id\":\"b\",\"displayName\":\"&cGolem\",\"health\":100,\"armor\":2,\"speed\":0.2,"
                + "\"phases\":[{\"abilities\":[{\"type\":\"slam\"}]},{\"hpThresholdPercent\":0.5,\"abilities\":[]}],"
                + "\"rages\":{\"angry\":{\"abilities\":[{\"type\":\"charge\"}]},\"calm\":{\"speedMultiplier\":2}},"
                + "\"loot\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"toolId\":\"blade\",\"unique\":true}],"
                + "\"affixes\":" + affixes + "}", BossDefinition.class);
    }

    private static Map<String, AffixDefinition> library(String json) {
        return GSON.fromJson(json, new TypeToken<Map<String, AffixDefinition>>() {}.getType());
    }

    private static final String POOL = "{\"count\":2,\"pool\":["
            + "{\"id\":\"swift\",\"name\":\"&bSwift\",\"speedMultiplier\":1.5},"
            + "{\"id\":\"armored\",\"name\":\"&7Armored\",\"armorBonus\":8,\"healthMultiplier\":2,\"lootMultiplier\":3},"
            + "{\"id\":\"brutal\",\"name\":\"&4Brutal\",\"damageMultiplier\":2,\"abilityDamageMultiplier\":1.5},"
            + "{\"id\":\"healer\",\"name\":\"&aHealer\",\"abilities\":[{\"type\":\"heal\",\"cooldownTicks\":100}]}]}";

    @Test
    void rollPicksCountDifferentAffixes() {
        BossDefinition def = boss(POOL);
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            List<String> ids = BossAffixes.roll(def, Map.of(), random);
            assertEquals(2, ids.size());
            assertTrue(!ids.get(0).equals(ids.get(1)), "no affix twice: " + ids);
        }
        // Asking for more than the pool has gives the whole pool.
        BossDefinition all = boss(POOL.replace("\"count\":2", "\"count\":9"));
        assertEquals(4, BossAffixes.roll(all, Map.of(), random).size());
    }

    @Test
    void chanceZeroOrNoBlockRollsNothing() {
        assertEquals(List.of(), BossAffixes.roll(boss(POOL.replace("{\"count\":2", "{\"chance\":0,\"count\":2")), Map.of(), new Random(1)));
        BossDefinition plain = GSON.fromJson("{\"id\":\"b\"}", BossDefinition.class);
        assertEquals(List.of(), BossAffixes.roll(plain, Map.of(), new Random(1)));
        assertSame(plain, BossAffixes.rollAndApply(plain, new Random(1)));
    }

    @Test
    void weightsSteerTheRollAndZeroWeightNeverRolls() {
        BossDefinition def = boss("{\"pool\":[{\"id\":\"a\",\"name\":\"A\",\"weight\":9},"
                + "{\"id\":\"b\",\"name\":\"B\",\"weight\":1},{\"id\":\"c\",\"name\":\"C\",\"weight\":0}]}");
        Random random = new Random(42);
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < 2000; i++) seen.merge(BossAffixes.roll(def, Map.of(), random).get(0), 1, Integer::sum);
        assertNull(seen.get("c"));
        assertTrue(seen.get("a") > 1600 && seen.get("b") > 100, "about 90% / 10%: " + seen);
    }

    @Test
    void poolEntriesTakeMissingFieldsFromTheLibrary() {
        Map<String, AffixDefinition> lib = library("{\"swift\":{\"name\":\"&bSwift\",\"speedMultiplier\":2,\"weight\":5},"
                + "\"ghost\":{\"name\":\"&fGhost\"}}");
        BossDefinition def = boss("{\"pool\":[{\"id\":\"swift\",\"speedMultiplier\":3},{\"id\":\"missing\"}]}");
        AffixDefinition swift = BossAffixes.resolve(def, "swift", lib);
        assertEquals("&bSwift", swift.name);
        assertEquals(3f, swift.speedMultiplier);
        assertEquals(5f, swift.weight);
        // Only an id, not in the library: nothing to apply, so it can never roll.
        for (int i = 0; i < 50; i++) assertEquals(List.of("swift"), BossAffixes.roll(def, lib, new Random(i)));
        // The library alone still resolves an id the pool dropped (bosses saved with it keep it).
        assertEquals("&fGhost", BossAffixes.resolve(def, "ghost", lib).name);
    }

    @Test
    void applyChangesStatsNameLootAndAbilitiesOnACopy() {
        BossDefinition base = BossVariants.apply(
                GSON.fromJson(GSON.toJson(boss(POOL)).replace("\"affixes\"", "\"variants\":{\"hard\":{\"damageMultiplier\":2}},\"affixes\""),
                        BossDefinition.class), "hard");
        BossDefinition def = BossAffixes.apply(base, List.of("armored", "brutal", "healer", "swift"), Map.of());

        assertNotSame(base, def);
        assertEquals(List.of("armored", "brutal", "healer", "swift"), def.affixIds);
        assertEquals("&7Armored &4Brutal &aHealer &bSwift &cGolem", def.displayName);
        assertEquals(200f, def.health, 1e-4);
        assertEquals(10f, def.armor, 1e-4);
        assertEquals(0.3f, def.speed, 1e-4);
        // Variant x2 on everything, affix x2 on everything and x1.5 more on abilities.
        assertEquals("hard", def.variantName);
        assertEquals(4f, def.variantMeleeScale, 1e-4);
        assertEquals(6f, def.variantAbilityScale, 1e-4);
        assertEquals(6, def.loot.get(0).count);
        assertEquals(1, def.loot.get(1).count, "unique drops stay at one");
        assertEquals(List.of("slam", "heal"), def.phases.get(0).abilities.stream().map(a -> a.type).toList());
        assertEquals(List.of("heal"), def.phases.get(1).abilities.stream().map(a -> a.type).toList());
        assertEquals(2, def.rages.get("angry").getAsJsonArray("abilities").size());
        assertTrue(!def.rages.get("calm").has("abilities"), "a rage without its own abilities keeps the phase's");

        // The shared definition is never modified.
        assertEquals("&cGolem", base.displayName);
        assertEquals(100f, base.health);
        assertEquals(1, base.phases.get(0).abilities.size());
        assertEquals(2, base.loot.get(0).count);
        assertNull(base.affixIds);
    }

    @Test
    void nameFormatAndEmptyRollStillMarkTheBossRolled() {
        BossDefinition def = boss("{\"nameFormat\":\"{name} the {affixes}\",\"pool\":[{\"id\":\"swift\",\"name\":\"Swift\"}]}");
        assertEquals("&cGolem the Swift", BossAffixes.apply(def, List.of("swift"), Map.of()).displayName);
        BossDefinition none = BossAffixes.apply(def, List.of(), Map.of());
        assertEquals("&cGolem", none.displayName);
        assertEquals(List.of(), none.affixIds);
        // An already rolled boss (for example a copy of one) never rolls again.
        assertSame(none, BossAffixes.rollAndApply(none, new Random(3)));
    }

    @Test
    void savedIdsRoundTripAndUnknownOnesAreSkipped() {
        assertEquals("swift,armored", BossAffixes.encode(List.of("swift", "armored")));
        assertEquals(List.of("swift", "armored"), BossAffixes.decode("swift, armored,"));
        assertEquals(List.of(), BossAffixes.decode(null));
        BossDefinition def = BossAffixes.apply(boss(POOL), List.of("removed", "swift"), Map.of());
        assertEquals(List.of("swift"), def.affixIds);
        assertEquals("&bSwift &cGolem", def.displayName);
        BossDefinition plain = boss(POOL);
        assertSame(plain, BossAffixes.restore(plain, ""));
    }

    @Test
    void validatorReportsBadAffixes() {
        List<ConfigIssue> issues = ConfigValidator.boss("b.json", new StringReader("{\"id\":\"b\",\"phases\":[{}],"
                + "\"affixes\":{\"chance\":2,\"nameFormat\":\"{affixes}\",\"pool\":[{\"name\":\"no id\"},"
                + "{\"id\":\"x\",\"speedMultiplier\":0,\"weight\":-1,\"abilities\":[{\"type\":\"nope\"}]},{\"id\":\"x\",\"name\":\"X\"},"
                + "{\"id\":\"lib_only\"}],\"sped\":1}}")).issues();
        List<String> fields = issues.stream().map(ConfigIssue::field).toList();
        assertTrue(fields.containsAll(List.of("affixes.chance", "affixes.nameFormat", "affixes.pool[0].id",
                "affixes.pool[1].speedMultiplier", "affixes.pool[1].weight", "affixes.pool[1].abilities[0].type",
                "affixes.pool[2].id", "affixes.sped")), fields.toString());

        BossDefinition def = ConfigValidator.boss("b.json", new StringReader(
                "{\"id\":\"b\",\"affixes\":{\"pool\":[{\"id\":\"lib_only\"},{\"id\":\"swift\"}]}}")).value();
        List<ConfigIssue> refs = ConfigValidator.affixReferences(Map.of("b", def), Map.of("b", "b.json"),
                library("{\"swift\":{\"speedMultiplier\":2}}"));
        assertEquals(List.of("affixes.pool[0].id"), refs.stream().map(ConfigIssue::field).toList());
    }
}
