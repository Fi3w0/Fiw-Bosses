package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class MirrorImageGoal extends Goal {

    private final BossEntity boss;
    private final int cloneCount;
    private final double radius;
    private final int duration;
    private final int swapInterval;
    private final boolean invisibility;
    private final float reappearDamage;
    private final double reappearRadius;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private final List<Vec3> images = new ArrayList<>();

    public MirrorImageGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.cloneCount = params.has("cloneCount") ? params.get("cloneCount").getAsInt() : 4;
        this.radius = params.has("radius") ? params.get("radius").getAsDouble() : 5.0;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 100;
        this.swapInterval = params.has("swapInterval") ? params.get("swapInterval").getAsInt() : 25;
        this.invisibility = !params.has("invisibility") || params.get("invisibility").getAsBoolean();
        this.reappearDamage = params.has("reappearDamage") ? params.get("reappearDamage").getAsFloat() : 0.0f;
        this.reappearRadius = params.has("reappearRadius") ? params.get("reappearRadius").getAsDouble() : 3.0;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= 24.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        rebuildImages();

        if (invisibility) {
            boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration + 10, 0, false, false));
        }

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4f, 1.8f);
            sendTaunt(level);
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        renderImages(level);

        if (swapInterval > 0 && tick % swapInterval == 0 && !images.isEmpty()) {
            Vec3 target = images.get(boss.getRandom().nextInt(images.size()));
            if (canSwapTo(target)) {
                level.sendParticles(ParticleTypes.POOF,
                        boss.getX(), boss.getY() + 1.0, boss.getZ(), 12, 0.35, 0.5, 0.35, 0.05);
                boss.teleportTo(target.x, target.y, target.z);
                level.playSound(null, target.x, target.y, target.z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9f, 1.7f);
            }
            rebuildImages();
        }

        if (tick >= duration) {
            active = false;
        }
    }

    @Override
    public void stop() {
        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            if (invisibility) {
                boss.removeEffect(MobEffects.INVISIBILITY);
            }
            reappear(level);
        }
        images.clear();
        active = false;
        cooldownTimer = cooldown;
    }

    private boolean canSwapTo(Vec3 target) {
        return boss.level().noCollision(boss,
                boss.getBoundingBox().move(target.subtract(boss.position())));
    }

    private void rebuildImages() {
        images.clear();
        int count = Math.max(1, cloneCount);
        double base = boss.getRandom().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            double angle = base + Math.PI * 2.0 * i / count;
            double dist = radius * (0.75 + boss.getRandom().nextDouble() * 0.35);
            images.add(boss.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist));
        }
    }

    private void renderImages(ServerLevel level) {
        for (Vec3 pos : images) {
            level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z, 2, 0.25, 0.5, 0.25, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.8, pos.z, 1, 0.12, 0.15, 0.12, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.3, pos.z, 1, 0.18, 0.08, 0.18, 0.0);
            if (tick % 8 == 0) {
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1.0, pos.z, 1, 0.15, 0.3, 0.15, 0.01);
            }
        }
    }

    private void reappear(ServerLevel level) {
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF),
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 30, 0.5, 0.8, 0.5, 0.08);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.2f, 1.2f);

        if (reappearDamage <= 0) return;
        AABB area = boss.getBoundingBox().inflate(reappearRadius);
        List<Player> victims = level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        && boss.distanceToSqr(p) <= reappearRadius * reappearRadius);
        for (Player player : victims) {
            player.hurtServer(level, boss.damageSources().magic(), reappearDamage);
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
