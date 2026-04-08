package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Freezes nearby players for a configurable duration.
 * The goal runs for `duration` ticks and re-applies frozen ticks every tick,
 * holding the freeze steady rather than letting it count down immediately.
 *
 * JSON params:
 *   duration   (int,   60)    how many ticks the freeze is held (20 = 1 second)
 *   intensity  (int,  140)    frozen ticks value maintained each tick (140 = full overlay, player cap)
 *   radius     (float, 8.0)   range to freeze players
 *   taunt      (string, null)
 */
public class FreezeGoal extends Goal {

    private final BossEntity boss;
    private final int duration;
    private final int intensity;
    private final float radius;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private List<LivingEntity> frozenTargets;

    public FreezeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss      = boss;
        this.duration  = params.has("duration")  ? params.get("duration").getAsInt()   : 60;
        this.intensity = params.has("intensity") ? params.get("intensity").getAsInt()  : 140;
        this.radius    = params.has("radius")    ? params.get("radius").getAsFloat()   : 8.0f;
        this.taunt     = params.has("taunt")     ? params.get("taunt").getAsString()   : null;
        this.cooldown  = cooldownTicks;
        this.cooldownTimer = 0;
        this.frozenTargets = new ArrayList<>();
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.squaredDistanceTo(target) <= radius * radius;
    }

    @Override
    public boolean shouldContinue() {
        return tick < duration;
    }

    @Override
    public void start() {
        tick = 0;
        frozenTargets = new ArrayList<>();
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Collect targets once at start
        Box area = boss.getBoundingBox().expand(radius);
        frozenTargets = world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != boss && e.isAlive() && !boss.isMinion(e)
                     && boss.squaredDistanceTo(e) <= radius * radius);

        if (frozenTargets.isEmpty()) return;

        // Entry effects
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.2f, 1.0f);

        for (LivingEntity entity : frozenTargets) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    16, 0.4, 0.6, 0.4, 0.04);
            world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    10, 0.3, 0.3, 0.3, 0.08);
        }

        if (taunt != null) {
            var bossName = boss.getCustomName();
            net.minecraft.text.MutableText nameText = bossName != null ? bossName.copy() : Text.literal("Boss");
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(nameText)
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(taunt));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48)
                    player.sendMessage(tauntText, false);
            }
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Re-apply frozen ticks every tick to hold the freeze in place
        frozenTargets.removeIf(e -> !e.isAlive());
        for (LivingEntity entity : frozenTargets) {
            entity.setFrozenTicks(intensity);
        }

        // Light snowflake trail every 4 ticks so it's visible without being spammy
        if (tick % 4 == 0) {
            for (LivingEntity entity : frozenTargets) {
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        3, 0.3, 0.4, 0.3, 0.02);
            }
        }

        // End warning: play sound 20 ticks before freeze expires
        if (tick == duration - 20 && duration > 20) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 1.5f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        // Release targets — frozen ticks will naturally decay
        frozenTargets.clear();
    }
}
