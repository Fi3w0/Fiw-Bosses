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

public class ShieldGoal extends Goal {

    private final BossEntity boss;
    private final int durationTicks;
    private final float damageReduction;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int activeTick;

    public ShieldGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.durationTicks = params.has("durationTicks") ? params.get("durationTicks").getAsInt() : 60;
        this.damageReduction = params.has("damageReduction") ? params.get("damageReduction").getAsFloat() : 0.8f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = cooldown / 2;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        return hpPercent < 0.6f;
    }

    @Override
    public void start() {
        activeTick = 0;
        boss.setDamageReduction(damageReduction);

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 0.5f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 1.5f);

            level.sendParticles(ParticleTypes.END_ROD,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    20, 1.0, 1.0, 1.0, 0.1);

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
        return activeTick < durationTicks;
    }

    @Override
    public void tick() {
        activeTick++;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();

            if (activeTick % 3 == 0) {
                int points = 10;
                for (int i = 0; i < points; i++) {
                    double angle = Math.toRadians((360.0 / points) * i + activeTick * 6);
                    double px = boss.getX() + Math.cos(angle) * 1.3;
                    double pz = boss.getZ() + Math.sin(angle) * 1.3;
                    double py = boss.getY() + 0.3 + (Math.sin(angle + activeTick * 0.1) * 0.5 + 0.5) * 1.4;
                    level.sendParticles(ParticleTypes.END_ROD,
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            if (activeTick % 20 == 0) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.5f, 2.0f);
            }
        }
    }

    @Override
    public void stop() {
        boss.setDamageReduction(0.0f);
        cooldownTimer = cooldown;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    15, 0.8, 0.8, 0.8, 0.3);
        }
    }
}
