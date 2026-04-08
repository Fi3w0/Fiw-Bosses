package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Boss slams the ground and sends out expanding shockwave rings.
 * Players on the ground caught by a ring take damage — they must jump over it.
 *
 * JSON params:
 *   damage       (float,  8.0)   damage per ring hit
 *   waves        (int,    3)     number of rings
 *   maxRadius    (float, 14.0)   max distance the rings travel
 *   waveSpeed    (float,  0.55)  blocks per tick each ring expands
 *   knockback    (float,  0.5)   horizontal knockback on hit
 *   windupTicks  (int,   20)     charge ticks before slam
 */
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
    private int[] waveLaunchTick;     // tick at which each wave launches
    private Set<UUID>[] waveHit;      // entities already hit by each wave

    private Vec3d slamOrigin;

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
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        tick = 0;
        slamOrigin = boss.getPos();
        waveRadii = new float[waves];
        waveLaunchTick = new int[waves];
        waveHit = new HashSet[waves];

        // Stagger wave launches evenly after windup
        int spacing = Math.max(4, (int)(maxRadius / waveSpeed / waves));
        for (int i = 0; i < waves; i++) {
            waveRadii[i] = 0;
            waveLaunchTick[i] = windupTicks + i * spacing;
            waveHit[i] = new HashSet<>();
        }

        if (!boss.getWorld().isClient) {
            boss.getWorld().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
    }

    @Override
    public boolean shouldContinue() {
        // Keep running until all waves reach maxRadius
        for (int i = 0; i < waves; i++) {
            if (tick <= waveLaunchTick[i] || waveRadii[i] < maxRadius) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // ── WINDUP ───────────────────────────────────────────────────────────
        if (tick <= windupTicks) {
            float progress = (float) tick / windupTicks;
            // Rising rubble + energy buildup at boss feet
            for (int i = 0; i < 3; i++) {
                double ang = Math.random() * Math.PI * 2;
                double r = Math.random() * 1.5;
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        slamOrigin.x + Math.cos(ang) * r,
                        slamOrigin.y + progress * 1.5,
                        slamOrigin.z + Math.sin(ang) * r,
                        1, 0, 0.04, 0, 0.01);
            }
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    slamOrigin.x, slamOrigin.y + 0.5, slamOrigin.z,
                    1, 0.3, 0.1, 0.3, 0.04);

            if (tick == windupTicks) {
                // SLAM impact
                world.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.5f, 0.25f);
                world.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.45f);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z,
                        20, maxRadius * 0.3, 0.2, maxRadius * 0.3, 0.06);
                world.spawnParticles(ParticleTypes.CRIT,
                        slamOrigin.x, slamOrigin.y + 0.1, slamOrigin.z,
                        12, 0.8, 0.3, 0.8, 0.3);
            }
            return;
        }

        // ── WAVE EXPANSION ────────────────────────────────────────────────────
        for (int w = 0; w < waves; w++) {
            if (tick < waveLaunchTick[w]) continue;

            // Advance this ring outward
            if (tick == waveLaunchTick[w]) {
                waveRadii[w] = 0.5f;
                float pitch = 1.0f - w * 0.15f;
                world.playSound(null, slamOrigin.x, slamOrigin.y, slamOrigin.z,
                        SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.2f, pitch);
            } else {
                waveRadii[w] = Math.min(maxRadius, waveRadii[w] + waveSpeed);
            }

            float r = waveRadii[w];
            if (r >= maxRadius) continue;

            // ── Draw ring ─────────────────────────────────────────────────
            int points = Math.max(16, (int)(r * 6));
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians(360.0 / points * i);
                double px = slamOrigin.x + Math.cos(angle) * r;
                double pz = slamOrigin.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.CRIT,
                        px, slamOrigin.y + 0.08, pz, 1, 0, 0.12, 0, 0.12);
                if (i % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            px, slamOrigin.y + 0.12, pz, 1, 0.05, 0.06, 0.05, 0.01);
                }
            }

            // ── Hit detection ─────────────────────────────────────────────
            Box scanBox = new Box(
                    slamOrigin.x - r - 1, slamOrigin.y - 0.5, slamOrigin.z - r - 1,
                    slamOrigin.x + r + 1, slamOrigin.y + 3.0, slamOrigin.z + r + 1);

            List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, scanBox,
                    e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

            for (LivingEntity entity : nearby) {
                if (waveHit[w].contains(entity.getUuid())) continue;

                double dx = entity.getX() - slamOrigin.x;
                double dz = entity.getZ() - slamOrigin.z;
                double dist2D = Math.sqrt(dx * dx + dz * dz);

                boolean inRing = dist2D >= r - RING_THICKNESS && dist2D <= r + RING_THICKNESS * 0.4;
                if (!inRing) continue;

                // Safe if jumping (upward velocity or airborne + not just walked off edge)
                boolean jumping = entity.getVelocity().y > 0.15;
                if (jumping) continue;

                entity.damage(boss.getDamageSources().mobAttack(boss), damage);
                waveHit[w].add(entity.getUuid());

                Vec3d dir = new Vec3d(dx, 0, dz).normalize();
                entity.addVelocity(dir.x * knockback, 0.35, dir.z * knockback);
                entity.velocityModified = true;

                world.spawnParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        8, 0.4, 0.2, 0.4, 0.2);
                world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.HOSTILE, 0.8f, 0.8f);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
