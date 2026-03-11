package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.ai.goal.Goal;

import java.util.HashMap;
import java.util.Map;

@FunctionalInterface
public interface BossGoalFactory {

    Goal create(BossEntity boss, int cooldownTicks, JsonObject params);

    Map<String, BossGoalFactory> REGISTRY = new HashMap<>();

    static void init() {
        register("melee_slash", MeleeSlashAttackGoal::new);
        register("dodge", DodgeGoal::new);
        register("aoe_smash", AoeSmashAttackGoal::new);
        register("summon_minions", SummonMinionsGoal::new);
        register("ranged_projectile", RangedProjectileAttackGoal::new);
        register("teleport", TeleportGoal::new);
        register("charge", ChargeGoal::new);
        register("shield", ShieldGoal::new);
        register("heal", HealGoal::new);
        register("meteor", MeteorGoal::new);
        register("chain_lightning", ChainLightningGoal::new);
        register("orbital", OrbitalGoal::new);
        register("beam", BeamGoal::new);
        register("pull", PullGoal::new);
        register("arc_slash", ArcSlashGoal::new);
        register("slam", SlamGoal::new);
        register("flames", FlamesGoal::new);
        register("freeze", FreezeGoal::new);
        register("random_message", RandomMessageGoal::new);
        register("particle_tornado", ParticleTornadoGoal::new);
        register("swap", SwapGoal::new);
    }

    static void register(String key, BossGoalFactory factory) {
        REGISTRY.put(key, factory);
    }

    static Goal create(String type, BossEntity boss, int cooldownTicks, JsonObject params) {
        BossGoalFactory factory = REGISTRY.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown boss ability type: " + type);
        }
        return factory.create(boss, cooldownTicks, params);
    }
}
