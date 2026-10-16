package com.fiw.fiw_bosses.config;

import java.util.Set;

/**
 * Every ability {@code type} the goal factories register. Core cannot see the Minecraft
 * goal classes, so this list is kept equal to each BossGoalFactory by AbilityTypesTest.
 */
public final class AbilityTypes {

    public static final Set<String> KNOWN = Set.of(
            "melee_slash", "dodge", "flames", "random_message", "aoe_smash", "teleport", "shield",
            "heal", "charge", "pull", "swap", "summon_minions", "ranged_projectile", "meteor", "slam",
            "shockwave", "beam", "chain_lightning", "sonic_boom", "slash_wave", "freeze", "arc_slash",
            "particle_tornado", "orbital", "fire_arrow", "ice_crystal", "ground_spike",
            "lightning_radial", "arrow_rain", "crimson_slash", "orb_throw", "tracking_orb",
            "detect_mark", "judgment_mark", "divine_execution", "essence_absorption",
            "guardian_shield", "moving_tornado", "phantom_dash", "potion_field",
            "singularity_cannon", "domain", "rift_cleave", "fear_burst", "mirror_image",
            "sacrifice_minion", "last_breath", "wither_crown", "cleanse", "second_wind", "adaptation",
            "rewind", "gravity_well", "shadow_clone", "blink_strike", "curse_bomb", "soul_tether",
            "wind_charge");

    private AbilityTypes() {}
}
