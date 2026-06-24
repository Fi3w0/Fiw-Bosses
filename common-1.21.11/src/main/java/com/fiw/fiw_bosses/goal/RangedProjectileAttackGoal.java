package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RangedProjectileAttackGoal extends Goal {

    private final BossEntity boss;
    private final String projectileType;
    private final int count;
    private final float spread;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int windupTick;
    private static final int WINDUP_TICKS = 8;

    public RangedProjectileAttackGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.projectileType = params.has("projectile") ? params.get("projectile").getAsString() : "minecraft:small_fireball";
        this.count = params.has("count") ? params.get("count").getAsInt() : 1;
        this.spread = params.has("spread") ? params.get("spread").getAsFloat() : 5.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) >= 4 * 4
                && boss.distanceToSqr(target) <= 40 * 40;
    }

    @Override
    public void start() {
        windupTick = 0;
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 360, 90);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return windupTick < WINDUP_TICKS + 5;
    }

    @Override
    public void tick() {
        windupTick++;

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 360, 90);
        }

        if (windupTick <= WINDUP_TICKS && !boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            Vec3 handPos = boss.position().add(0, boss.getBbHeight() * 0.7, 0);

            if (projectileType.contains("fireball")) {
                level.sendParticles(ParticleTypes.FLAME,
                        handPos.x, handPos.y, handPos.z, 3, 0.15, 0.15, 0.15, 0.03);
                level.sendParticles(ParticleTypes.SMOKE,
                        handPos.x, handPos.y, handPos.z, 1, 0.1, 0.1, 0.1, 0.01);
            } else {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        handPos.x, handPos.y, handPos.z, 2, 0.15, 0.15, 0.15, 0.05);
            }
        }

        if (windupTick == WINDUP_TICKS) {
            shootProjectiles();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void shootProjectiles() {
        if (boss.level().isClientSide()) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerLevel level = (ServerLevel) boss.level();

        if (projectileType.contains("fireball")) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.7f);
        } else {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.5f, 0.8f);
        }

        for (int i = 0; i < count; i++) {
            Vec3 dir = target.position().add(0, target.getBbHeight() / 2, 0)
                    .subtract(boss.position().add(0, boss.getBbHeight() / 2, 0))
                    .normalize();

            if (count > 1) {
                double spreadRad = Math.toRadians(spread);
                dir = dir.add(
                        (boss.getRandom().nextDouble() - 0.5) * spreadRad,
                        (boss.getRandom().nextDouble() - 0.5) * spreadRad * 0.5,
                        (boss.getRandom().nextDouble() - 0.5) * spreadRad
                ).normalize();
            }

            double startX = boss.getX();
            double startY = boss.getEyeY() - 0.1;
            double startZ = boss.getZ();

            switch (projectileType) {
                case "minecraft:fireball" -> {
                    LargeFireball fireball = new LargeFireball(level, boss, dir, 1);
                    fireball.setPos(startX, startY, startZ);
                    level.addFreshEntity(fireball);
                }
                case "minecraft:arrow" -> {
                    Arrow arrow = new Arrow(level, boss, new ItemStack(Items.ARROW), null);
                    arrow.setPos(startX, startY, startZ);
                    arrow.shoot(dir.x, dir.y, dir.z, 1.6f, spread);
                    arrow.setBaseDamage(2.5);
                    level.addFreshEntity(arrow);
                }
                default -> {
                    SmallFireball fireball = new SmallFireball(level, boss, dir);
                    fireball.setPos(startX, startY, startZ);
                    level.addFreshEntity(fireball);
                }
            }

            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF),
                    startX + dir.x * 0.5, startY + dir.y * 0.5, startZ + dir.z * 0.5,
                    1, 0, 0, 0, 0);
        }

        if (taunt != null && boss.getRandom().nextFloat() < 0.25f) {
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
}
