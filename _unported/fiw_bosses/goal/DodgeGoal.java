package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
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
    public boolean shouldContinue() {
        return false;
    }

    private void performDodge() {
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        Vec3d look = boss.getRotationVec(1.0f).normalize();
        boolean dodgeRight = boss.getRandom().nextBoolean();
        Vec3d sideDir = dodgeRight
                ? new Vec3d(-look.z, 0, look.x)
                : new Vec3d(look.z, 0, -look.x);

        Vec3d dodgeTarget = boss.getPos().add(sideDir.multiply(distance));
        BlockPos groundCheck = new BlockPos(
                (int) dodgeTarget.x, (int) (dodgeTarget.y - 1), (int) dodgeTarget.z);

        if (world.getBlockState(groundCheck).isSolidBlock(world, groundCheck)) {
            // Afterimage particles at old position
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    8, 0.2, 0.5, 0.2, 0.02);
            world.spawnParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.5, boss.getZ(),
                    5, 0.15, 0.3, 0.15, 0.05);

            // Dodge sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.0f, 1.5f);

            boss.setVelocity(sideDir.multiply(0.9).add(0, 0.15, 0));
            boss.velocityModified = true;
        }
    }
}
