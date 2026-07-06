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

public class AoeSmashAttackGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float damage;
    private final float knockback;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int windupTick;
    private static final int WINDUP_DURATION = 20;

    public AoeSmashAttackGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 5.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 15.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsFloat() : 2.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) <= (radius + 2) * (radius + 2);
    }

    @Override
    public void start() {
        windupTick = 0;
        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 0.5f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return windupTick < WINDUP_DURATION + 5;
    }

    @Override
    public void tick() {
        windupTick++;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();

            if (windupTick <= WINDUP_DURATION) {
                float progress = (float) windupTick / WINDUP_DURATION;
                int particleCount = (int) (4 + progress * 10);
                double ringRadius = radius * progress * 0.8;

                for (int i = 0; i < particleCount; i++) {
                    double angle = Math.toRadians((360.0 / particleCount) * i + windupTick * 12);
                    double px = boss.getX() + Math.cos(angle) * ringRadius;
                    double pz = boss.getZ() + Math.sin(angle) * ringRadius;
                    level.sendParticles(ParticleTypes.CLOUD,
                            px, boss.getY() + 0.1, pz, 1, 0, 0.1, 0, 0.01);
                }

                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        boss.getX(), boss.getY() + 0.5 + progress, boss.getZ(),
                        2, 0.15, 0.1, 0.15, 0.02);
            }
        }

        if (windupTick == WINDUP_DURATION) {
            performSmash();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void performSmash() {
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();
        Vec3 center = boss.position();

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.6f);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0f, 0.4f);

        AABB aoeBox = boss.getBoundingBox().inflate(radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aoeBox,
                e -> boss.canAbilityHit(e));

        int hitCount = 0;
        for (LivingEntity entity : entities) {
            double dist = entity.distanceTo(boss);
            if (dist <= radius) {
                float finalDamage = damage * (1.0f - (float) (dist / radius) * 0.3f);
                entity.hurt(boss.damageSources().mobAttack(boss), finalDamage);

                Vec3 dir = entity.position().subtract(center).normalize();
                double yLaunch = 0.4 + (1.0 - dist / radius) * 0.4;
                entity.push(dir.x * knockback, yLaunch, dir.z * knockback);
                entity.hurtMarked = true;
                hitCount++;
            }
        }

        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = radius * (0.3 + ring * 0.3);
            int count = 12 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians((360.0 / count) * i);
                double px = center.x + Math.cos(angle) * ringRadius;
                double pz = center.z + Math.sin(angle) * ringRadius;
                level.sendParticles(ParticleTypes.EXPLOSION, px, center.y + 0.1, pz, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y, center.z, 20, radius * 0.4, 0.3, radius * 0.4, 0.05);
        level.sendParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.1, center.z, 8, radius * 0.3, 0.0, radius * 0.3, 0.0);

        if (hitCount > 0 && taunt != null && boss.getRandom().nextFloat() < 0.4f) {
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
