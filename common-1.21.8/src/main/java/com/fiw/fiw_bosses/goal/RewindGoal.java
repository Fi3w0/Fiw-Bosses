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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

/**
 * Continuously records the boss's position and health, and when it drops below a health
 * threshold (and is off cooldown) snaps it back to where it was a few seconds ago — health
 * and all. This is intentionally very strong: a long cooldown and a low trigger threshold
 * are recommended, and it should usually be the boss's signature/desperation move.
 */
public class RewindGoal extends Goal {

    private final BossEntity boss;
    private final int    historyTicks;
    private final double triggerBelowPercent;
    private final float  healPercent;
    private final String taunt;
    private final int    cooldown;

    private final Deque<double[]> history = new ArrayDeque<>(); // {x, y, z, health}
    private int cooldownTimer;

    public RewindGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        double seconds = params.has("delaySeconds") ? params.get("delaySeconds").getAsDouble() : 4.0;
        this.historyTicks = Math.max(20, (int) Math.round(seconds * 20.0));
        this.triggerBelowPercent = params.has("triggerBelowPercent") ? params.get("triggerBelowPercent").getAsDouble() : 0.5;
        this.healPercent = params.has("healPercent") ? params.get("healPercent").getAsFloat() : 1.0f;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = Math.max(1, cooldownTicks);
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true; // always running so it can keep recording history
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        if (boss.level().isClientSide()) return;
        if (cooldownTimer > 0) cooldownTimer--;

        if (boss.getBossState() != BossEntity.BossState.ACTIVE) {
            history.clear();
            return;
        }

        history.addLast(new double[]{boss.getX(), boss.getY(), boss.getZ(), boss.getHealth()});
        while (history.size() > historyTicks) history.removeFirst();

        if (cooldownTimer <= 0
                && history.size() >= historyTicks
                && boss.getHealth() <= boss.getMaxHealth() * triggerBelowPercent) {
            rewind((ServerLevel) boss.level());
        }
    }

    private void rewind(ServerLevel level) {
        double[] past = history.peekFirst();
        if (past == null) return;

        Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.5, 0);
        Vec3 to   = new Vec3(past[0], past[1] + boss.getBbHeight() * 0.5, past[2]);

        boss.teleportTo(past[0], past[1], past[2]);
        float restored = Math.min(boss.getMaxHealth(), (float) past[3] * healPercent);
        if (restored > boss.getHealth()) boss.setHealth(restored);

        int steps = Math.max(6, (int) (from.distanceTo(to) * 1.5));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.add(to.subtract(from).scale(t));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
        level.sendParticles(ParticleTypes.PORTAL,
                past[0], past[1] + 1.0, past[2], 60, 0.4, 0.8, 0.4, 0.4);
        level.sendParticles(ParticleTypes.ENCHANT,
                past[0], past[1] + 1.0, past[2], 40, 0.5, 0.8, 0.5, 0.2);
        level.playSound(null, past[0], past[1], past[2],
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.6f, 0.6f);
        level.playSound(null, past[0], past[1], past[2],
                SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4f, 1.3f);

        history.clear();
        cooldownTimer = cooldown;
        sendTaunt(level);
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
