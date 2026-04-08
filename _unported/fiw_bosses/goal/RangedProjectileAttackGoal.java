package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

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
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.squaredDistanceTo(target) >= 4 * 4
                && boss.squaredDistanceTo(target) <= 40 * 40;
    }

    @Override
    public void start() {
        windupTick = 0;
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }
    }

    @Override
    public boolean shouldContinue() {
        return windupTick < WINDUP_TICKS + 5;
    }

    @Override
    public void tick() {
        windupTick++;

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }

        // Windup — gathering energy
        if (windupTick <= WINDUP_TICKS && !boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            Vec3d handPos = boss.getPos().add(0, boss.getHeight() * 0.7, 0);

            if (projectileType.contains("fireball")) {
                world.spawnParticles(ParticleTypes.FLAME,
                        handPos.x, handPos.y, handPos.z, 3, 0.15, 0.15, 0.15, 0.03);
                world.spawnParticles(ParticleTypes.SMOKE,
                        handPos.x, handPos.y, handPos.z, 1, 0.1, 0.1, 0.1, 0.01);
            } else {
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
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
        if (boss.getWorld().isClient) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        if (projectileType.contains("fireball")) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.7f);
        } else {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }

        for (int i = 0; i < count; i++) {
            Vec3d dir = target.getPos().add(0, target.getHeight() / 2, 0)
                    .subtract(boss.getPos().add(0, boss.getHeight() / 2, 0))
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
                    FireballEntity fireball = new FireballEntity(world, boss, dir.x, dir.y, dir.z, 1);
                    fireball.setPosition(startX, startY, startZ);
                    world.spawnEntity(fireball);
                }
                case "minecraft:arrow" -> {
                    ArrowEntity arrow = new ArrowEntity(world, boss);
                    arrow.setPosition(startX, startY, startZ);
                    arrow.setVelocity(dir.x, dir.y, dir.z, 1.6f, spread);
                    arrow.setDamage(2.5);
                    world.spawnEntity(arrow);
                }
                default -> {
                    SmallFireballEntity fireball = new SmallFireballEntity(world, boss, dir.x, dir.y, dir.z);
                    fireball.setPosition(startX, startY, startZ);
                    world.spawnEntity(fireball);
                }
            }

            world.spawnParticles(ParticleTypes.FLASH,
                    startX + dir.x * 0.5, startY + dir.y * 0.5, startZ + dir.z * 0.5,
                    1, 0, 0, 0, 0);
        }

        if (taunt != null && boss.getRandom().nextFloat() < 0.25f) {
            var bossName = boss.getCustomName();
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Text.literal("Boss"))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(taunt));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48) {
                    player.sendMessage(tauntText, false);
                }
            }
        }
    }
}
