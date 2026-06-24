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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Strips every harmful status effect off the boss and, for a short window, makes it
 * immune to new debuffs ({@link BossEntity#canBeAffected}).
 */
public class CleanseGoal extends Goal {

    private final BossEntity boss;
    private final int resistTicks;
    private final boolean requireDebuff;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;

    public CleanseGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.resistTicks   = params.has("resistTicks")   ? params.get("resistTicks").getAsInt()       : 60;
        this.requireDebuff = !params.has("requireDebuff") || params.get("requireDebuff").getAsBoolean();
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        if (!requireDebuff) return true;
        for (MobEffectInstance effect : boss.getActiveEffects()) {
            if (!effect.getEffect().isBeneficial()) return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (!(boss.level() instanceof ServerLevel level)) return;

        List<MobEffect> harmful = new ArrayList<>();
        for (MobEffectInstance effect : boss.getActiveEffects()) {
            if (!effect.getEffect().isBeneficial()) harmful.add(effect.getEffect());
        }
        for (MobEffect effect : harmful) {
            boss.removeEffect(effect);
        }

        boss.setDebuffImmuneTicks(resistTicks);

        level.sendParticles(ParticleTypes.END_ROD,
                boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                40, 0.5, 0.8, 0.5, 0.12);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 18, 0.4, 0.6, 0.4, 0.1);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 1.4f, 1.4f);

        sendTaunt(level);
        cooldownTimer = cooldown;
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
