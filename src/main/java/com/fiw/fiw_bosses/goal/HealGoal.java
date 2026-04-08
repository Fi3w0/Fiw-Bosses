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
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HealGoal extends Goal {

    private final BossEntity boss;
    private final float amount;
    private final float belowPercent;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int healTick;
    private static final int HEAL_DURATION = 30;

    public HealGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.amount = params.has("amount") ? params.get("amount").getAsFloat() : 30.0f;
        this.belowPercent = params.has("belowPercent") ? params.get("belowPercent").getAsFloat() : 0.3f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = cooldown;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        return hpPercent <= belowPercent;
    }

    @Override
    public void start() {
        healTick = 0;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 2.0f, 1.0f);

            String msg = taunt != null ? taunt : "&a&lYou cannot stop me!";
            var bossName = boss.getCustomName();
            Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(msg));
            for (var player : level.players()) {
                if (player.distanceToSqr(boss) <= 48 * 48) {
                    player.sendSystemMessage(tauntText);
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return healTick < HEAL_DURATION;
    }

    @Override
    public void tick() {
        healTick++;

        float healPerTick = amount / HEAL_DURATION;
        boss.heal(healPerTick);

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();

            if (healTick % 2 == 0) {
                level.sendParticles(ParticleTypes.HEART,
                        boss.getX(), boss.getY() + 2.2, boss.getZ(),
                        1, 0.4, 0.2, 0.4, 0.0);
            }

            if (healTick % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians((360.0 / 6) * i + healTick * 8);
                    double px = boss.getX() + Math.cos(angle) * 0.8;
                    double pz = boss.getZ() + Math.sin(angle) * 0.8;
                    double py = boss.getY() + 0.2 + ((float) healTick / HEAL_DURATION) * 2.0;
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            if (healTick == HEAL_DURATION / 2) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 0.8f, 2.0f);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
