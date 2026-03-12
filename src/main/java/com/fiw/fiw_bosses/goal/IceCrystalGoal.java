package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Summons an ice snowflake crystal pattern around the boss, dealing damage
 * and applying slow/freeze effects to nearby players.
 *
 * JSON params:
 *   radius          (float, 10.0)  outer radius of the snowflake arms
 *   centerRadius    (float,  4.0)  radius within which heavy freeze is applied
 *   damage          (float,  8.0)  base damage dealt on activation
 *   slowDuration    (int,    60)   ticks of slowness for players outside centerRadius
 *   freezeDuration  (int,    30)   ticks of heaviest slow for players inside centerRadius
 *   windupTicks     (int,    15)   ticks spent in windup before activation
 *   duration        (int,   100)   ticks the crystal field persists
 */
public class IceCrystalGoal extends Goal {

    private static final int STATE_WINDUP = 0;
    private static final int STATE_ACTIVE = 1;

    private final BossEntity boss;
    private final float radius;
    private final float centerRadius;
    private final float damage;
    private final int slowDuration;
    private final int freezeDuration;
    private final int windupTicks;
    private final int activeDuration;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private int state;
    private boolean active;

    public IceCrystalGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss           = boss;
        this.radius         = params.has("radius")         ? params.get("radius").getAsFloat()         : 10.0f;
        this.centerRadius   = params.has("centerRadius")   ? params.get("centerRadius").getAsFloat()   : 4.0f;
        this.damage         = params.has("damage")         ? params.get("damage").getAsFloat()          : 8.0f;
        this.slowDuration   = params.has("slowDuration")   ? params.get("slowDuration").getAsInt()     : 60;
        this.freezeDuration = params.has("freezeDuration") ? params.get("freezeDuration").getAsInt()   : 30;
        this.windupTicks    = params.has("windupTicks")    ? params.get("windupTicks").getAsInt()      : 15;
        this.activeDuration = params.has("duration")       ? params.get("duration").getAsInt()         : 100;
        this.cooldown       = cooldownTicks;
        this.cooldownTimer  = 0;
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
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void start() {
        tick   = 0;
        state  = STATE_WINDUP;
        active = true;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        if (state == STATE_WINDUP) {
            LivingEntity target = boss.getTarget();
            if (target != null) boss.getLookControl().lookAt(target, 360, 90);

            // Swirl SNOWFLAKE + END_ROD converging toward boss at decreasing radius
            double swirlRadius = radius * (1.0 - (double) tick / windupTicks) + 1.0;
            for (int i = 0; i < 4; i++) {
                double angle = Math.toRadians(i * 90.0 + tick * 24.0);
                double px = boss.getX() + Math.cos(angle) * swirlRadius;
                double pz = boss.getZ() + Math.sin(angle) * swirlRadius;
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        px, boss.getY() + 1.0, pz, 1, 0.05, 0.1, 0.05, 0.02);
                world.spawnParticles(ParticleTypes.END_ROD,
                        px, boss.getY() + 1.2, pz, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (tick >= windupTicks) {
                // Transition to active
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.5f, 0.8f);
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.2f, 1.0f);
                spawnCrystalBurst(world);
                applyEffects(world);
                state = STATE_ACTIVE;
                tick  = 0;
            }

        } else if (state == STATE_ACTIVE) {
            if (tick % 4 == 0) {
                spawnCrystalMaintain(world);
            }

            int fadeStart = activeDuration - 20;
            if (tick >= fadeStart && tick % 2 == 0) {
                spawnFadeParticles(world);
            }

            if (tick >= activeDuration) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 1.5f);
                active = false;
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }

    // ── Crystal helpers ──────────────────────────────────────────────────────

    private void spawnCrystalBurst(ServerWorld world) {
        double bx = boss.getX();
        double by = boss.getY();
        double bz = boss.getZ();

        // 6 snowflake arms
        for (int arm = 0; arm < 6; arm++) {
            double armAngle = Math.toRadians(arm * 60.0);
            double ax = Math.cos(armAngle);
            double az = Math.sin(armAngle);

            for (double d = 1.0; d <= radius; d += 0.6) {
                double px = bx + ax * d;
                double pz = bz + az * d;
                double maxH = 3.0 + (radius - d) * 0.3;

                // Vertical pillar of END_ROD
                for (double h = 0; h <= maxH; h += 0.4) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            px, by + h, pz, 1, 0.05, 0.0, 0.05, 0.0);
                }
                // SNOWFLAKE at ground level
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        px, by + 0.1, pz, 3, 0.2, 0.1, 0.2, 0.02);
            }

            // Side branches at 55% of radius
            double branchDist = radius * 0.55;
            double bpx = bx + ax * branchDist;
            double bpz = bz + az * branchDist;

            for (int sign = -1; sign <= 1; sign += 2) {
                double branchAngle = armAngle + Math.toRadians(sign * 30.0);
                double branchAx = Math.cos(branchAngle);
                double branchAz = Math.sin(branchAngle);
                double branchLen = radius * 0.35;

                for (double bd = 0.5; bd <= branchLen; bd += 0.5) {
                    double bx2 = bpx + branchAx * bd;
                    double bz2 = bpz + branchAz * bd;

                    for (double h = 0; h <= 2.0; h += 0.4) {
                        world.spawnParticles(ParticleTypes.END_ROD,
                                bx2, by + h, bz2, 1, 0.05, 0.0, 0.05, 0.0);
                    }
                    world.spawnParticles(ParticleTypes.SNOWFLAKE,
                            bx2, by + 0.1, bz2, 2, 0.15, 0.1, 0.15, 0.01);
                }
            }
        }

        // Center burst
        world.spawnParticles(ParticleTypes.END_ROD,
                bx, by + 1.0, bz, 20, 0.4, 0.6, 0.4, 0.08);
        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                bx, by + 1.0, bz, 30, 0.5, 0.8, 0.5, 0.05);
    }

    private void spawnCrystalMaintain(ServerWorld world) {
        double bx = boss.getX();
        double by = boss.getY();
        double bz = boss.getZ();

        for (int arm = 0; arm < 6; arm++) {
            double armAngle = Math.toRadians(arm * 60.0);
            double ax = Math.cos(armAngle);
            double az = Math.sin(armAngle);

            for (double d = 1.0; d <= radius; d += 2.0) {
                double px = bx + ax * d;
                double pz = bz + az * d;
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        px, by + 1.0, pz, 1, 0.1, 0.3, 0.1, 0.02);
                world.spawnParticles(ParticleTypes.END_ROD,
                        px, by + 2.5, pz, 1, 0.05, 0.1, 0.05, 0.0);
            }
        }

        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                bx, by + 1.0, bz, 3, 0.3, 0.3, 0.3, 0.02);
    }

    private void spawnFadeParticles(ServerWorld world) {
        double bx = boss.getX();
        double by = boss.getY();
        double bz = boss.getZ();

        for (int arm = 0; arm < 6; arm++) {
            double armAngle = Math.toRadians(arm * 60.0);
            double ax = Math.cos(armAngle);
            double az = Math.sin(armAngle);

            double d = 1.0 + boss.getRandom().nextDouble() * (radius - 1.0);
            double px = bx + ax * d;
            double pz = bz + az * d;
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    px, by + 0.5, pz, 3, 0.3, 0.5, 0.3, 0.1);
        }
    }

    private void applyEffects(ServerWorld world) {
        double bx = boss.getX();
        double by = boss.getY();
        double bz = boss.getZ();

        Box aoe = new Box(bx - radius, by - 2, bz - radius,
                          bx + radius, by + 3, bz + radius);

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, aoe,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (PlayerEntity player : players) {
            player.damage(boss.getDamageSources().magic(), damage);

            double dist = player.distanceTo(boss);
            if (dist <= centerRadius) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, freezeDuration, 7, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.MINING_FATIGUE, freezeDuration, 2, false, true));

                Vec3d vel = player.getVelocity();
                player.setVelocity(0.0, vel.y > 0 ? vel.y : 0.0, 0.0);
                player.velocityModified = true;
            } else {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, slowDuration, 3, false, true));
            }
        }
    }
}
