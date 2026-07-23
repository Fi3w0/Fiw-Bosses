package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.core.FiwBossesCore;
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
        register("charge", ChargeGoal::new);
        register("pull", PullGoal::new);
        register("swap", SwapGoal::new);
        register("summon_minions", SummonMinionsGoal::new);
        register("ranged_projectile", RangedProjectileAttackGoal::new);
        register("meteor", MeteorGoal::new);
        register("slam", SlamGoal::new);
        register("shockwave", ShockwaveGoal::new);
        register("beam", BeamGoal::new);
        register("chain_lightning", ChainLightningGoal::new);
        register("sonic_boom", SonicBoomGoal::new);
        register("slash_wave", SlashWaveGoal::new);
        register("freeze", FreezeGoal::new);
        register("arc_slash", ArcSlashGoal::new);
        register("particle_tornado", ParticleTornadoGoal::new);
        register("orbital", OrbitalGoal::new);
        register("fire_arrow", FireArrowGoal::new);
        register("ice_crystal", IceCrystalGoal::new);
        register("ground_spike", GroundSpikeGoal::new);
        register("lightning_radial", LightningRadialGoal::new);
        register("arrow_rain", ArrowRainGoal::new);
        register("crimson_slash", CrimsonSlashGoal::new);
        register("orb_throw", OrbThrowGoal::new);
        register("tracking_orb", TrackingOrbGoal::new);
        register("detect_mark", DetectMarkGoal::new);
        register("judgment_mark", JudgmentMarkGoal::new);
        register("divine_execution", DivineExecutionGoal::new);
        register("essence_absorption", EssenceAbsorptionGoal::new);
        register("guardian_shield", GuardianShieldGoal::new);
        register("moving_tornado", MovingTornadoGoal::new);
        register("phantom_dash", PhantomDashGoal::new);
        register("potion_field", PotionFieldGoal::new);
        register("singularity_cannon", SingularityCannonGoal::new);
        register("domain", DomainGoal::new);
        register("rift_cleave", RiftCleaveGoal::new);
        register("fear_burst", FearBurstGoal::new);
        register("mirror_image", MirrorImageGoal::new);
        register("sacrifice_minion", SacrificeMinionGoal::new);
        register("last_breath", LastBreathGoal::new);
        register("wither_crown", WitherCrownGoal::new);
        register("cleanse", CleanseGoal::new);
        register("second_wind", SecondWindGoal::new);
        register("adaptation", AdaptationGoal::new);
        register("rewind", RewindGoal::new);
        register("gravity_well", GravityWellGoal::new);
        register("shadow_clone", ShadowCloneGoal::new);
        register("blink_strike", BlinkStrikeGoal::new);
        register("curse_bomb", CurseBombGoal::new);
        register("soul_tether", SoulTetherGoal::new);
        register("wind_charge", WindChargeGoal::new);
    }

    static void register(String key, BossGoalFactory factory) {
        REGISTRY.put(key, factory);
    }

    static Goal create(String type, BossEntity boss, int cooldownTicks, JsonObject params) {
        BossGoalFactory factory = REGISTRY.get(type);
        if (factory == null) {
            FiwBossesCore.LOGGER.warn("Unknown ability type '{}' for boss '{}' — ability will be skipped", type, boss.getBossId());
            return new Goal() {
                { setFlags(EnumSet.noneOf(Flag.class)); }
                @Override public boolean canUse() { return false; }
            };
        }
        return factory.create(boss, cooldownTicks, params);
    }
}
