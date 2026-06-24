package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Arms a one-shot auto-revive. The actual save happens in {@link BossEntity#hurtServer}
 * when a fatal blow lands; this goal only keeps the boss armed and re-arms it after the
 * cooldown once it has been spent.
 */
public class SecondWindGoal extends Goal {

    private final BossEntity boss;
    private final float revivePercent;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;

    public SecondWindGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        float pct = params.has("revivePercent") ? params.get("revivePercent").getAsFloat() : 0.5f;
        this.revivePercent = Math.max(0.05f, Math.min(1.0f, pct));
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return !boss.isSecondWindArmed();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        boss.armSecondWind(revivePercent);
        cooldownTimer = cooldown;
        if (boss.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                    14, 0.4, 0.6, 0.4, 0.1);
            sendTaunt(level);
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
