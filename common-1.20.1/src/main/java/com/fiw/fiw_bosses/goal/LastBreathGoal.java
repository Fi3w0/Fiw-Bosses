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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class LastBreathGoal extends Goal {

    private final BossEntity boss;
    private final double belowPercent;
    private final int channelTicks;
    private final double radius;
    private final float damage;
    private final double knockback;
    private final float interruptDamage;
    private final int witherDuration;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private boolean released;
    private float startHealth;

    public LastBreathGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.belowPercent = params.has("belowPercent") ? params.get("belowPercent").getAsDouble() : 0.25;
        this.channelTicks = params.has("channelTicks") ? params.get("channelTicks").getAsInt() : 80;
        this.radius = params.has("radius") ? params.get("radius").getAsDouble() : 18.0;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 24.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsDouble() : 2.2;
        this.interruptDamage = params.has("interruptDamage") ? params.get("interruptDamage").getAsFloat() : 14.0f;
        this.witherDuration = params.has("witherDuration") ? params.get("witherDuration").getAsInt() : 120;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.getHealth() <= boss.getMaxHealth() * belowPercent
                && boss.distanceTo(target) <= radius + 8.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        released = false;
        startHealth = boss.getHealth();
        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.65f);
            sendTaunt(level);
        }
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (interruptDamage > 0 && startHealth - boss.getHealth() >= interruptDamage) {
            interrupt(level);
            active = false;
            return;
        }

        if (tick <= channelTicks) {
            spawnChannel(level);
            if (tick % 20 == 0) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.55f + tick / (float) channelTicks * 0.35f);
            }
            return;
        }

        if (!released) {
            released = true;
            release(level);
        }

        if (tick > channelTicks + 10) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
    }

    private void spawnChannel(ServerLevel level) {
        double progress = (double) tick / Math.max(1, channelTicks);
        double ring = radius * progress;
        int points = 28;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + tick * 0.08;
            double px = boss.getX() + Math.cos(angle) * ring;
            double pz = boss.getZ() + Math.sin(angle) * ring;
            level.sendParticles(ParticleTypes.SOUL,
                    px, boss.getY() + 0.08, pz, 1, 0.05, 0.02, 0.05, 0.01);
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.SMOKE,
                        px, boss.getY() + 0.12, pz, 1, 0.04, 0.02, 0.04, 0.02);
            }
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                boss.getX(), boss.getY() + boss.getBbHeight() * 0.65, boss.getZ(),
                6, 0.35, 0.5, 0.35, 0.04);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                boss.getX(), boss.getY() + 0.2, boss.getZ(), 3, 0.25, 0.15, 0.25, 0.02);
    }

    private void interrupt(ServerLevel level) {
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 1.6f, 0.55f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                45, 0.8, 0.8, 0.8, 0.08);
    }

    private void release(ServerLevel level) {
        Vec3 center = boss.position().add(0, boss.getBbHeight() * 0.5, 0);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.8f, 0.65f);
        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y, center.z, 6, 0.8, 0.4, 0.8, 0.0);
        level.sendParticles(ParticleTypes.SOUL,
                center.x, center.y, center.z, 120, radius * 0.35, 0.8, radius * 0.35, 0.25);

        AABB area = boss.getBoundingBox().inflate(radius);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != boss && e.isAlive() && !boss.isMinion(e) && boss.distanceToSqr(e) <= radius * radius);

        for (LivingEntity victim : victims) {
            if (damage > 0) {
                victim.hurt(boss.damageSources().magic(), damage);
            }
            if (witherDuration > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, witherDuration, 1, false, true));
            }
            Vec3 away = victim.position().subtract(boss.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1.0e-6) away = boss.getLookAngle();
            away = away.normalize();
            victim.push(away.x * knockback, 0.45, away.z * knockback);
            victim.hurtMarked = true;
        }
    }

    private void sendTaunt(ServerLevel level) {
        if (taunt == null) return;
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
