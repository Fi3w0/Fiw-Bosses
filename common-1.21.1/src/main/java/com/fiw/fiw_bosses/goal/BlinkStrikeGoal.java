package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class BlinkStrikeGoal extends Goal {

    private final BossEntity boss;
    private final double maxRange;
    private final double backstepDistance;
    private final double strikeRadius;
    private final float damage;
    private final double knockback;
    private final int windupTicks;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private boolean struck;
    private Vec3 targetPos;

    public BlinkStrikeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.maxRange = params.has("maxRange") ? params.get("maxRange").getAsDouble() : 18.0;
        this.backstepDistance = params.has("backstepDistance") ? params.get("backstepDistance").getAsDouble() : 2.2;
        this.strikeRadius = params.has("strikeRadius") ? params.get("strikeRadius").getAsDouble() : 2.8;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 10.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsDouble() : 0.9;
        this.windupTicks = params.has("windupTicks") ? params.get("windupTicks").getAsInt() : 16;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= maxRange;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        struck = false;
        LivingEntity target = boss.getTarget();
        targetPos = target != null ? target.position() : boss.position().add(boss.getLookAngle().scale(3.0));

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 0.65f);
        }
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            targetPos = target.position();
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (tick <= windupTicks) {
            spawnWarning(level);
            return;
        }

        if (!struck) {
            struck = true;
            strike(level, target);
        }

        if (tick >= windupTicks + 8) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
    }

    private void spawnWarning(ServerLevel level) {
        double progress = (double) tick / Math.max(1, windupTicks);
        double ring = 0.7 + strikeRadius * progress;
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + tick * 0.25;
            double px = targetPos.x + Math.cos(angle) * ring;
            double pz = targetPos.z + Math.sin(angle) * ring;
            level.sendParticles(ParticleTypes.PORTAL, px, targetPos.y + 0.2, pz, 1, 0.05, 0.05, 0.05, 0.02);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.CRIT, px, targetPos.y + 0.15, pz, 1, 0.04, 0.02, 0.04, 0.08);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 3, 0.25, 0.4, 0.25, 0.02);
        if (tick == windupTicks - 4) {
            level.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    private void strike(ServerLevel level, LivingEntity target) {
        Vec3 landing = findLanding(level, target);
        level.sendParticles(ParticleTypes.POOF, boss.getX(), boss.getY() + 1.0, boss.getZ(), 18, 0.4, 0.5, 0.4, 0.04);
        boss.teleportTo(landing.x, landing.y, landing.z);
        level.sendParticles(ParticleTypes.POOF, landing.x, landing.y + 1.0, landing.z, 18, 0.4, 0.5, 0.4, 0.04);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, targetPos.x, targetPos.y + 1.0, targetPos.z, 2, 0.4, 0.2, 0.4, 0.0);
        level.playSound(null, landing.x, landing.y, landing.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.8f, 0.65f);

        AABB hitBox = new AABB(
                targetPos.x - strikeRadius, targetPos.y - 0.5, targetPos.z - strikeRadius,
                targetPos.x + strikeRadius, targetPos.y + 2.5, targetPos.z + strikeRadius);
        List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.position().distanceToSqr(targetPos) <= strikeRadius * strikeRadius);

        for (Player player : victims) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
            Vec3 away = player.position().subtract(landing);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1.0e-6) away = boss.getLookAngle();
            away = away.normalize();
            player.push(away.x * knockback, 0.35, away.z * knockback);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private Vec3 findLanding(ServerLevel level, LivingEntity target) {
        Vec3 base = target != null && target.isAlive() ? target.position() : targetPos;
        Vec3 facing = target != null && target.isAlive() ? target.getLookAngle() : boss.getLookAngle();
        Vec3 dir = new Vec3(facing.x, 0, facing.z);
        if (dir.lengthSqr() < 1.0e-6) dir = boss.position().subtract(base);
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();

        Vec3 preferred = base.subtract(dir.scale(backstepDistance));
        Vec3 safe = findStandableY(level, preferred, base.y);
        if (safe != null) return safe;

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            Vec3 candidate = base.add(Math.cos(angle) * backstepDistance, 0, Math.sin(angle) * backstepDistance);
            safe = findStandableY(level, candidate, base.y);
            if (safe != null) return safe;
        }
        return boss.position();
    }

    private Vec3 findStandableY(ServerLevel level, Vec3 pos, double preferredY) {
        int base = (int) Math.floor(preferredY);
        for (int dy = 0; dy <= 4; dy++) {
            for (int s : (dy == 0 ? new int[]{0} : new int[]{1, -1})) {
                Vec3 candidate = new Vec3(pos.x, base + s * dy, pos.z);
                if (canStandAt(level, candidate)) return candidate;
            }
        }
        return null;
    }

    private boolean canStandAt(ServerLevel level, Vec3 feet) {
        AABB body = boss.getBoundingBox().move(feet.subtract(boss.position()));
        if (!level.noCollision(boss, body)) return false;
        AABB below = new AABB(feet.x - 0.3, feet.y - 0.25, feet.z - 0.3,
                feet.x + 0.3, feet.y, feet.z + 0.3);
        return !level.noCollision(boss, below);
    }
}
