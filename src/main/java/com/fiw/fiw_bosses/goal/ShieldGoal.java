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
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        return hpPercent < 0.6f;
    }

    @Override
    public void start() {
        activeTick = 0;
        boss.setDamageReduction(damageReduction);

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Activation sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 1.5f);

            // Burst of shield particles
            world.spawnParticles(ParticleTypes.END_ROD,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    20, 1.0, 1.0, 1.0, 0.1);

            // Taunt
            String msg = taunt != null ? taunt : "&b&lYour attacks are futile!";
            var bossName = boss.getCustomName();
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Text.literal("Boss"))
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
        return activeTick < durationTicks;
    }

    @Override
    public void tick() {
        activeTick++;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            // Rotating shield ring
            if (activeTick % 3 == 0) {
                int points = 10;
                for (int i = 0; i < points; i++) {
                    double angle = Math.toRadians((360.0 / points) * i + activeTick * 6);
                    double px = boss.getX() + Math.cos(angle) * 1.3;
                    double pz = boss.getZ() + Math.sin(angle) * 1.3;
                    double py = boss.getY() + 0.3 + (Math.sin(angle + activeTick * 0.1) * 0.5 + 0.5) * 1.4;
                    world.spawnParticles(ParticleTypes.END_ROD,
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }

            // Ambient hum every second
            if (activeTick % 20 == 0) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 0.5f, 2.0f);
            }
        }
    }

    @Override
    public void stop() {
        boss.setDamageReduction(0.0f);
        cooldownTimer = cooldown;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Shield break effect
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    15, 0.8, 0.8, 0.8, 0.3);
        }
    }
}
