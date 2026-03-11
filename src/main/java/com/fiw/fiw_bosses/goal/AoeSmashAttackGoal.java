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
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

public class AoeSmashAttackGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float damage;
    private final float knockback;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int windupTick;
    private static final int WINDUP_DURATION = 20;

    public AoeSmashAttackGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 5.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 15.0f;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsFloat() : 2.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.squaredDistanceTo(target) <= (radius + 2) * (radius + 2);
    }

    @Override
    public void start() {
        windupTick = 0;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Windup sound — rising rumble
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
    }

    @Override
    public boolean shouldContinue() {
        return windupTick < WINDUP_DURATION + 5;
    }

    @Override
    public void tick() {
        windupTick++;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            // Windup: rising ground particles, expanding ring
            if (windupTick <= WINDUP_DURATION) {
                float progress = (float) windupTick / WINDUP_DURATION;
                int particleCount = (int) (4 + progress * 10);
                double ringRadius = radius * progress * 0.8;

                for (int i = 0; i < particleCount; i++) {
                    double angle = Math.toRadians((360.0 / particleCount) * i + windupTick * 12);
                    double px = boss.getX() + Math.cos(angle) * ringRadius;
                    double pz = boss.getZ() + Math.sin(angle) * ringRadius;
                    world.spawnParticles(ParticleTypes.CLOUD,
                            px, boss.getY() + 0.1, pz, 1, 0, 0.1, 0, 0.01);
                }

                // Center buildup
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        boss.getX(), boss.getY() + 0.5 + progress, boss.getZ(),
                        2, 0.15, 0.1, 0.15, 0.02);
            }
        }

        if (windupTick == WINDUP_DURATION) {
            performSmash();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void performSmash() {
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        Vec3d center = boss.getPos();

        // Impact sound
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.0f, 0.4f);

        Box aoeBox = boss.getBoundingBox().expand(radius);
        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, aoeBox,
                e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

        int hitCount = 0;
        for (LivingEntity entity : entities) {
            double dist = entity.distanceTo(boss);
            if (dist <= radius) {
                float finalDamage = damage * (1.0f - (float) (dist / radius) * 0.3f);
                entity.damage(boss.getDamageSources().mobAttack(boss), finalDamage);

                Vec3d dir = entity.getPos().subtract(center).normalize();
                double yLaunch = 0.4 + (1.0 - dist / radius) * 0.4;
                entity.addVelocity(dir.x * knockback, yLaunch, dir.z * knockback);
                entity.velocityModified = true;
                hitCount++;
            }
        }

        // Impact particles — expanding shockwave ring
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = radius * (0.3 + ring * 0.3);
            int count = 12 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians((360.0 / count) * i);
                double px = center.x + Math.cos(angle) * ringRadius;
                double pz = center.z + Math.sin(angle) * ringRadius;
                world.spawnParticles(ParticleTypes.EXPLOSION, px, center.y + 0.1, pz, 1, 0, 0, 0, 0);
            }
        }
        // Dust cloud
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y, center.z, 20, radius * 0.4, 0.3, radius * 0.4, 0.05);
        // Ground cracks
        world.spawnParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.1, center.z, 8, radius * 0.3, 0.0, radius * 0.3, 0.0);

        // Taunt
        if (hitCount > 0 && taunt != null && boss.getRandom().nextFloat() < 0.4f) {
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
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
