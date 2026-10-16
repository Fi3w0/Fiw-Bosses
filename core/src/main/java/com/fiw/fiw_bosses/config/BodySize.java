package com.fiw.fiw_bosses.config;

/** Limits for the root-level {@code scale} and {@code reach} keys of bosses and custom minions. */
public final class BodySize {
    // Same range as vanilla's minecraft:scale attribute, so every version behaves alike.
    public static final float MIN_SCALE = 0.0625f;
    public static final float MAX_SCALE = 16.0f;
    public static final float MAX_REACH = 16.0f;
    /** Vanilla's default melee margin around a mob's hitbox (Mob.DEFAULT_ATTACK_REACH, 1.20.5+). */
    public static final double VANILLA_MELEE_REACH = Math.sqrt(2.04F) - 0.6F;

    private BodySize() {}

    public static float scale(float configured) {
        if (Float.isNaN(configured)) return 1.0f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, configured));
    }

    /** Extra melee reach in blocks; negative or invalid values mean none. */
    public static float reach(float configured) {
        if (Float.isNaN(configured) || configured <= 0f) return 0f;
        return Math.min(MAX_REACH, configured);
    }

    /** Vanilla melee margin does not follow size; shrink it for bodies below 1x so tiny bosses do not out-reach their hitbox. */
    public static double meleeReachFactor(float scale) {
        return Math.min(1.0f, scale(scale));
    }
}
