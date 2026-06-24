package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SwapGoal extends Goal {

    private final BossEntity boss;
    private final float maxDistance;
    private final float minDistance;
    private final String taunt;
    private final int cooldown;
    private int cooldownTimer;

    public SwapGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.maxDistance = params.has("maxDistance") ? params.get("maxDistance").getAsFloat() : 20.0f;
        this.minDistance = params.has("minDistance") ? params.get("minDistance").getAsFloat() : 3.0f;
        this.taunt       = params.has("taunt")       ? params.get("taunt").getAsString()       : null;
        this.cooldown    = cooldownTicks;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = boss.distanceTo(target);
        return dist >= minDistance && dist <= maxDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return;
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();

        Vec3 bossPos   = boss.position();
        Vec3 targetPos = target.position();

        spawnSwapBurst(level, bossPos);
        spawnSwapBurst(level, targetPos);

        boss.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(bossPos.x, bossPos.y, bossPos.z);

        spawnSwapBurst(level, targetPos);
        spawnSwapBurst(level, bossPos);

        level.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 0.6f);
        level.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 1.4f);

        if (taunt != null) {
            var bossName = boss.getCustomName();
            MutableComponent nameText = bossName != null ? bossName.copy() : Component.literal("Boss");
            Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(nameText)
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(taunt));
            for (var player : level.players()) {
                if (player.distanceToSqr(boss) <= 48 * 48)
                    player.sendSystemMessage(tauntText);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void spawnSwapBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0, pos.z, 30, 0.4, 0.6, 0.4, 0.3);
        level.sendParticles(ParticleTypes.POOF,
                pos.x, pos.y + 0.5, pos.z, 12, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.1);
    }
}
