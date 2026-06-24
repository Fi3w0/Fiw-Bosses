package com.fiw.fiw_bosses.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SmartMovementController {

    private final BossEntity boss;
    private Vec3 guardAnchor;
    private int strafeTimer;
    private int strafeDir = 1;
    private int retreatTimer;
    private int phaseWalkCooldown;
    private int decisionTimer;
    private boolean hovering;

    public SmartMovementController(BossEntity boss) {
        this.boss = boss;
    }

    public void reset() {
        guardAnchor = null;
        strafeTimer = 0;
        strafeDir = 1;
        retreatTimer = 0;
        phaseWalkCooldown = 30 + boss.getRandom().nextInt(50);
        decisionTimer = 0;
        if (hovering) {
            boss.setNoGravity(false);
            hovering = false;
        }
    }

    public void tick() {
        String mode = normalize(boss.getMovementMode());
        if ("static".equals(mode) || "follow_boss".equals(mode)) {
            stopHovering();
            return;
        }

        if (boss.getBossState() != BossEntity.BossState.ACTIVE) {
            stopHovering();
            return;
        }

        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) {
            stopHovering();
            retreatTimer = 0;
            strafeTimer = 0;
            return;
        }

        if (isAbilityHoldingMove()) {
            strafeTimer = 0;
            retreatTimer = 0;
            return;
        }

        if (guardAnchor == null) {
            guardAnchor = boss.position();
        }

        switch (mode) {
            case "vanilla" -> {
                stopHovering();
            }
            case "hit_and_run" -> {
                stopHovering();
                tickHitAndRun(target);
            }
            case "guard_point" -> {
                stopHovering();
                tickGuardPoint(target);
            }
            case "phase_walk" -> {
                stopHovering();
                tickPhaseWalk(target);
            }
            case "hover" -> tickHover(target);
            case "sniper" -> {
                stopHovering();
                tickSniper(target);
            }
            case "berserk" -> {
                stopHovering();
                tickBerserk(target);
            }
            default -> {
                stopHovering();
                tickSideStrafe(target);
            }
        }
    }

    private String normalize(String mode) {
        if (mode == null || mode.isBlank()) return "side";
        String value = mode.toLowerCase(java.util.Locale.ROOT);
        if ("normal".equals(value)) return "side";
        return value;
    }

    private boolean isAbilityHoldingMove() {
        return boss.getGoalSelector().getAvailableGoals().stream().anyMatch(pg ->
                pg.isRunning()
                        && pg.getGoal().getFlags().contains(Goal.Flag.MOVE)
                        && !(pg.getGoal() instanceof MeleeAttackGoal)
                        && !(pg.getGoal() instanceof WaterAvoidingRandomStrollGoal));
    }

    private void tickSideStrafe(LivingEntity target) {
        double dist = boss.distanceTo(target);
        if (dist < 7.0 && dist > 2.0) {
            strafeTimer++;
            if (strafeTimer % 30 == 0 && boss.getRandom().nextFloat() < 0.5f) strafeDir *= -1;
            if (strafeTimer % 2 == 0) {
                boss.getMoveControl().strafe(-0.3f, strafeDir * 0.6f);
                boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
        } else {
            strafeTimer = 0;
        }
    }

    private void tickHitAndRun(LivingEntity target) {
        double dist = boss.distanceTo(target);
        if (retreatTimer > 0) {
            retreatTimer--;
            moveAwayFrom(target, 8.0, 1.3);
            return;
        }

        if (dist <= 3.4) {
            retreatTimer = 34 + boss.getRandom().nextInt(18);
            moveAwayFrom(target, 8.0, 1.3);
        } else if (dist > 5.5) {
            boss.getNavigation().moveTo(target, 1.25);
        } else {
            tickSideStrafe(target);
        }
    }

    private void tickGuardPoint(LivingEntity target) {
        double bossFromAnchor = boss.position().distanceTo(guardAnchor);
        double targetFromAnchor = target.position().distanceTo(guardAnchor);
        if (bossFromAnchor > 22.0 || targetFromAnchor > 20.0) {
            boss.getNavigation().moveTo(guardAnchor.x, guardAnchor.y, guardAnchor.z, 1.15);
            if (bossFromAnchor > 30.0) {
                boss.setTarget(null);
            }
            return;
        }

        double dist = boss.distanceTo(target);
        if (dist > 9.0) {
            boss.getNavigation().moveTo(target, 1.05);
        } else {
            tickSideStrafe(target);
        }
    }

    private void tickPhaseWalk(LivingEntity target) {
        if (phaseWalkCooldown > 0) {
            phaseWalkCooldown--;
            tickSideStrafe(target);
            return;
        }

        double dist = boss.distanceTo(target);
        if (dist < 3.0 || dist > 18.0) {
            phaseWalkCooldown = 30;
            tickSideStrafe(target);
            return;
        }

        if (boss.level() instanceof ServerLevel level) {
            Vec3 safe = findPhaseWalkSpot(level, target);
            if (safe != null) {
                level.sendParticles(ParticleTypes.PORTAL,
                        boss.getX(), boss.getY() + 1.0, boss.getZ(),
                        30, 0.35, 0.6, 0.35, 0.5);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.9f);
                boss.teleportTo(safe.x, safe.y, safe.z);
                boss.getLookControl().setLookAt(target, 360, 90);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        safe.x, safe.y + 1.0, safe.z,
                        22, 0.3, 0.5, 0.3, 0.08);
            }
        }
        phaseWalkCooldown = 85 + boss.getRandom().nextInt(65);
    }

    private void tickHover(LivingEntity target) {
        if (!hovering) {
            boss.setNoGravity(true);
            hovering = true;
        }

        Vec3 toTarget = target.position().subtract(boss.position());
        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        double dist = Math.max(0.001, flat.length());
        Vec3 dir = flat.normalize();
        Vec3 side = new Vec3(-dir.z * strafeDir, 0, dir.x * strafeDir);
        if (++strafeTimer % 60 == 0 && boss.getRandom().nextFloat() < 0.65f) strafeDir *= -1;

        double forward = dist > 8.0 ? 0.08 : (dist < 4.5 ? -0.08 : 0.0);
        double desiredY = Math.max(target.getY() + 2.2, guardAnchor != null ? guardAnchor.y + 1.5 : boss.getY() + 1.5);
        double yDelta = clamp((desiredY - boss.getY()) * 0.08, -0.08, 0.12);
        Vec3 delta = boss.getDeltaMovement()
                .scale(0.72)
                .add(dir.scale(forward))
                .add(side.scale(0.045))
                .add(0, yDelta, 0);
        Vec3 next = boss.position().add(delta);
        if (boss.level().noCollision(boss, boss.getBoundingBox().move(next.subtract(boss.position())))) {
            boss.setDeltaMovement(delta);
        } else {
            boss.setDeltaMovement(0, Math.max(0.02, yDelta), 0);
        }
        boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
        boss.hurtMarked = true;

        if (boss.level() instanceof ServerLevel level && boss.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.15, boss.getZ(),
                    2, 0.25, 0.03, 0.25, 0.01);
        }
    }

    private void tickSniper(LivingEntity target) {
        double dist = boss.distanceTo(target);
        if (dist < 10.0) {
            moveAwayFrom(target, 14.0, 1.2);
        } else if (dist > 22.0) {
            boss.getNavigation().moveTo(target, 1.0);
        } else {
            boss.getNavigation().stop();
            if (++strafeTimer % 45 == 0 && boss.getRandom().nextFloat() < 0.55f) strafeDir *= -1;
            boss.getMoveControl().strafe(0.0f, strafeDir * 0.65f);
            boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
    }

    private void tickBerserk(LivingEntity target) {
        if (boss.getHealth() <= boss.getMaxHealth() * 0.35f) {
            boss.getNavigation().moveTo(target, 1.45);
            boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
        } else {
            tickSideStrafe(target);
        }
    }

    private void moveAwayFrom(LivingEntity target, double distance, double speed) {
        if (--decisionTimer > 0) return;
        decisionTimer = 8;
        Vec3 away = boss.position().subtract(target.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0e-6) away = boss.getLookAngle();
        away = new Vec3(away.x, 0, away.z).normalize();
        Vec3 desired = boss.position().add(away.scale(distance));
        Vec3 safe = boss.level() instanceof ServerLevel level ? findStandableY(level, desired, boss.getY()) : null;
        if (safe != null) {
            boss.getNavigation().moveTo(safe.x, safe.y, safe.z, speed);
        } else {
            boss.getMoveControl().strafe(-0.8f, 0.0f);
        }
        boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
    }

    private Vec3 findPhaseWalkSpot(ServerLevel level, LivingEntity target) {
        double baseAngle = Math.atan2(boss.getZ() - target.getZ(), boss.getX() - target.getX());
        for (int i = 0; i < 18; i++) {
            double offset = (i == 0 ? Math.PI : (boss.getRandom().nextDouble() - 0.5) * Math.PI * 1.6);
            double angle = baseAngle + offset;
            double radius = 3.0 + boss.getRandom().nextDouble() * 3.5;
            Vec3 pos = new Vec3(
                    target.getX() + Math.cos(angle) * radius,
                    target.getY(),
                    target.getZ() + Math.sin(angle) * radius);
            Vec3 safe = findStandableY(level, pos, target.getY());
            if (safe != null && safe.distanceToSqr(target.position()) >= 4.0) return safe;
        }
        return null;
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

    private void stopHovering() {
        if (hovering) {
            boss.setNoGravity(false);
            hovering = false;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
