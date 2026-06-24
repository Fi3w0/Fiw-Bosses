package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
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

public class BeamGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final float width;
    private final int duration;
    private final int cooldown;
    private final String taunt;
    private final SimpleParticleType outerParticle;
    private final SimpleParticleType coreParticle;
    private final double maxLength;
    private int cooldownTimer;
    private int beamTick;
    private Vec3 lockedDest;
    private static final int WINDUP_TICKS = 20;

    public BeamGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 3.0f;
        this.width = params.has("width") ? params.get("width").getAsFloat() : 0.9f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 50;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.outerParticle = ParticleTornadoGoal.particleOr(params, "particle", ParticleTypes.ELECTRIC_SPARK);
        this.coreParticle  = ParticleTornadoGoal.particleOr(params, "coreParticle", ParticleTypes.END_ROD);
        // Default keeps current behavior (target is gated to <=35 in canUse).
        this.maxLength     = params.has("maxLength") ? params.get("maxLength").getAsDouble() : 40.0;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) >= 3 * 3
                && boss.distanceToSqr(target) <= 35 * 35;
    }

    @Override
    public void start() {
        beamTick = -WINDUP_TICKS;
        lockedDest = null;

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 1.0f);

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
        return beamTick < duration;
    }

    @Override
    public void tick() {
        beamTick++;

        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        LivingEntity target = boss.getTarget();
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();
        Vec3 origin = new Vec3(boss.getX(), boss.getEyeY() - 0.2, boss.getZ());

        if (beamTick <= 0) {
            if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

            float progress = (float)(WINDUP_TICKS + beamTick) / WINDUP_TICKS;
            double gatherRadius = 2.5 * (1.0 - progress);
            int gatherCount = 6;
            for (int i = 0; i < gatherCount; i++) {
                double angle = Math.toRadians((360.0 / gatherCount) * i + beamTick * 15);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        origin.x + Math.cos(angle) * gatherRadius,
                        origin.y + Math.sin(angle) * gatherRadius * 0.4,
                        origin.z + Math.sin(angle) * gatherRadius,
                        1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD,
                    origin.x, origin.y, origin.z,
                    2 + (int)(progress * 4), 0.08, 0.08, 0.08, 0.0);

            if (beamTick == 0 && target != null) {
                lockedDest = new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ());
                boss.getLookControl().setLookAt(lockedDest.x, lockedDest.y, lockedDest.z, 360, 90);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.8f, 0.6f);
            }
            return;
        }

        if (lockedDest == null) return;

        Vec3 dir = lockedDest.subtract(origin).normalize();
        double beamLen = Math.min(origin.distanceTo(lockedDest), maxLength);
        Vec3 dest = origin.add(dir.scale(beamLen));

        int outerSteps = (int) Math.ceil(beamLen * 5);
        for (int s = 0; s <= outerSteps; s++) {
            double t = (double) s / outerSteps;
            double px = origin.x + dir.x * beamLen * t;
            double py = origin.y + dir.y * beamLen * t;
            double pz = origin.z + dir.z * beamLen * t;
            level.sendParticles(outerParticle, px, py, pz, 2, 0.1, 0.1, 0.1, 0.0);
        }

        int coreSteps = (int) Math.ceil(beamLen * 10);
        for (int s = 0; s <= coreSteps; s++) {
            double t = (double) s / coreSteps;
            double px = origin.x + dir.x * beamLen * t;
            double py = origin.y + dir.y * beamLen * t;
            double pz = origin.z + dir.z * beamLen * t;
            level.sendParticles(coreParticle, px, py, pz, 1, 0, 0, 0, 0);
        }

        level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                dest.x, dest.y, dest.z, 6, 0.25, 0.25, 0.25, 0.1);
        level.sendParticles(ParticleTypes.END_ROD,
                dest.x, dest.y, dest.z, 2, 0.15, 0.15, 0.15, 0.05);

        AABB broad = new AABB(
                Math.min(origin.x, dest.x) - width, Math.min(origin.y, dest.y) - 1.5,
                Math.min(origin.z, dest.z) - width,
                Math.max(origin.x, dest.x) + width, Math.max(origin.y, dest.y) + 1.5,
                Math.max(origin.z, dest.z) + width
        );
        List<Player> victims = level.getEntitiesOfClass(Player.class, broad,
                p -> p.isAlive() && isOnBeam(
                        new Vec3(p.getX(), p.getY() + p.getBbHeight() / 2.0, p.getZ()),
                        origin, dir, beamLen));

        for (Player victim : victims) {
            victim.hurt(boss.damageSources().magic(), damage);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    4, 0.2, 0.2, 0.2, 0.05);
        }

        if (beamTick % 8 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 0.6f, 1.3f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    boss.getX(), boss.getEyeY() - 0.2, boss.getZ(),
                    20, 0.4, 0.4, 0.4, 0.15);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    private boolean isOnBeam(Vec3 point, Vec3 origin, Vec3 dir, double len) {
        Vec3 toPoint = point.subtract(origin);
        double proj = toPoint.dot(dir);
        if (proj < 0 || proj > len) return false;
        Vec3 closest = origin.add(dir.scale(proj));
        return closest.distanceToSqr(point) <= width * width;
    }
}
