package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        return hpPercent <= belowPercent;
    }

    @Override
    public void start() {
        healTick = 0;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_EVOKER_PREPARE_WOLOLO, SoundCategory.HOSTILE, 2.0f, 1.0f);

            // Taunt
            String msg = taunt != null ? taunt : "&a&lYou cannot stop me!";
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(msg));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48) {
                    player.sendMessage(tauntText, false);
                }
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return healTick < HEAL_DURATION;
    }

    @Override
    public void tick() {
        healTick++;

        float healPerTick = amount / HEAL_DURATION;
        boss.heal(healPerTick);

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            // Heart particles
            if (healTick % 2 == 0) {
                world.spawnParticles(ParticleTypes.HEART,
                        boss.getX(), boss.getY() + 2.2, boss.getZ(),
                        1, 0.4, 0.2, 0.4, 0.0);
            }

            // Healing aura — green sparkles rising
            if (healTick % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians((360.0 / 6) * i + healTick * 8);
                    double px = boss.getX() + Math.cos(angle) * 0.8;
                    double pz = boss.getZ() + Math.sin(angle) * 0.8;
                    double py = boss.getY() + 0.2 + ((float) healTick / HEAL_DURATION) * 2.0;
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            // Healing sound halfway through
            if (healTick == HEAL_DURATION / 2) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.HOSTILE, 0.8f, 2.0f);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
