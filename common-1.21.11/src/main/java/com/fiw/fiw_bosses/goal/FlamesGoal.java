package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

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
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < duration;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        for (int i = 0; i < density; i++) {
            double rx = (boss.getRandom().nextDouble() * 2 - 1) * radius;
            double rz = (boss.getRandom().nextDouble() * 2 - 1) * radius;
            double ry = boss.getRandom().nextDouble() * 2.0;
            level.sendParticles(ParticleTypes.FLAME,
                    boss.getX() + rx, boss.getY() + ry, boss.getZ() + rz,
                    1, 0, 0, 0, 0.02 + boss.getRandom().nextFloat() * 0.04);
        }

        if (tick % 10 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    3, radius * 0.4, 0.3, radius * 0.4, 0.01);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
