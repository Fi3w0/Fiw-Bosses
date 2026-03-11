package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

/**
 * Spawns randomized flame particles around the boss — imitates the appearance
 * of a mob spawner fire effect. Purely visual; boss keeps moving freely.
 *
 * JSON params:
 *   radius    (float,  3.0)  spread radius of the flames
 *   duration  (int,   60)   ticks the effect lasts
 *   density   (int,    6)   flame particles spawned per tick
 */
public class FlamesGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final int duration;
    private final int density;
    private final int cooldown;
    private int cooldownTimer;
    private int tick;

    public FlamesGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss     = boss;
        this.radius   = params.has("radius")   ? params.get("radius").getAsFloat()  : 3.0f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt()  : 60;
        this.density  = params.has("density")  ? params.get("density").getAsInt()   : 6;
        this.cooldown = cooldownTicks;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
    }

    @Override
    public boolean shouldContinue() {
        return tick < duration;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        for (int i = 0; i < density; i++) {
            double rx = (boss.getRandom().nextDouble() * 2 - 1) * radius;
            double rz = (boss.getRandom().nextDouble() * 2 - 1) * radius;
            double ry = boss.getRandom().nextDouble() * 2.0;
            world.spawnParticles(ParticleTypes.FLAME,
                    boss.getX() + rx, boss.getY() + ry, boss.getZ() + rz,
                    1, 0, 0, 0, 0.02 + boss.getRandom().nextFloat() * 0.04);
        }

        // Occasional small fireball burst
        if (tick % 10 == 0) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    3, radius * 0.4, 0.3, radius * 0.4, 0.01);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
