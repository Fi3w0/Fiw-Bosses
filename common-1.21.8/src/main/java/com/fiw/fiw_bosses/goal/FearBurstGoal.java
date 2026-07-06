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

public class FearBurstGoal extends Goal {

    private final BossEntity boss;
    private final double radius;
    private final float damage;
    private final double knockback;
    private final int windupTicks;
    private final int darknessDuration;
    private final int weaknessDuration;
    private final int slownessDuration;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private boolean burst;

    public FearBurstGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsDouble() : 10.0;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 4.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsDouble() : 1.6;
        this.windupTicks = params.has("windupTicks") ? params.get("windupTicks").getAsInt() : 24;
        this.darknessDuration = params.has("darknessDuration") ? params.get("darknessDuration").getAsInt() : 100;
        this.weaknessDuration = params.has("weaknessDuration") ? params.get("weaknessDuration").getAsInt() : 120;
        this.slownessDuration = params.has("slownessDuration") ? params.get("slownessDuration").getAsInt() : 60;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= radius + 4.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        burst = false;
        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.8f, 0.45f);
            sendTaunt(level);
        }
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (tick <= windupTicks) {
            spawnWindup(level);
            return;
        }

        if (!burst) {
            burst = true;
            doBurst(level);
        }

        if (tick > windupTicks + 8) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
    }

    private void spawnWindup(ServerLevel level) {
        double progress = (double) tick / Math.max(1, windupTicks);
        int points = 18;
        double ring = radius * progress;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + tick * 0.18;
            double px = boss.getX() + Math.cos(angle) * ring;
            double pz = boss.getZ() + Math.sin(angle) * ring;
            level.sendParticles(ParticleTypes.SOUL, px, boss.getY() + 0.1, pz, 1, 0.05, 0.05, 0.05, 0.02);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, boss.getY() + 0.2, pz, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.SOUL,
                boss.getX(), boss.getY() + 1.2, boss.getZ(), 3, 0.4, 0.5, 0.4, 0.04);
    }

    private void doBurst(ServerLevel level) {
        Vec3 center = boss.position().add(0, 1.0, 0);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.8f, 0.65f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL,
                center.x, center.y, center.z, 80, radius * 0.35, 0.8, radius * 0.35, 0.25);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                center.x, center.y, center.z, 35, radius * 0.2, 0.5, radius * 0.2, 0.05);

        AABB area = boss.getBoundingBox().inflate(radius);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> boss.canAbilityHit(e) && boss.distanceToSqr(e) <= radius * radius);

        for (LivingEntity victim : victims) {
            if (damage > 0) {
                victim.hurtServer(level, boss.damageSources().magic(), damage);
            }
            if (darknessDuration > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darknessDuration, 0, false, false));
            }
            if (weaknessDuration > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessDuration, 0, false, true));
            }
            if (slownessDuration > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slownessDuration, 1, false, true));
            }
            Vec3 away = victim.position().subtract(boss.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1.0e-6) away = boss.getLookAngle();
            away = away.normalize();
            victim.push(away.x * knockback, 0.25, away.z * knockback);
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
