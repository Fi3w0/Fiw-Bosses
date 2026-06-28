package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class CurseBombGoal extends Goal {

    private final BossEntity boss;
    private final double targetRadius;
    private final double blastRadius;
    private final float damage;
    private final double knockback;
    private final int markTicks;
    private final int maxTargets;
    private final boolean hitMarkedPlayer;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private boolean exploded;
    private final List<Player> marked = new ArrayList<>();

    public CurseBombGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.targetRadius = params.has("targetRadius") ? params.get("targetRadius").getAsDouble() : 24.0;
        this.blastRadius = params.has("blastRadius") ? params.get("blastRadius").getAsDouble() : 4.0;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 9.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsDouble() : 0.8;
        this.markTicks = params.has("markTicks") ? params.get("markTicks").getAsInt() : 60;
        this.maxTargets = params.has("maxTargets") ? params.get("maxTargets").getAsInt() : 2;
        this.hitMarkedPlayer = !params.has("hitMarkedPlayer") || params.get("hitMarkedPlayer").getAsBoolean();
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null && boss.getTarget().isAlive() && !findTargets().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        exploded = false;
        marked.clear();
        marked.addAll(findTargets());

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            for (Player player : marked) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, markTicks + 20, 0, false, true));
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.7f, 1.25f);
            }
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.3f, 0.55f);
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        marked.removeIf(player -> !player.isAlive() || player.isSpectator() || player.isCreative());

        if (tick <= markTicks) {
            spawnMarks(level);
            return;
        }

        if (!exploded) {
            exploded = true;
            explode(level);
        }

        if (tick >= markTicks + 8) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
        for (Player player : marked) {
            player.removeEffect(MobEffects.GLOWING);
        }
        marked.clear();
    }

    private List<Player> findTargets() {
        if (boss.level().isClientSide()) return List.of();
        ServerLevel level = (ServerLevel) boss.level();
        List<Player> players = level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(targetRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(boss)));
        if (players.size() > maxTargets) {
            return new ArrayList<>(players.subList(0, maxTargets));
        }
        return players;
    }

    private void spawnMarks(ServerLevel level) {
        double progress = (double) tick / Math.max(1, markTicks);
        for (Player player : marked) {
            double ring = blastRadius * (0.6 + 0.4 * progress);
            int points = 22;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + tick * 0.16;
                double px = player.getX() + Math.cos(angle) * ring;
                double pz = player.getZ() + Math.sin(angle) * ring;
                level.sendParticles(ParticleTypes.SOUL, px, player.getY() + 0.08, pz, 1, 0.02, 0.02, 0.02, 0.01);
                if (i % 4 == 0) {
                    level.sendParticles(ParticleTypes.WITCH, px, player.getY() + 0.35, pz, 1, 0.03, 0.03, 0.03, 0.02);
                }
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.25, 0.5, 0.25, 0.02);
        }
        if (tick % 20 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.45f, 0.8f + (float) progressPitch());
        }
    }

    private double progressPitch() {
        return Math.min(0.8, (double) tick / Math.max(1, markTicks));
    }

    private void explode(ServerLevel level) {
        for (Player marker : marked) {
            Vec3 center = marker.position();
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6f, 0.75f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.4, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    center.x, center.y + 0.5, center.z, 45, blastRadius * 0.35, 0.6, blastRadius * 0.35, 0.08);

            AABB area = new AABB(
                    center.x - blastRadius, center.y - 1.0, center.z - blastRadius,
                    center.x + blastRadius, center.y + 2.5, center.z + blastRadius);
            List<Player> victims = level.getEntitiesOfClass(Player.class, area,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && (hitMarkedPlayer || p != marker)
                            && p.position().distanceToSqr(center) <= blastRadius * blastRadius);

            for (Player victim : victims) {
                victim.hurtServer(level, boss.damageSources().magic(), damage);
                Vec3 away = victim.position().subtract(center);
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 1.0e-6) away = victim.position().subtract(boss.position());
                if (away.lengthSqr() < 1.0e-6) away = new Vec3(0, 0, 1);
                away = away.normalize();
                victim.push(away.x * knockback, 0.35, away.z * knockback);
                victim.hurtMarked = true;
            }
        }
    }
}
