package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@FunctionalInterface
public interface BossGoalFactory {

    Goal create(BossEntity boss, int cooldownTicks, JsonObject params);

    Map<String, BossGoalFactory> REGISTRY = new HashMap<>();

    static void init() {
        register("melee_slash", MeleeSlashAttackGoal::new);
        register("dodge", DodgeGoal::new);
        register("flames", FlamesGoal::new);
        register("random_message", RandomMessageGoal::new);
        register("aoe_smash", AoeSmashAttackGoal::new);
        register("teleport", TeleportGoal::new);
        register("shield", ShieldGoal::new);
        register("heal", HealGoal::new);
    }

    static void register(String key, BossGoalFactory factory) {
        REGISTRY.put(key, factory);
    }

    static Goal create(String type, BossEntity boss, int cooldownTicks, JsonObject params) {
        BossGoalFactory factory = REGISTRY.get(type);
        if (factory == null) {
            // Unknown ability type — return no-op goal until ported.
            return new Goal() {
                { setFlags(EnumSet.noneOf(Flag.class)); }
                @Override public boolean canUse() { return false; }
            };
        }
        return factory.create(boss, cooldownTicks, params);
    }
}
