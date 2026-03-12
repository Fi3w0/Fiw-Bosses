package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Three consecutive energy claws travel along the ground toward the target,
 * culminating in a dark flame explosion. Each claw is larger than the last
 * and deals escalating damage.
 *
 * JSON params:
 *   damage             (float, 12.0)   base damage per claw hit
 *   range              (double, 16.0)  distance each claw travels
 *   clawCount          (int,     3)    number of sequential claws
 *   clawSpeed          (double,  1.2)  blocks per tick each claw advances
 *   explosionRadius    (float,   5.0)  blast radius of the final explosion
 *   delayBetweenClaws  (int,    12)    ticks between consecutive claw launches
 */
public class CrimsonSlashGoal extends Goal {

    private static final int STATE_WINDUP    = 0;
    private static final int STATE_SLASHING  = 1;
    private static final int STATE_EXPLOSION = 2;
    private static final int WINDUP_TICKS    = 15;

    private final BossEntity boss;
    private final float damage;
    private final double range;
    private final int clawCount;
    private final double clawSpeed;
    private final float explosionRadius;
    private final int delayBetweenClaws;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private int state;
    private boolean active;

    private int currentClaw;
    private int nextClawTick;
    private double clawSize;
    private Vec3d clawPos;
    private double clawTraveled;
    private Vec3d clawDir;

    private final List<Set<UUID>> hitPerClaw = new ArrayList<>();

    public CrimsonSlashGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss              = boss;
        this.damage            = params.has("damage")            ? params.get("damage").getAsFloat()            : 12.0f;
        this.range             = params.has("range")             ? params.get("range").getAsDouble()            : 16.0;
        this.clawCount         = params.has("clawCount")         ? params.get("clawCount").getAsInt()           : 3;
        this.clawSpeed         = params.has("clawSpeed")         ? params.get("clawSpeed").getAsDouble()        : 1.2;
        this.explosionRadius   = params.has("explosionRadius")   ? params.get("explosionRadius").getAsFloat()   : 5.0f;
        this.delayBetweenClaws = params.has("delayBetweenClaws") ? params.get("delayBetweenClaws").getAsInt()  : 12;
        this.cooldown          = cooldownTicks;
        this.cooldownTimer     = 0;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= range + 4;
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_WINDUP;
        active       = true;
        currentClaw  = 0;
        nextClawTick = WINDUP_TICKS;
        clawTraveled = 0;
        clawPos      = null;

        hitPerClaw.clear();
        for (int i = 0; i < clawCount; i++) {
            hitPerClaw.add(new HashSet<>());
        }

        // Compute initial clawDir from boss toward target
        LivingEntity target = boss.getTarget();
        if (target != null) {
            Vec3d toTarget = target.getPos().subtract(boss.getPos());
            Vec3d horizontal = new Vec3d(toTarget.x, 0, toTarget.z);
            clawDir = horizontal.lengthSquared() > 0.0001 ? horizontal.normalize() : boss.getRotationVector();
        } else {
            Vec3d fwd = boss.getRotationVector();
            clawDir = new Vec3d(fwd.x, 0, fwd.z).normalize();
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Always try to face target
        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);

        // ── WINDUP ──────────────────────────────────────────────────────────
        if (state == STATE_WINDUP && currentClaw == 0 && tick < nextClawTick) {
            // Crimson energy gathering particles
            double angle = Math.toRadians(tick * 30.0);
            for (int i = 0; i < 3; i++) {
                double a = angle + Math.toRadians(i * 120.0);
                double px = boss.getX() + Math.cos(a) * 1.5;
                double pz = boss.getZ() + Math.sin(a) * 1.5;
                world.spawnParticles(
                        new DustParticleEffect(new Vector3f(0.8f, 0.0f, 0.2f), 1.0f),
                        px, boss.getY() + 1.0, pz, 1, 0.05, 0.05, 0.05, 0.0);
            }
            world.spawnParticles(ParticleTypes.CRIT,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0.2, 0.2, 0.2, 0.05);
        }

        // ── LAUNCH CLAW ─────────────────────────────────────────────────────
        if (tick == nextClawTick && currentClaw < clawCount) {
            // Update clawDir toward current target position
            if (target != null) {
                Vec3d toTarget = target.getPos().subtract(boss.getPos());
                Vec3d horizontal = new Vec3d(toTarget.x, 0, toTarget.z);
                if (horizontal.lengthSquared() > 0.0001) {
                    clawDir = horizontal.normalize();
                }
            }

            clawSize     = 1.0 + currentClaw * 0.5;
            clawPos      = boss.getPos().add(0, 0.3, 0);
            clawTraveled = 0;
            state        = STATE_SLASHING;

            float pitch = 0.6f + currentClaw * 0.1f;
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, pitch);
        }

        // ── SLASHING ────────────────────────────────────────────────────────
        if (state == STATE_SLASHING && currentClaw < clawCount && clawPos != null) {
            int substeps = (int) Math.ceil(clawSpeed);
            double stepSize = clawSpeed / substeps;

            for (int s = 0; s < substeps; s++) {
                clawPos      = clawPos.add(clawDir.multiply(stepSize));
                clawTraveled += stepSize;

                spawnClawParticles(world, clawPos, currentClaw);
                checkClawHit(world, clawPos, currentClaw);

                if (clawTraveled >= range) {
                    currentClaw++;
                    if (currentClaw < clawCount) {
                        nextClawTick = tick + delayBetweenClaws;
                        state        = STATE_WINDUP;
                    } else {
                        triggerExplosion(world, clawPos);
                        state  = STATE_EXPLOSION;
                        active = false;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void spawnClawParticles(ServerWorld world, Vec3d pos, int clawIndex) {
        double halfWidth = clawSize * 0.8;
        Vec3d perp = new Vec3d(-clawDir.z, 0, clawDir.x);

        // Ground trail
        for (double w = -halfWidth; w <= halfWidth; w += 0.4) {
            Vec3d p = pos.add(perp.multiply(w));
            world.spawnParticles(ParticleTypes.CRIT,
                    p.x, p.y + 0.05, p.z, 1, 0.05, 0.0, 0.05, 0.0);
            world.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.8f, 0.0f, 0.3f), 1.2f),
                    p.x, p.y + 0.1, p.z, 1, 0.05, 0.0, 0.05, 0.0);
        }

        // Vertical fan
        double fanHeight = 1.5 + clawSize * 0.4;
        for (double h = 0.5; h <= fanHeight; h += 0.4) {
            world.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.9f, 0.0f, 0.2f), 1.0f),
                    pos.x, pos.y + h, pos.z, 1, 0.05, 0.0, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.CRIT,
                    pos.x, pos.y + h, pos.z, 1, 0.04, 0.0, 0.04, 0.0);
        }
    }

    private void checkClawHit(ServerWorld world, Vec3d pos, int clawIndex) {
        double halfWidth = clawSize * 1.0;
        Box hitBox = new Box(
                pos.x - halfWidth, pos.y - 0.5, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + 2.5, pos.z + halfWidth);

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                p -> p.isAlive() && !hitPerClaw.get(clawIndex).contains(p.getUuid()));

        float clawDamage = damage * (1.0f + clawIndex * 0.2f);
        for (PlayerEntity player : players) {
            player.damage(boss.getDamageSources().magic(), clawDamage);
            hitPerClaw.get(clawIndex).add(player.getUuid());
        }
    }

    private void triggerExplosion(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);

        double fs = explosionRadius * 0.4;
        world.spawnParticles(ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 50, fs, explosionRadius * 0.5, fs, 0.15);

        double ds = explosionRadius * 0.3;
        world.spawnParticles(
                new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.2f), 2.0f),
                pos.x, pos.y, pos.z, 30, ds, ds, ds, 0.0);

        double ls = explosionRadius * 0.2;
        world.spawnParticles(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, 15, ls, ls, ls, 0.0);

        // Rising wave of FLAME rings
        for (double h = 0; h <= explosionRadius; h += 0.5) {
            double ringR = explosionRadius * Math.sin(Math.PI * h / explosionRadius);
            int N = Math.max(4, (int)(ringR * 4));
            for (int i = 0; i < N; i++) {
                double angle = Math.toRadians(360.0 / N * i);
                double rx = pos.x + Math.cos(angle) * ringR;
                double rz = pos.z + Math.sin(angle) * ringR;
                world.spawnParticles(ParticleTypes.FLAME,
                        rx, pos.y + h, rz, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.7f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2f, 0.5f);

        // Damage players with falloff
        Box blastBox = new Box(
                pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius);

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, blastBox,
                p -> p.isAlive());

        for (PlayerEntity player : players) {
            double dist = player.getPos().distanceTo(pos);
            if (dist > explosionRadius) continue;

            float falloff = (float)(1.0 - dist / explosionRadius);
            player.damage(boss.getDamageSources().magic(), damage * 1.5f * falloff);

            Vec3d knock = player.getPos().subtract(pos).normalize()
                    .multiply(1.8 * falloff);
            player.addVelocity(knock.x, 0.8 * falloff, knock.z);
            player.velocityModified = true;
        }
    }
}
