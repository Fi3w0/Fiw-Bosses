package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class SlamGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float damage;
    private final float knockback;
    private final int cooldown;
    private final int windupTicks;
    private final float teleportRange;
    private final String taunt;

    private int cooldownTimer;
    private int tick;
    private boolean slamDone;

    private static final int AFTERSHOCK_TICKS = 6;

    public SlamGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius       = params.has("radius")        ? params.get("radius").getAsFloat()        : 5.0f;
        this.damage       = params.has("damage")        ? params.get("damage").getAsFloat()         : 20.0f;
        this.knockback    = params.has("knockback")     ? params.get("knockback").getAsFloat()      : 2.5f;
        this.cooldown     = cooldownTicks;
        this.windupTicks  = params.has("windupTicks")   ? params.get("windupTicks").getAsInt()      : 16;
        this.teleportRange= params.has("teleportRange") ? params.get("teleportRange").getAsFloat()  : 6.0f;
        this.taunt        = params.has("taunt")         ? params.get("taunt").getAsString()         : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return false;
        }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        slamDone = false;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();

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

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 0.4f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < windupTicks + AFTERSHOCK_TICKS;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();
        Vec3 bossPos = boss.position();

        if (tick <= windupTicks) {
            float progress = (float) tick / windupTicks;

            int vortexCount = 3 + (int)(progress * 8);
            for (int i = 0; i < vortexCount; i++) {
                double angle = Math.toRadians(boss.getRandom().nextFloat() * 360);
                double dist = radius * (1.0 - progress * 0.6) + boss.getRandom().nextFloat();
                double px = bossPos.x + Math.cos(angle) * dist;
                double pz = bossPos.z + Math.sin(angle) * dist;
                double py = bossPos.y + 0.1 + boss.getRandom().nextFloat() * 1.5;
                level.sendParticles(ParticleTypes.SOUL, px, py, pz, 1, 0, 0, 0, 0);
            }

            double spiralAngle = Math.toRadians(tick * 30);
            double spiralDist  = radius * (1.0 - progress * 0.7);
            for (int arm = 0; arm < 3; arm++) {
                double armAngle = spiralAngle + Math.toRadians(arm * 120);
                double px = bossPos.x + Math.cos(armAngle) * spiralDist;
                double pz = bossPos.z + Math.sin(armAngle) * spiralDist;
                double py = bossPos.y + 0.5 + progress * 0.8;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, px, py, pz, 1, 0.05, 0.05, 0.05, 0.01);
            }

            int ringPoints = 24;
            double warningR = radius * 0.2 + radius * 0.8 * progress;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.toRadians((360.0 / ringPoints) * i);
                double px = bossPos.x + Math.cos(angle) * warningR;
                double pz = bossPos.z + Math.sin(angle) * warningR;
                level.sendParticles(ParticleTypes.SMOKE,
                        px, bossPos.y + 0.05, pz, 1, 0, 0, 0, 0);
            }

            if (tick == windupTicks / 2) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 1.2f, 0.6f);
            }
        }

        if (tick == windupTicks && !slamDone) {
            slamDone = true;
            performSlam(level, bossPos);
        }

        if (tick > windupTicks) {
            int afterTick = tick - windupTicks;
            double ringProgress = (double) afterTick / AFTERSHOCK_TICKS;

            for (int ring = 0; ring < 2; ring++) {
                double ringR = radius * (0.3 + ring * 0.4 + ringProgress * (0.7 - ring * 0.2));
                int count = 20 + ring * 8;
                for (int i = 0; i < count; i++) {
                    double angle = Math.toRadians((360.0 / count) * i);
                    double px = bossPos.x + Math.cos(angle) * ringR;
                    double pz = bossPos.z + Math.sin(angle) * ringR;
                    level.sendParticles(ring == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                            px, bossPos.y + 0.1, pz, 1, 0, 0.05, 0, 0);
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void performSlam(ServerLevel level, Vec3 bossPos) {
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            double dist = boss.distanceTo(target);
            if (dist > teleportRange) {
                Vec3 dir = target.position().subtract(bossPos).normalize();
                double tx = target.getX() - dir.x * 1.8;
                double tz = target.getZ() - dir.z * 1.8;
                boss.teleportTo(tx, target.getY(), tz);
                bossPos = boss.position();

                level.sendParticles(ParticleTypes.POOF, bossPos.x, bossPos.y + 1, bossPos.z,
                        12, 0.4, 0.5, 0.4, 0.05);
                level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
            }
        }

        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0f, 0.3f);
        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.7f);

        Vec3 center = bossPos;

        AABB aoeBox = boss.getBoundingBox().inflate(radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aoeBox,
                e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

        for (LivingEntity entity : entities) {
            double dist = entity.distanceTo(boss);
            if (dist <= radius) {
                float falloff = 1.0f - (float)(dist / radius) * 0.4f;
                entity.hurt(boss.damageSources().mobAttack(boss), damage * falloff);

                Vec3 dir = entity.position().subtract(center).normalize();
                double yLaunch = 0.5 + (1.0 - dist / radius) * 0.6;
                entity.push(dir.x * knockback, yLaunch, dir.z * knockback);
                entity.hurtMarked = true;
            }
        }

        for (int ring = 0; ring < 3; ring++) {
            double r = radius * (0.25 + ring * 0.3);
            int count = 12 + ring * 8;
            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians((360.0 / count) * i);
                double px = center.x + Math.cos(angle) * r;
                double pz = center.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.EXPLOSION, px, center.y + 0.05, pz, 1, 0, 0, 0, 0);
            }
        }

        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                center.x, center.y, center.z, 8, radius * 0.3, 0.1, radius * 0.3, 0.08);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.2, center.z, 16, radius * 0.35, 0.4, radius * 0.35, 0.12);
        level.sendParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.1, center.z, 6, radius * 0.2, 0.0, radius * 0.2, 0.0);
    }
}
