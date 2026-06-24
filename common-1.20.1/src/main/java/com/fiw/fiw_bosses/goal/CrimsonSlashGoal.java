package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private Vec3 clawPos;
    private double clawTraveled;
    private Vec3 clawDir;

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
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= range + 4;
    }

    @Override
    public boolean canContinueToUse() {
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

        LivingEntity target = boss.getTarget();
        if (target != null) {
            Vec3 toTarget = target.position().subtract(boss.position());
            Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
            clawDir = horizontal.lengthSqr() > 0.0001 ? horizontal.normalize() : boss.getLookAngle();
        } else {
            Vec3 fwd = boss.getLookAngle();
            clawDir = new Vec3(fwd.x, 0, fwd.z).normalize();
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

        if (state == STATE_WINDUP && currentClaw == 0 && tick < nextClawTick) {
            double angle = Math.toRadians(tick * 30.0);
            for (int i = 0; i < 3; i++) {
                double a = angle + Math.toRadians(i * 120.0);
                double px = boss.getX() + Math.cos(a) * 1.5;
                double pz = boss.getZ() + Math.sin(a) * 1.5;
                level.sendParticles(
                        new DustParticleOptions(Colors.rgb(0.8f, 0.0f, 0.2f), 1.0f),
                        px, boss.getY() + 1.0, pz, 1, 0.05, 0.05, 0.05, 0.0);
            }
            level.sendParticles(ParticleTypes.CRIT,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0.2, 0.2, 0.2, 0.05);
        }

        if (tick == nextClawTick && currentClaw < clawCount) {
            if (target != null) {
                Vec3 toTarget = target.position().subtract(boss.position());
                Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
                if (horizontal.lengthSqr() > 0.0001) {
                    clawDir = horizontal.normalize();
                }
            }

            clawSize     = 1.0 + currentClaw * 0.5;
            clawPos      = boss.position().add(0, 0.3, 0);
            clawTraveled = 0;
            state        = STATE_SLASHING;

            float pitch = 0.6f + currentClaw * 0.1f;
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, pitch);
        }

        if (state == STATE_SLASHING && currentClaw < clawCount && clawPos != null) {
            int substeps = (int) Math.ceil(clawSpeed);
            double stepSize = clawSpeed / substeps;

            for (int s = 0; s < substeps; s++) {
                clawPos      = clawPos.add(clawDir.scale(stepSize));
                clawTraveled += stepSize;

                spawnClawParticles(level, clawPos, currentClaw);
                checkClawHit(level, clawPos, currentClaw);

                if (clawTraveled >= range) {
                    currentClaw++;
                    if (currentClaw < clawCount) {
                        nextClawTick = tick + delayBetweenClaws;
                        state        = STATE_WINDUP;
                    } else {
                        triggerExplosion(level, clawPos);
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

    private void spawnClawParticles(ServerLevel level, Vec3 pos, int clawIndex) {
        double halfWidth = clawSize * 0.8;
        Vec3 perp = new Vec3(-clawDir.z, 0, clawDir.x);

        for (double w = -halfWidth; w <= halfWidth; w += 0.4) {
            Vec3 p = pos.add(perp.scale(w));
            level.sendParticles(ParticleTypes.CRIT,
                    p.x, p.y + 0.05, p.z, 1, 0.05, 0.0, 0.05, 0.0);
            level.sendParticles(
                    new DustParticleOptions(Colors.rgb(0.8f, 0.0f, 0.3f), 1.2f),
                    p.x, p.y + 0.1, p.z, 1, 0.05, 0.0, 0.05, 0.0);
        }

        double fanHeight = 1.5 + clawSize * 0.4;
        for (double h = 0.5; h <= fanHeight; h += 0.4) {
            level.sendParticles(
                    new DustParticleOptions(Colors.rgb(0.9f, 0.0f, 0.2f), 1.0f),
                    pos.x, pos.y + h, pos.z, 1, 0.05, 0.0, 0.05, 0.0);
            level.sendParticles(ParticleTypes.CRIT,
                    pos.x, pos.y + h, pos.z, 1, 0.04, 0.0, 0.04, 0.0);
        }
    }

    private void checkClawHit(ServerLevel level, Vec3 pos, int clawIndex) {
        double halfWidth = clawSize * 1.0;
        AABB hitBox = new AABB(
                pos.x - halfWidth, pos.y - 0.5, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + 2.5, pos.z + halfWidth);

        List<Player> players = level.getEntitiesOfClass(Player.class, hitBox,
                p -> p.isAlive() && !hitPerClaw.get(clawIndex).contains(p.getUUID()));

        float clawDamage = damage * (1.0f + clawIndex * 0.2f);
        for (Player player : players) {
            player.hurt(boss.damageSources().magic(), clawDamage);
            hitPerClaw.get(clawIndex).add(player.getUUID());
        }
    }

    private void triggerExplosion(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);

        double fs = explosionRadius * 0.4;
        level.sendParticles(ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 50, fs, explosionRadius * 0.5, fs, 0.15);

        double ds = explosionRadius * 0.3;
        level.sendParticles(
                new DustParticleOptions(Colors.rgb(0.7f, 0.0f, 0.2f), 2.0f),
                pos.x, pos.y, pos.z, 30, ds, ds, ds, 0.0);

        double ls = explosionRadius * 0.2;
        level.sendParticles(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, 15, ls, ls, ls, 0.0);

        for (double h = 0; h <= explosionRadius; h += 0.5) {
            double ringR = explosionRadius * Math.sin(Math.PI * h / explosionRadius);
            int N = Math.max(4, (int)(ringR * 4));
            for (int i = 0; i < N; i++) {
                double angle = Math.toRadians(360.0 / N * i);
                double rx = pos.x + Math.cos(angle) * ringR;
                double rz = pos.z + Math.sin(angle) * ringR;
                level.sendParticles(ParticleTypes.FLAME,
                        rx, pos.y + h, rz, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.7f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.5f);

        AABB blastBox = new AABB(
                pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius);

        List<Player> players = level.getEntitiesOfClass(Player.class, blastBox, p -> p.isAlive());

        for (Player player : players) {
            double dist = player.position().distanceTo(pos);
            if (dist > explosionRadius) continue;

            float falloff = (float)(1.0 - dist / explosionRadius);
            player.hurt(boss.damageSources().magic(), damage * 1.5f * falloff);

            Vec3 knock = player.position().subtract(pos).normalize().scale(1.8 * falloff);
            player.push(knock.x, 0.8 * falloff, knock.z);
            player.hurtMarked = true;
        }
    }
}
