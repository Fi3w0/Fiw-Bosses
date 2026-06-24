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

/**
 * Passive: every {@code updateInterval} ticks the boss looks at which damage type has
 * recently hurt it most and grows resistant to it for {@code windowTicks}. Encourages
 * players to vary their damage sources instead of spamming one.
 */
public class AdaptationGoal extends Goal {

    private final BossEntity boss;
    private final float resistPercent;
    private final int windowTicks;
    private final int updateInterval;
    private final boolean announce;
    private final String taunt;

    private int timer;
    private String lastType;

    public AdaptationGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        float pct = params.has("resistPercent") ? params.get("resistPercent").getAsFloat() : 0.4f;
        this.resistPercent = Math.max(0.0f, Math.min(0.9f, pct));
        this.windowTicks    = params.has("windowTicks")    ? params.get("windowTicks").getAsInt()    : 200;
        this.updateInterval = Math.max(20, params.has("updateInterval") ? params.get("updateInterval").getAsInt() : 100);
        this.announce = params.has("announce") && params.get("announce").getAsBoolean();
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        if (boss.level().isClientSide()) return;
        if (boss.getBossState() != BossEntity.BossState.ACTIVE) return;
        if (++timer < updateInterval) return;
        timer = 0;

        String dominant = boss.getDominantDamageType();
        if (dominant == null) return;

        boss.setAdaptation(dominant, resistPercent, windowTicks);

        ServerLevel level = (ServerLevel) boss.level();
        if (!dominant.equals(lastType)) {
            lastType = dominant;
            level.sendParticles(ParticleTypes.WAX_ON,
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                    16, 0.5, 0.7, 0.5, 0.05);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 0.8f, 1.4f);
            if (announce && taunt != null) sendTaunt(level, dominant);
        }
    }

    private void sendTaunt(ServerLevel level, String dominant) {
        var bossName = boss.getCustomName();
        Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(taunt.replace("{type}", dominant)));
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= 48 * 48) {
                player.sendSystemMessage(tauntText);
            }
        }
    }
}
