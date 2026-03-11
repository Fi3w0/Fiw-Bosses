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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrbitalGoal extends Goal {

    private final BossEntity boss;
    private final int orbCount;
    private final float radius;
    private final float damage;
    private final int duration;
    private final float speed;       // degrees per tick
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int orbitalTick;
    private float currentAngle;
    private final Set<UUID> hitThisTick = new HashSet<>();

    public OrbitalGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.orbCount = params.has("count") ? params.get("count").getAsInt() : 3;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 3.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 6.0f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 100;
        this.speed = params.has("speed") ? params.get("speed").getAsFloat() : 8.0f;
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
        orbitalTick = 0;
        currentAngle = 0;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.2f, 1.6f);

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
        return orbitalTick < duration && boss.getTarget() != null;
    }

    @Override
    public void tick() {
        orbitalTick++;
        currentAngle = (currentAngle + speed) % 360;
        hitThisTick.clear();

        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        double cx = boss.getX();
        double cy = boss.getY() + 1.0;
        double cz = boss.getZ();

        for (int i = 0; i < orbCount; i++) {
            double orbAngle = Math.toRadians(currentAngle + (360.0 / orbCount) * i);
            double ox = cx + Math.cos(orbAngle) * radius;
            double oz = cz + Math.sin(orbAngle) * radius;

            // Orb glow particles
            world.spawnParticles(ParticleTypes.END_ROD,
                    ox, cy, oz, 3, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    ox, cy, oz, 1, 0.05, 0.05, 0.05, 0.0);

            // Damage players that pass through orb
            Box orbBox = new Box(ox - 1.0, cy - 1.0, oz - 1.0, ox + 1.0, cy + 1.5, oz + 1.0);
            List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, orbBox,
                    p -> p.isAlive() && !hitThisTick.contains(p.getUuid()));

            for (PlayerEntity victim : victims) {
                victim.damage(boss.getDamageSources().mobAttack(boss), damage);
                hitThisTick.add(victim.getUuid());

                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        4, 0.25, 0.25, 0.25, 0.06);
                world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 0.8f, 1.4f);
            }
        }

        // Ambient hum every 20 ticks
        if (orbitalTick % 20 == 0) {
            world.playSound(null, cx, cy, cz,
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 0.5f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Dispersal burst when orbitals disappear
            world.spawnParticles(ParticleTypes.END_ROD,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    25, radius * 0.5, 0.6, radius * 0.5, 0.25);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 1.5f);
        }
    }
}
