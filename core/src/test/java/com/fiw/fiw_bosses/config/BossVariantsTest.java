package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BossVariantsTest {

    private static BossDefinition parse(String json) {
        return new Gson().fromJson(json, BossDefinition.class);
    }

    private static final String BOSS = "{\"id\":\"b\",\"displayName\":\"B\",\"health\":100,"
            + "\"loot\":[{\"item\":\"minecraft:diamond\",\"count\":3,\"minCount\":2,\"maxCount\":10},"
            + "{\"toolId\":\"blade\",\"unique\":true}],"
            + "\"variants\":{\"hard\":{\"displayName\":\"B [Hard]\",\"healthMultiplier\":2.5,"
            + "\"damageMultiplier\":2,\"meleeDamageMultiplier\":1.5,\"abilityDamageMultiplier\":0.5,"
            + "\"lootMultiplier\":2}}}";

    @Test
    void unknownOrMissingVariantReturnsBaseUnchanged() {
        BossDefinition base = parse(BOSS);
        assertSame(base, BossVariants.apply(base, null));
        assertSame(base, BossVariants.apply(base, "nightmare"));
        assertNull(base.variantName);
    }

    @Test
    void variantScalesHealthDamageNameAndLoot() {
        BossDefinition base = parse(BOSS);
        BossDefinition hard = BossVariants.apply(base, "hard");
        assertEquals("hard", hard.variantName);
        assertEquals(250f, hard.health);
        assertEquals("B [Hard]", hard.displayName);
        assertEquals(3.0f, hard.variantMeleeScale, 1e-6);
        assertEquals(1.0f, hard.variantAbilityScale, 1e-6);
        assertEquals(6, hard.loot.get(0).count);
        assertEquals(4, hard.loot.get(0).minCount);
        assertEquals(20, hard.loot.get(0).maxCount);
        assertEquals(1, hard.loot.get(1).count);
        // The shared definition is never modified.
        assertEquals(100f, base.health);
        assertEquals(3, base.loot.get(0).count);
        assertEquals(1.0f, base.variantMeleeScale);
    }

    @Test
    void variantLootReplacesBaseLoot() {
        BossDefinition base = parse("{\"id\":\"b\",\"loot\":[{\"item\":\"minecraft:dirt\"}],"
                + "\"variants\":{\"easy\":{\"lootMultiplier\":0.1,\"loot\":[{\"item\":\"minecraft:stick\",\"count\":4}]}}}");
        BossDefinition easy = BossVariants.apply(base, "easy");
        assertEquals(1, easy.loot.size());
        assertEquals("minecraft:stick", easy.loot.get(0).item);
        assertEquals(1, easy.loot.get(0).count);
        assertEquals(4, base.variants.get("easy").loot.get(0).count);
    }
}
