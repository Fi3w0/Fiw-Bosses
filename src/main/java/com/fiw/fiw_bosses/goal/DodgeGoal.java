package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DodgeGoal extends Goal {

    private final BossEntity boss;
    private final float chance;
    private final float distance;
    private final int cooldown;
    private int cooldownTimer;
    private float lastHealth;

    public DodgeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.chance = params.has("chance") ? params.get("chance").getAsFloat() : 0.3f;
        this.distance = params.has("distance") ? params.get("distance").getAsFloat() : 3.0f;
        this.cooldown = cooldownTicks;
        this.cooldownTimer = 0;
        this.lastHealth = boss.getHealth();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) cooldownTimer--;
        float currentHealth = boss.getHealth();
        boolean tookDamage = currentHealth < lastHealth;
        lastHealth = currentHealth;

        return tookDamage && cooldownTimer <= 0 && boss.getRandom().nextFloat() < chance;
    }

    @Override
    public void start() {
        performDodge();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private void performDodge() {
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();

        Vec3 look = boss.getViewVector(1.0f).normalize();
        boolean dodgeRight = boss.getRandom().nextBoolean();
        Vec3 sideDir = dodgeRight
                ? new Vec3(-look.z, 0, look.x)
                : new Vec3(look.z, 0, -look.x);

        Vec3 dodgeTarget = boss.position().add(sideDir.scale(distance));
        BlockPos groundCheck = BlockPos.containing(dodgeTarget.x, dodgeTarget.y - 1, dodgeTarget.z);

        if (level.getBlockState(groundCheck).isSolid()) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    8, 0.2, 0.5, 0.2, 0.02);
            level.sendParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.5, boss.getZ(),
                    5, 0.15, 0.3, 0.15, 0.05);

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.0f, 1.5f);

            boss.setDeltaMovement(sideDir.scale(0.9).add(0, 0.15, 0));
            boss.hasImpulse = true;
        }
    }
}
