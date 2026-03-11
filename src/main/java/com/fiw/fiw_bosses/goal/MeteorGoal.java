package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.EnumSet;

public class MeteorGoal extends Goal {

    private final BossEntity boss;
    private final int count;
    private final float height;
    private final String type;    // "fireball" or "wither_skull"
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int windupTick;
    private static final int WINDUP_TICKS = 20;

    public MeteorGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.count = params.has("count") ? params.get("count").getAsInt() : 3;
        this.height = params.has("height") ? params.get("height").getAsFloat() : 20.0f;
        this.type = params.has("type") ? params.get("type").getAsString() : "fireball";
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
                && boss.squaredDistanceTo(target) <= 40 * 40;
    }

    @Override
    public void start() {
        windupTick = 0;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);

            if (taunt != null) {
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

    @Override
    public boolean shouldContinue() {
        return windupTick < WINDUP_TICKS;
    }

    @Override
    public void tick() {
        windupTick++;

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);

        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        // Warning particles: smoke rings on the ground + fire falling from sky
        if (target != null) {
            for (int i = 0; i < count; i++) {
                double spread = count > 1 ? 3.0 : 0.0;
                double tx = target.getX() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
                double tz = target.getZ() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;

                // Ground warning ring (pulses)
                for (int j = 0; j < 6; j++) {
                    double angle = Math.toRadians(j * 60.0 + windupTick * 18);
                    world.spawnParticles(ParticleTypes.SMOKE,
                            tx + Math.cos(angle) * 1.5, target.getY() + 0.1, tz + Math.sin(angle) * 1.5,
                            1, 0, 0.05, 0, 0.01);
                }

                // Falling fire from above
                world.spawnParticles(ParticleTypes.FLAME,
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
        if (boss.getWorld().isClient) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 0.4f);

        for (int i = 0; i < count; i++) {
            double spread = count > 1 ? 3.0 : 0.0;
            double tx = target.getX() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
            double ty = target.getY();
            double tz = target.getZ() + (boss.getRandom().nextDouble() - 0.5) * spread * 2;
            double spawnY = ty + height;

            if (type.equals("wither_skull")) {
                WitherSkullEntity skull = new WitherSkullEntity(world, boss, 0, -1, 0);
                skull.setPosition(tx, spawnY, tz);
                skull.setCharged(boss.getRandom().nextFloat() < 0.2f);
                world.spawnEntity(skull);
            } else {
                FireballEntity fireball = new FireballEntity(world, boss, 0, -1, 0, 1);
                fireball.setPosition(tx, spawnY, tz);
                world.spawnEntity(fireball);
            }

            // Impact ring on ground
            for (int j = 0; j < 12; j++) {
                double angle = Math.toRadians(j * 30.0);
                world.spawnParticles(ParticleTypes.LAVA,
                        tx + Math.cos(angle) * 1.8, ty + 0.15, tz + Math.sin(angle) * 1.8,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
