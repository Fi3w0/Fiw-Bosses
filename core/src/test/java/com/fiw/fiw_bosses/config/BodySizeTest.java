package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodySizeTest {

    @Test
    void scaleIsClampedToTheVanillaAttributeRange() {
        assertEquals(1.0f, BodySize.scale(1.0f));
        assertEquals(16.0f, BodySize.scale(100f));
        assertEquals(0.0625f, BodySize.scale(0f));
        assertEquals(0.0625f, BodySize.scale(-3f));
        assertEquals(1.0f, BodySize.scale(Float.NaN));
    }

    @Test
    void meleeReachShrinksOnlyBelowNormalSize() {
        assertEquals(1.0, BodySize.meleeReachFactor(1f));
        assertEquals(1.0, BodySize.meleeReachFactor(3f));
        assertEquals(0.5, BodySize.meleeReachFactor(0.5f));
        assertEquals(BodySize.MIN_SCALE, BodySize.meleeReachFactor(0f), 1e-6);
        assertEquals(0.828, BodySize.VANILLA_MELEE_REACH, 0.001);
    }

    @Test
    void reachIgnoresNegativeAndCapsLargeValues() {
        assertEquals(0f, BodySize.reach(-2f));
        assertEquals(0f, BodySize.reach(Float.NaN));
        assertEquals(3f, BodySize.reach(3f));
        assertEquals(16f, BodySize.reach(40f));
    }

    @Test
    void definitionsDefaultToNormalSizeAndReadBothKeys() {
        Gson gson = new Gson();
        BossDefinition plain = gson.fromJson("{\"id\":\"b\"}", BossDefinition.class);
        assertEquals(1.0f, plain.scale);
        assertEquals(0f, plain.reach);
        BossDefinition big = gson.fromJson("{\"id\":\"b\",\"scale\":2.5,\"reach\":1.5}", BossDefinition.class);
        assertEquals(2.5f, big.scale);
        assertEquals(1.5f, big.reach);
        MinionDefinition minion = gson.fromJson("{\"id\":\"m\",\"scale\":0.5}", MinionDefinition.class);
        assertEquals(0.5f, minion.scale);
    }

    @Test
    void variantsKeepTheBossSize() {
        BossDefinition base = new Gson().fromJson(
                "{\"id\":\"b\",\"scale\":3.0,\"reach\":2.0,\"variants\":{\"hard\":{\"healthMultiplier\":2.0}}}",
                BossDefinition.class);
        BossDefinition hard = BossVariants.apply(base, "hard");
        assertEquals(3.0f, hard.scale);
        assertEquals(2.0f, hard.reach);
    }
}
