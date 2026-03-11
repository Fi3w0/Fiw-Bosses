package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
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

public class PullGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float strength;
    private final int duration;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int pullTick;

    public PullGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 10.0f;
        this.strength = params.has("strength") ? params.get("strength").getAsFloat() : 0.8f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 20;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
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
        pullTick = 0;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.8f);

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
        return pullTick < duration;
    }

    @Override
    public void tick() {
        pullTick++;

        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        // Spiraling portal vortex particles converging on boss
        for (int i = 0; i < 8; i++) {
            double r = radius * (0.3 + boss.getRandom().nextDouble() * 0.7);
            double angle = Math.toRadians(pullTick * 22 + i * 45);
            double px = boss.getX() + Math.cos(angle) * r;
            double py = boss.getY() + 0.5 + boss.getRandom().nextDouble() * 2.5;
            double pz = boss.getZ() + Math.sin(angle) * r;
            world.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, 0, 0, 0);
        }

        // Pull all players within radius toward the boss
        Box area = Box.of(boss.getPos(), radius * 2, radius * 2, radius * 2);
        List<PlayerEntity> targets = world.getEntitiesByClass(PlayerEntity.class, area,
                p -> p.isAlive() && p.squaredDistanceTo(boss) <= radius * radius);

        Vec3d bossCenter = boss.getPos().add(0, 1.0, 0);
        for (PlayerEntity player : targets) {
            double dist = player.distanceTo(boss);
            if (dist < 1.5) continue; // don't pull if already touching
            Vec3d toward = bossCenter.subtract(player.getPos()).normalize();
            double pull = strength * (dist / radius);
            player.addVelocity(toward.x * pull, toward.y * pull * 0.3, toward.z * pull);
            player.velocityModified = true;
        }

        // Periodic sound
        if (pullTick % 10 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 0.8f, 1.2f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Vortex implosion at end
            world.spawnParticles(ParticleTypes.PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    40, 0.6, 1.2, 0.6, 0.35);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
    }
}
