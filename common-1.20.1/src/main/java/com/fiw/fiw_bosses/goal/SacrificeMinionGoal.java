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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class SacrificeMinionGoal extends Goal {

    private final BossEntity boss;
    private final double searchRadius;
    private final int count;
    private final float healAmount;
    private final float damage;
    private final double explosionRadius;
    private final float belowPercent;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;

    public SacrificeMinionGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.searchRadius = params.has("searchRadius") ? params.get("searchRadius").getAsDouble() : 32.0;
        this.count = params.has("count") ? params.get("count").getAsInt() : 1;
        this.healAmount = params.has("healAmount") ? params.get("healAmount").getAsFloat() : 30.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 8.0f;
        this.explosionRadius = params.has("explosionRadius") ? params.get("explosionRadius").getAsDouble() : 4.0;
        this.belowPercent = params.has("belowPercent") ? params.get("belowPercent").getAsFloat() : 1.0f;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        if (boss.getHealth() / boss.getMaxHealth() > belowPercent) return false;
        if (!(boss.level() instanceof ServerLevel level)) return false;
        return !findMinions(level).isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();
        List<LivingEntity> minions = findMinions(level);
        if (minions.isEmpty()) return;

        sendTaunt(level);
        int sacrificed = 0;
        for (LivingEntity minion : minions) {
            if (sacrificed >= Math.max(1, count)) break;
            sacrifice(level, minion);
            sacrificed++;
        }

        if (sacrificed > 0) {
            boss.setHealth(Math.min(boss.getMaxHealth(), boss.getHealth() + healAmount * sacrificed));
            level.sendParticles(ParticleTypes.HEART,
                    boss.getX(), boss.getY() + 1.4, boss.getZ(), 4 * sacrificed, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.5f, 0.7f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private List<LivingEntity> findMinions(ServerLevel level) {
        List<LivingEntity> minions = new ArrayList<>();
        for (UUID uuid : boss.getMinionUuids()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living && living.isAlive()
                    && living.distanceToSqr(boss) <= searchRadius * searchRadius) {
                minions.add(living);
            }
        }
        minions.sort(Comparator.comparingDouble(e -> e.distanceToSqr(boss)));
        return minions;
    }

    private void sacrifice(ServerLevel level, LivingEntity minion) {
        Vec3 pos = minion.position().add(0, minion.getBbHeight() * 0.5, 0);
        Vec3 bossCenter = boss.position().add(0, 1.2, 0);
        Vec3 delta = bossCenter.subtract(pos);
        int steps = Math.max(4, (int) Math.ceil(delta.length() * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = pos.add(delta.scale(t));
            level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.02);
        }

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.x, pos.y, pos.z, 24, 0.4, 0.4, 0.4, 0.08);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y, pos.z, 18, 0.3, 0.3, 0.3, 0.08);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.4f, 0.7f);

        if (damage > 0 && explosionRadius > 0) {
            AABB area = minion.getBoundingBox().inflate(explosionRadius);
            List<Player> victims = level.getEntitiesOfClass(Player.class, area,
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                            && p.distanceToSqr(minion) <= explosionRadius * explosionRadius);
            for (Player player : victims) {
                player.hurt(boss.damageSources().magic(), damage);
                Vec3 away = player.position().subtract(minion.position());
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() > 1.0e-6) {
                    away = away.normalize();
                    player.push(away.x * 0.8, 0.25, away.z * 0.8);
                    player.hurtMarked = true;
                }
            }
        }

        boss.getMinionUuids().remove(minion.getUUID());
        minion.discard();
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
