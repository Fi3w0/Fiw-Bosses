package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class MeteorGoal extends Goal {

    private final BossEntity boss;
    private final int count;
    private final float height;
    private final String type;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int windupTick;
    private static final int WINDUP_TICKS = 20;
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    public MeteorGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.count = params.has("count") ? params.get("count").getAsInt() : 3;
        this.height = params.has("height") ? params.get("height").getAsFloat() : 20.0f;
        this.type = params.has("type") ? params.get("type").getAsString() : "fireball";
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
                && boss.distanceToSqr(target) <= 40 * 40;
    }

    @Override
    public void start() {
        windupTick = 0;

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);

            if (taunt != null) {
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

    @Override
    public boolean canContinueToUse() {
        return windupTick < WINDUP_TICKS;
    }

    @Override
    public void tick() {
        windupTick++;

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();

        if (target != null) {
            for (int i = 0; i < count; i++) {
                double spread = count > 1 ? 3.0 : 0.0;
                double tx = target.getX() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
                double tz = target.getZ() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;

                for (int j = 0; j < 6; j++) {
                    double angle = Math.toRadians(j * 60.0 + windupTick * 18);
                    level.sendParticles(ParticleTypes.SMOKE,
                            tx + Math.cos(angle) * 1.5, target.getY() + 0.1, tz + Math.sin(angle) * 1.5,
                            1, 0, 0.05, 0, 0.01);
                }

                level.sendParticles(ParticleTypes.FLAME,
                        tx, target.getY() + height * 0.5 + (boss.getRandom().nextDouble() - 0.5) * 5, tz,
                        1, 0.2, 0.3, 0.2, 0.02);
            }
        }

        if (windupTick == WINDUP_TICKS) {
            fireMeteors();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void fireMeteors() {
        if (boss.level().isClientSide) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerLevel level = (ServerLevel) boss.level();

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.4f);

        for (int i = 0; i < count; i++) {
            double spread = count > 1 ? 3.0 : 0.0;
            double tx = target.getX() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
            double ty = target.getY();
            double tz = target.getZ() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
            double spawnY = ty + height;

            if (type.equals("wither_skull")) {
                WitherSkull skull = new WitherSkull(level, boss, DOWN.x, DOWN.y, DOWN.z);
                skull.setPos(tx, spawnY, tz);
                skull.setDangerous(boss.getRandom().nextFloat() < 0.2f);
                level.addFreshEntity(skull);
            } else {
                LargeFireball fireball = new LargeFireball(level, boss, DOWN.x, DOWN.y, DOWN.z, 1);
                fireball.setPos(tx, spawnY, tz);
                level.addFreshEntity(fireball);
            }

            for (int j = 0; j < 12; j++) {
                double angle = Math.toRadians(j * 30.0);
                level.sendParticles(ParticleTypes.LAVA,
                        tx + Math.cos(angle) * 1.8, ty + 0.15, tz + Math.sin(angle) * 1.8,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
