package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

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

    private Vec3 lockedForward;
    private Vec3 chargeOrigin;

    public SonicBoomGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()     : 30.0f;
        this.radius     = params.has("radius")     ? params.get("radius").getAsFloat()     : 15.0f;
        this.coneAngle  = params.has("coneAngle")  ? params.get("coneAngle").getAsFloat()  : 60.0f;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()   : 40;
        this.knockback  = params.has("knockback")  ? params.get("knockback").getAsFloat()  : 2.0f;
        this.darkness   = !params.has("darkness")  || params.get("darkness").getAsBoolean();
        this.cooldown   = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 4;
    }

    @Override
    public void start() {
        tick = 0;
        chargeOrigin = boss.position();

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

        if (!boss.level().isClientSide()) {
            boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 0.6f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < chargeTime + 5;
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (tick < chargeTime) {
            LivingEntity target = boss.getTarget();
            if (target != null) boss.getLookControl().setLookAt(target, 360, 90);
        }

        if (tick < chargeTime) {
            float progress = (float) tick / chargeTime;

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
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            level.sendParticles(ParticleTypes.SOUL,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    (int)(1 + progress * 4), 0.25, 0.3, 0.25, 0.04);

            if (tick == (int)(chargeTime * 0.8f)) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 1.3f);
            }
            return;
        }

        if (tick == chargeTime) {
            Vec3 fwd = boss.getViewVector(1.0f);
            lockedForward = new Vec3(fwd.x, 0, fwd.z).normalize();

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 1.0f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.4f);

            fireBoom(level);
        }

        if (tick > chargeTime && tick <= chargeTime + 5) {
            double dist = (tick - chargeTime) / 5.0 * radius;
            Vec3 tip = chargeOrigin.add(lockedForward.scale(dist)).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, tip.x, tip.y, tip.z,
                    6, 0.6, 0.4, 0.6, 0.15);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void fireBoom(ServerLevel level) {
        double cosHalf = Math.cos(Math.toRadians(coneAngle));

        AABB scanBox = new AABB(
                boss.getX() - radius, boss.getY() - 2, boss.getZ() - radius,
                boss.getX() + radius, boss.getY() + 4, boss.getZ() + radius);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> e != boss && e.isAlive() && !boss.isMinion(e));

        for (LivingEntity entity : targets) {
            Vec3 toEntity = entity.position().subtract(boss.position());
            double dist = toEntity.length();
            if (dist > radius) continue;

            if (coneAngle < 180.0) {
                Vec3 toEntityH = new Vec3(toEntity.x, 0, toEntity.z).normalize();
                if (toEntityH.dot(lockedForward) < cosHalf) continue;
            }

            entity.hurtServer(level, boss.damageSources().magic(), damage);

            Vec3 knockDir = new Vec3(toEntity.x, 0.3, toEntity.z).normalize();
            double falloff = 1.0 - (dist / radius) * 0.5;
            entity.push(
                    knockDir.x * knockback * falloff,
                    0.5 + knockDir.y * knockback * falloff,
                    knockDir.z * knockback * falloff);
            entity.hurtMarked = true;

            if (darkness) {
                entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            }

            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    10, 0.5, 0.4, 0.5, 0.2);
        }

        int ringPoints = 40;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(360.0 / ringPoints * i);
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            if (coneAngle < 180.0 && dir.dot(lockedForward) < cosHalf) continue;
            double px = boss.getX() + Math.cos(angle) * radius;
            double pz = boss.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, boss.getY() + 0.1, pz, 1, 0, 0.1, 0, 0.05);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                15, 1.0, 0.5, 1.0, 0.12);
    }
}
