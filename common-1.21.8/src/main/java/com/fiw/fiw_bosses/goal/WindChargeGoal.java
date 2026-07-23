package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Custom "mace jump-slam" ability, independent of whatever is actually equipped in the
 * boss's mainhand. Leaps toward the target and slams down on landing for a fully
 * ability-defined amount of damage/knockback — a boss can visually wield a mace (equip
 * "minecraft:mace" in equipment.mainHand) and still hit for 1 damage here, or the reverse,
 * a bare-fisted boss can hit like a real mace smash. This never reads the held item's
 * attack-damage attribute.
 * <p>
 * This is the "custom" implementation. The "vanilla" implementation is just equipping a
 * real minecraft:mace — Mob-held maces already get the authentic unpredictable fall-based
 * smash mechanic for free with zero code here; the two are independent and can be combined.
 */
public class WindChargeGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final float fallDamagePerBlock;
    private final float maxFallBonus;
    private final float radius;
    private final float knockback;
    private final float launchPower;
    private final float jumpPower;
    private final float horizontalSpeed;
    private final boolean selfNoFallDamage;
    private final float minRange;
    private final float maxRange;
    private final int cooldown;
    private final String taunt;

    private int cooldownTimer;
    private int ticksAirborne;
    private boolean hasLaunched;
    private boolean hasLanded;
    private double peakY;

    private static final int MAX_AIRBORNE_TICKS = 100;

    public WindChargeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 8.0f;
        this.fallDamagePerBlock = params.has("fallDamagePerBlock") ? params.get("fallDamagePerBlock").getAsFloat() : 0.0f;
        this.maxFallBonus = params.has("maxFallBonus") ? params.get("maxFallBonus").getAsFloat() : 20.0f;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 3.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsFloat() : 1.1f;
        this.launchPower = params.has("launchPower") ? params.get("launchPower").getAsFloat() : 0.7f;
        this.jumpPower = params.has("jumpPower") ? params.get("jumpPower").getAsFloat() : 1.0f;
        this.horizontalSpeed = params.has("horizontalSpeed") ? params.get("horizontalSpeed").getAsFloat() : 0.5f;
        this.selfNoFallDamage = !params.has("selfNoFallDamage") || params.get("selfNoFallDamage").getAsBoolean();
        this.minRange = params.has("minRange") ? params.get("minRange").getAsFloat() : 3.0f;
        this.maxRange = params.has("maxRange") ? params.get("maxRange").getAsFloat() : 12.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive() || !boss.onGround()) return false;
        double dist = boss.distanceTo(target);
        return dist >= minRange && dist <= maxRange;
    }

    @Override
    public void start() {
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        Vec3 dir = new Vec3(target.getX() - boss.getX(), 0, target.getZ() - boss.getZ());
        if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize(); else dir = Vec3.ZERO;

        boss.setDeltaMovement(dir.x * horizontalSpeed, jumpPower, dir.z * horizontalSpeed);
        boss.hurtMarked = true;

        if (selfNoFallDamage) boss.ignoreNextFallDamage();

        ticksAirborne = 0;
        hasLaunched = true;
        hasLanded = false;
        peakY = boss.getY();

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.3f, 0.6f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WIND_CHARGE_THROW, SoundSource.HOSTILE, 1.5f, 0.8f);

            if (taunt != null) {
                var bossName = boss.getCustomName();
                Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                        .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                        .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(taunt));
                for (var player : level.players()) {
                    if (player.distanceToSqr(boss) <= 48 * 48) {
                        player.sendSystemMessage(tauntText);
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return hasLaunched && !hasLanded && ticksAirborne < MAX_AIRBORNE_TICKS;
    }

    @Override
    public void tick() {
        ticksAirborne++;
        peakY = Math.max(peakY, boss.getY());

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.SMALL_GUST,
                    boss.getX(), boss.getY() + 0.5, boss.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
        }

        // Only start checking for landing after leaving the ground, so we don't
        // mistake the takeoff tick (still onGround) for a landing.
        if (ticksAirborne > 2 && boss.onGround()) {
            performSlam();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        hasLaunched = false;
        hasLanded = true;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;
    }

    private void performSlam() {
        hasLanded = true;
        if (boss.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) boss.level();
        Vec3 center = boss.position();
        double fellBlocks = Math.max(0, peakY - boss.getY());
        float fallBonus = Math.min(fallDamagePerBlock * (float) fellBlocks, maxFallBonus);
        float finalDamage = damage + fallBonus;

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.9f);

        AABB aoeBox = boss.getBoundingBox().inflate(radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aoeBox,
                e -> boss.canAbilityHit(e));

        for (LivingEntity entity : entities) {
            double dist = entity.distanceTo(boss);
            if (dist > radius) continue;

            entity.hurtServer(level, boss.damageSources().mobAttack(boss), finalDamage);

            Vec3 dir = entity.position().subtract(center);
            Vec3 push = dir.lengthSqr() > 1.0E-4 ? dir.normalize() : Vec3.ZERO;
            entity.push(push.x * knockback, launchPower, push.z * knockback);
            entity.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                center.x, center.y + 0.1, center.z, 1, 0, 0, 0, 0);
        for (int ring = 0; ring < 2; ring++) {
            double ringRadius = radius * (0.4 + ring * 0.4);
            int count = 10 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians((360.0 / count) * i);
                double px = center.x + Math.cos(angle) * ringRadius;
                double pz = center.z + Math.sin(angle) * ringRadius;
                level.sendParticles(ParticleTypes.GUST,
                        px, center.y + 0.1, pz, 1, 0, 0, 0, 0);
            }
        }
    }
}
