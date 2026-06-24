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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChargeGoal extends Goal {

    private final BossEntity boss;
    private final float speed;
    private final float damage;
    private final float distance;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private Vec3 chargeDir;
    private int chargeTick;
    private int maxChargeTicks;
    private final Set<UUID> alreadyHit = new HashSet<>();
    private static final int WINDUP_TICKS = 10;

    public ChargeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.speed = params.has("speed") ? params.get("speed").getAsFloat() : 1.5f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 15.0f;
        this.distance = params.has("distance") ? params.get("distance").getAsFloat() : 10.0f;
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
                && boss.distanceTo(target) >= 5.0f
                && boss.distanceTo(target) <= distance + 5;
    }

    @Override
    public void start() {
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        chargeDir = target.position().subtract(boss.position()).normalize();
        chargeTick = -WINDUP_TICKS;
        maxChargeTicks = (int) (distance / (speed * 0.5f));
        maxChargeTicks = Math.max(5, Math.min(30, maxChargeTicks));
        alreadyHit.clear();

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.8f);

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
        return chargeTick < maxChargeTicks;
    }

    @Override
    public void tick() {
        chargeTick++;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();

            if (chargeTick <= 0) {
                boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
                boss.hurtMarked = true;

                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        boss.getX(), boss.getY() + 2.0, boss.getZ(),
                        2, 0.3, 0.3, 0.3, 0.0);
                level.sendParticles(ParticleTypes.CLOUD,
                        boss.getX() + chargeDir.x * 0.5, boss.getY() + 0.1, boss.getZ() + chargeDir.z * 0.5,
                        3, 0.2, 0.0, 0.2, 0.01);
                return;
            }

            boss.setDeltaMovement(chargeDir.x * speed * 0.5, boss.getDeltaMovement().y, chargeDir.z * speed * 0.5);
            boss.hurtMarked = true;

            AABB hitbox = boss.getBoundingBox().inflate(0.8);
            List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, hitbox,
                    e -> e != boss && e.isAlive() && !(e instanceof BossEntity)
                            && !boss.isMinion(e) && !alreadyHit.contains(e.getUUID()));

            for (LivingEntity entity : hit) {
                entity.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
                Vec3 knockDir = entity.position().subtract(boss.position()).normalize();
                entity.push(knockDir.x * 1.5, 0.6, knockDir.z * 1.5);
                entity.hurtMarked = true;
                alreadyHit.add(entity.getUUID());

                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 0.8f);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        5, 0.3, 0.3, 0.3, 0.1);
            }

            level.sendParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.3, boss.getZ(),
                    3, 0.2, 0.1, 0.2, 0.02);
            level.sendParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.05, boss.getZ(),
                    2, 0.3, 0.0, 0.3, 0.01);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 1.0f, 0.6f);
        }
    }
}
