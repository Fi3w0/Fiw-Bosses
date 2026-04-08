package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShockwaveGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final int waves;
    private final float maxRadius;
    private final float waveSpeed;
    private final float knockback;
    private final int windupTicks;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;

    private float[] waveRadii;
    private int[] waveLaunchTick;
    private Set<UUID>[] waveHit;

    private Vec3 slamOrigin;

    private static final float RING_THICKNESS = 1.0f;

    public ShockwaveGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.damage      = params.has("damage")      ? params.get("damage").getAsFloat()      : 8.0f;
        this.waves       = Math.max(1, params.has("waves")  ? params.get("waves").getAsInt()  : 3);
        this.maxRadius   = params.has("maxRadius")   ? params.get("maxRadius").getAsFloat()   : 14.0f;
        this.waveSpeed   = params.has("waveSpeed")   ? params.get("waveSpeed").getAsFloat()   : 0.55f;
        this.knockback   = params.has("knockback")   ? params.get("knockback").getAsFloat()   : 0.5f;
        this.windupTicks = params.has("windupTicks") ? params.get("windupTicks").getAsInt()   : 20;
        this.cooldown    = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        tick = 0;
        slamOrigin = boss.position();
        waveRadii = new float[waves];
        waveLaunchTick = new int[waves];
        waveHit = new HashSet[waves];

        int spacing = Math.max(4, (int)(maxRadius / waveSpeed / waves));
        for (int i = 0; i < waves; i++) {
            waveRadii[i] = 0;
            waveLaunchTick[i] = windupTicks + i * spacing;
            waveHit[i] = new HashSet<>();
        }

        if (!boss.level().isClientSide) {
            boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 0.5f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        for (int i = 0; i < waves; i++) {
            if (tick <= waveLaunchTick[i] || waveRadii[i] < maxRadius) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (tick <= windupTicks) {
            float progress = (float) tick / windupTicks;
            for (int i = 0; i < 3; i++) {
                double ang = Math.random() * Math.PI * 2;
                double r = Math.random() * 1.5;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        slamOrigin.x + Math.cos(ang) * r,
                        slamOrigin.y + progress * 1.5,
                        slamOrigin.z + Math.sin(ang) * r,
                        1, 0, 0.04, 0, 0.01);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    slamOrigin.x, slamOrigin.y + 0.5, slamOrigin.z,
                    1, 0.3, 0.1, 0.3, 0.04);

            if (tick == windupTicks) {
                level.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.5f, 0.25f);
                level.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.45f);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z,
                        20, maxRadius * 0.3, 0.2, maxRadius * 0.3, 0.06);
                level.sendParticles(ParticleTypes.CRIT,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z,
                        12, 0.8, 0.3, 0.8, 0.3);
            }
            return;
        }

        for (int w = 0; w < waves; w++) {
            if (tick < waveLaunchTick[w]) continue;

            if (tick == waveLaunchTick[w]) {
                waveRadii[w] = 0.5f;
                float pitch = 1.0f - w * 0.15f;
                level.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2f, pitch);
            } else {
                waveRadii[w] = Math.min(maxRadius, waveRadii[w] + waveSpeed);
            }

            float r = waveRadii[w];
            if (r >= maxRadius) continue;

            int points = Math.max(16, (int)(r * 6));
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians(360.0 / points * i);
                double px = slamOrigin.x + Math.cos(angle) * r;
                double pz = slamOrigin.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.CRIT,
                        px, slamOrigin.y + 0.08, pz, 1, 0, 0.12, 0, 0.12);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            px, slamOrigin.y + 0.12, pz, 1, 0.05, 0.06, 0.05, 0.01);
                }
            }

            AABB scanBox = new AABB(
                    slamOrigin.x - r - 1, slamOrigin.y - 0.5, slamOrigin.z - r - 1,
                    slamOrigin.x + r + 1, slamOrigin.y + 3.0, slamOrigin.z + r + 1);

            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, scanBox,
                    e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

            for (LivingEntity entity : nearby) {
                if (waveHit[w].contains(entity.getUUID())) continue;

                double dx = entity.getX() - slamOrigin.x;
                double dz = entity.getZ() - slamOrigin.z;
                double dist2D = Math.sqrt(dx * dx + dz * dz);

                boolean inRing = dist2D >= r - RING_THICKNESS && dist2D <= r + RING_THICKNESS * 0.4;
                if (!inRing) continue;

                boolean jumping = entity.getDeltaMovement().y > 0.15;
                if (jumping) continue;

                entity.hurt(boss.damageSources().mobAttack(boss), damage);
                waveHit[w].add(entity.getUUID());

                Vec3 dir = new Vec3(dx, 0, dz).normalize();
                entity.push(dir.x * knockback, 0.35, dir.z * knockback);
                entity.hasImpulse = true;

                level.sendParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        8, 0.4, 0.2, 0.4, 0.2);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 0.8f, 0.8f);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
