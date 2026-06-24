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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class PullGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float strength;
    private final int duration;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int pullTick;

    public PullGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 10.0f;
        this.strength = params.has("strength") ? params.get("strength").getAsFloat() : 0.8f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 20;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        pullTick = 0;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.8f);

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
        return pullTick < duration;
    }

    @Override
    public void tick() {
        pullTick++;

        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();

        for (int i = 0; i < 8; i++) {
            double r = radius * (0.3 + boss.getRandom().nextDouble() * 0.7);
            double angle = Math.toRadians(pullTick * 22 + i * 45);
            double px = boss.getX() + Math.cos(angle) * r;
            double py = boss.getY() + 0.5 + boss.getRandom().nextDouble() * 2.5;
            double pz = boss.getZ() + Math.sin(angle) * r;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, 0, 0, 0);
        }

        AABB area = AABB.ofSize(boss.position(), radius * 2, radius * 2, radius * 2);
        List<Player> targets = level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && p.distanceToSqr(boss) <= radius * radius);

        Vec3 bossCenter = boss.position().add(0, 1.0, 0);
        for (Player player : targets) {
            double dist = player.distanceTo(boss);
            if (dist < 1.5) continue;
            Vec3 toward = bossCenter.subtract(player.position()).normalize();
            double pull = strength * (dist / radius);
            player.push(toward.x * pull, toward.y * pull * 0.3, toward.z * pull);
            player.hurtMarked = true;
        }

        if (pullTick % 10 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.2f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    40, 0.6, 1.2, 0.6, 0.35);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
        }
    }
}
