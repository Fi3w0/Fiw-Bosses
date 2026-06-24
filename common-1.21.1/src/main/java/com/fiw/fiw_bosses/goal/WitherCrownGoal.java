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
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class WitherCrownGoal extends Goal {

    private final BossEntity boss;
    private final int skulls;
    private final double orbitRadius;
    private final int windupTicks;
    private final int fireInterval;
    private final double speed;
    private final double range;
    private final float dangerousChance;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private int fired;
    private boolean active;

    public WitherCrownGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.skulls = params.has("skulls") ? params.get("skulls").getAsInt() : 6;
        this.orbitRadius = params.has("orbitRadius") ? params.get("orbitRadius").getAsDouble() : 2.4;
        this.windupTicks = params.has("windupTicks") ? params.get("windupTicks").getAsInt() : 30;
        this.fireInterval = params.has("fireInterval") ? params.get("fireInterval").getAsInt() : 8;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.range = params.has("range") ? params.get("range").getAsDouble() : 36.0;
        this.dangerousChance = params.has("dangerousChance") ? params.get("dangerousChance").getAsFloat() : 0.15f;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return skulls > 0 && target != null && target.isAlive() && boss.distanceTo(target) <= range;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        fired = 0;
        active = true;
        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.8f);
            sendTaunt(level);
        }
    }

    @Override
    public void tick() {
        tick++;
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        spawnCrown(level);

        if (tick > windupTicks && (tick - windupTicks - 1) % Math.max(1, fireInterval) == 0 && fired < skulls) {
            fireSkull(level, fired);
            fired++;
        }

        if (fired >= skulls && tick > windupTicks + skulls * Math.max(1, fireInterval) + 8) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
    }

    private void spawnCrown(ServerLevel level) {
        int remaining = Math.max(0, skulls - fired);
        double y = boss.getY() + boss.getBbHeight() + 0.35;
        for (int i = 0; i < remaining; i++) {
            double angle = Math.PI * 2.0 * (i + fired) / Math.max(1, skulls) + tick * 0.16;
            double x = boss.getX() + Math.cos(angle) * orbitRadius;
            double z = boss.getZ() + Math.sin(angle) * orbitRadius;
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.06, 0.06, 0.06, 0.01);
            level.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0.04, 0.04, 0.04, 0.01);
        }
    }

    private void fireSkull(ServerLevel level, int index) {
        LivingEntity target = boss.getTarget();
        double angle = Math.PI * 2.0 * index / Math.max(1, skulls) + tick * 0.16;
        Vec3 start = new Vec3(
                boss.getX() + Math.cos(angle) * orbitRadius,
                boss.getY() + boss.getBbHeight() + 0.35,
                boss.getZ() + Math.sin(angle) * orbitRadius);
        Vec3 dir;
        if (target != null && target.isAlive()) {
            dir = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(start);
        } else {
            dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        if (dir.lengthSqr() < 1.0e-6) {
            dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        dir = dir.normalize().scale(speed);

        WitherSkull skull = new WitherSkull(level, boss, dir);
        skull.setPos(start.x, start.y, start.z);
        skull.setDangerous(boss.getRandom().nextFloat() < dangerousChance);
        level.addFreshEntity(skull);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.2f, 0.75f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                start.x, start.y, start.z, 12, 0.15, 0.15, 0.15, 0.05);
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
