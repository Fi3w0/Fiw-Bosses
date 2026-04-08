package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Warden-style sonic boom — boss charges then fires a devastating sound pulse.
 * The charge is telegraphed by a rising rumble and particle buildup.
 * The boom hits all players in a cone ahead and ignores some armor.
 *
 * JSON params:
 *   damage       (float, 30.0)   damage on hit (ignores armor)
 *   radius       (float, 15.0)   max range of the boom
 *   coneAngle    (float, 60.0)   cone half-angle in degrees (180 = full circle)
 *   chargeTime   (int,   40)     ticks to charge before firing
 *   knockback    (float,  2.0)   knockback strength
 *   darkness     (boolean, true) apply darkness effect to hit players
 */
public class SonicBoomGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final float radius;
    private final float coneAngle;
    private final int chargeTime;
    private final float knockback;
    private final boolean darkness;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;

    private Vec3d lockedForward;  // direction locked at moment of fire
    private Vec3d chargeOrigin;

    public SonicBoomGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()     : 30.0f;
        this.radius     = params.has("radius")     ? params.get("radius").getAsFloat()     : 15.0f;
        this.coneAngle  = params.has("coneAngle")  ? params.get("coneAngle").getAsFloat()  : 60.0f;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()   : 40;
        this.knockback  = params.has("knockback")  ? params.get("knockback").getAsFloat()  : 2.0f;
        this.darkness   = !params.has("darkness")  || params.get("darkness").getAsBoolean();
        this.cooldown   = cooldownTicks;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 4;
    }

    @Override
    public void start() {
        tick = 0;
        chargeOrigin = boss.getPos();

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);

        if (!boss.getWorld().isClient) {
            boss.getWorld().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
    }

    @Override
    public boolean shouldContinue() {
        return tick < chargeTime + 5;
    }

    @Override
    public void tick() {
        tick++;
        boss.setVelocity(0, boss.getVelocity().y, 0);
        boss.velocityModified = true;

        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Keep facing target during charge
        if (tick < chargeTime) {
            LivingEntity target = boss.getTarget();
            if (target != null) boss.getLookControl().lookAt(target, 360, 90);
        }

        // ── CHARGE PHASE ─────────────────────────────────────────────────────
        if (tick < chargeTime) {
            float progress = (float) tick / chargeTime;

            // Increasingly frantic vibrating rings around the boss
            int rings = 2 + (int)(progress * 4);
            for (int r = 0; r < rings; r++) {
                double ringRadius = 0.4 + r * 0.35 + progress * 0.5;
                int points = 8 + r * 4;
                double angleOffset = tick * 15.0 * (r % 2 == 0 ? 1 : -1);
                for (int i = 0; i < points; i++) {
                    double angle = Math.toRadians(360.0 / points * i + angleOffset);
                    double px = boss.getX() + Math.cos(angle) * ringRadius;
                    double pz = boss.getZ() + Math.sin(angle) * ringRadius;
                    double py = boss.getY() + 1.0 + Math.sin(tick * 0.4 + r) * 0.2;
                    world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            // Rising soul particles above boss head
            world.spawnParticles(ParticleTypes.SOUL,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    (int)(1 + progress * 4), 0.25, 0.3, 0.25, 0.04);

            // Final warning — full sphere collapse at 80% charge
            if (tick == (int)(chargeTime * 0.8f)) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 1.3f);
            }
            return;
        }

        // ── FIRE ─────────────────────────────────────────────────────────────
        if (tick == chargeTime) {
            Vec3d fwd = boss.getRotationVec(1.0f);
            lockedForward = new Vec3d(fwd.x, 0, fwd.z).normalize();

            // BOOM sounds
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 1.0f);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.4f);

            fireBoom(world);
        }

        // ── AFTER-BOOM VISUAL TRAIL ───────────────────────────────────────────
        if (tick > chargeTime && tick <= chargeTime + 5) {
            double dist = (tick - chargeTime) / 5.0 * radius;
            Vec3d tip = chargeOrigin.add(lockedForward.multiply(dist)).add(0, 1.0, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, tip.x, tip.y, tip.z,
                    6, 0.6, 0.4, 0.6, 0.15);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void fireBoom(ServerWorld world) {
        double cosHalf = Math.cos(Math.toRadians(coneAngle));

        Box scanBox = new Box(
                boss.getX() - radius, boss.getY() - 2, boss.getZ() - radius,
                boss.getX() + radius, boss.getY() + 4, boss.getZ() + radius);

        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, scanBox,
                e -> e != boss && e.isAlive() && !boss.isMinion(e));

        for (LivingEntity entity : targets) {
            Vec3d toEntity = entity.getPos().subtract(boss.getPos());
            double dist = toEntity.length();
            if (dist > radius) continue;

            // Cone check (skip if full-circle)
            if (coneAngle < 180.0) {
                Vec3d toEntityH = new Vec3d(toEntity.x, 0, toEntity.z).normalize();
                if (toEntityH.dotProduct(lockedForward) < cosHalf) continue;
            }

            // Sonic boom ignores armor — use magic damage source
            entity.damage(boss.getDamageSources().magic(), damage);

            // Knockback away from boss along the boom direction
            Vec3d knockDir = new Vec3d(toEntity.x, 0.3, toEntity.z).normalize();
            double falloff = 1.0 - (dist / radius) * 0.5;
            entity.addVelocity(
                    knockDir.x * knockback * falloff,
                    0.5 + knockDir.y * knockback * falloff,
                    knockDir.z * knockback * falloff);
            entity.velocityModified = true;

            // Darkness effect
            if (darkness) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0, false, false));
            }

            // Hit particles
            world.spawnParticles(ParticleTypes.SONIC_BOOM,
                    entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(),
                    1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP,
                    entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(),
                    10, 0.5, 0.4, 0.5, 0.2);
        }

        // Boom shockwave ring at ground level
        int ringPoints = 40;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(360.0 / ringPoints * i);
            // Only draw the cone portion
            Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            if (coneAngle < 180.0 && dir.dotProduct(lockedForward) < cosHalf) continue;
            double px = boss.getX() + Math.cos(angle) * radius;
            double pz = boss.getZ() + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, px, boss.getY() + 0.1, pz, 1, 0, 0.1, 0, 0.05);
        }

        // Center burst
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SOUL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                15, 1.0, 0.5, 1.0, 0.12);
    }
}
