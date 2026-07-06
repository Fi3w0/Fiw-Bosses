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

public class MeleeSlashAttackGoal extends Goal {

    private final BossEntity boss;
    private final float range;
    private final float arc;
    private final float damage;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int attackTick;

    public MeleeSlashAttackGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.range = params.has("range") ? params.get("range").getAsFloat() : 4.0f;
        this.arc = params.has("arc") ? params.get("arc").getAsFloat() : 90.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 10.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) <= range * range;
    }

    @Override
    public void start() {
        attackTick = 0;
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 360, 90);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return attackTick < 12;
    }

    @Override
    public void tick() {
        attackTick++;

        if (attackTick <= 4 && !boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    boss.getX(), boss.getY() + 1.2, boss.getZ(),
                    2, 0.3, 0.2, 0.3, 0.1);
        }

        if (attackTick == 5) {
            performSlash();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void performSlash() {
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();
        Vec3 bossPos = boss.position();
        Vec3 lookDir = boss.getViewVector(1.0f).normalize();
        float halfArc = arc / 2.0f;

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.7f);

        AABB searchBox = boss.getBoundingBox().inflate(range);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> boss.canAbilityHit(e));

        int hitCount = 0;
        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity.position().subtract(bossPos).normalize();
            double dot = lookDir.dot(toEntity);
            double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

            if (angle <= halfArc && boss.distanceToSqr(entity) <= range * range) {
                entity.hurt(boss.damageSources().mobAttack(boss), damage);
                Vec3 knockback = toEntity.scale(0.7);
                entity.push(knockback.x, 0.25, knockback.z);
                entity.hurtMarked = true;
                hitCount++;

                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        3, 0.2, 0.2, 0.2, 0.1);
            }
        }

        for (int i = 0; i < 15; i++) {
            double sweepAngle = Math.toRadians(boss.getYRot() - halfArc + (arc / 15.0) * i);
            double dist = range * (0.5 + (i % 3) * 0.15);
            double px = bossPos.x + Math.sin(-sweepAngle) * dist;
            double pz = bossPos.z + Math.cos(sweepAngle) * dist;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, bossPos.y + 1.0, pz, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.CRIT,
                bossPos.x + lookDir.x * range * 0.5, bossPos.y + 1.0, bossPos.z + lookDir.z * range * 0.5,
                8, range * 0.3, 0.3, range * 0.3, 0.2);

        if (hitCount > 0 && taunt != null && boss.getRandom().nextFloat() < 0.3f) {
            sendBossTaunt(level, taunt);
        }
    }

    private void sendBossTaunt(ServerLevel level, String message) {
        var bossName = boss.getCustomName();
        Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(message));
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= 48 * 48) {
                player.sendSystemMessage(tauntText);
            }
        }
    }
}
