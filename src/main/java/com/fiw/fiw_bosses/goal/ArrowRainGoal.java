package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * Boss marks a circular area with a warning ring, then a rain of actual ArrowEntity
 * projectiles falls from above within the radius.
 *
 * JSON params:
 *   radius       (double, 8.0)   radius of the rain area
 *   arrowCount   (int,   20)     total arrows spawned per volley
 *   height       (double, 20.0)  height above ground from which arrows fall
 *   damage       (float,  8.0)   base arrow damage
 *   warnTicks    (int,   40)     warning phase duration
 *   rainTicks    (int,   30)     duration of the rain phase (arrows spread over this)
 *   cooldown     via constructor
 */
public class ArrowRainGoal extends Goal {

    private static final int STATE_WARN = 0;
    private static final int STATE_RAIN = 1;

    private final BossEntity boss;
    private final double radius;
    private final int    arrowCount;
    private final double height;
    private final float  damage;
    private final int    warnTicks;
    private final int    rainTicks;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     arrowsSpawned;

    private Vec3d   rainCenter;

    private static final DustParticleEffect DUST_ORANGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.0f), 1.2f);
    private static final DustParticleEffect DUST_RED =
            new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.0f), 1.0f);

    public ArrowRainGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.radius      = params.has("radius")     ? params.get("radius").getAsDouble()    : 8.0;
        this.arrowCount  = params.has("arrowCount") ? params.get("arrowCount").getAsInt()   : 20;
        this.height      = params.has("height")     ? params.get("height").getAsDouble()    : 20.0;
        this.damage      = params.has("damage")     ? params.get("damage").getAsFloat()     : 8.0f;
        this.warnTicks   = params.has("warnTicks")  ? params.get("warnTicks").getAsInt()    : 40;
        this.rainTicks   = params.has("rainTicks")  ? params.get("rainTicks").getAsInt()    : 30;
        this.cooldown    = cooldownTicks;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_WARN;
        active       = true;
        arrowsSpawned = 0;

        LivingEntity target = boss.getTarget();
        rainCenter = (target != null && target.isAlive())
                ? target.getPos()
                : boss.getPos().add(boss.getRotationVector().multiply(radius));
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 30, 30);
        }

        if (state == STATE_WARN) {
            tickWarn(world);
        } else {
            tickRain(world);
        }
    }

    private void tickWarn(ServerWorld world) {
        // Pulsing warning ring on the ground
        double pulse = 0.8 + 0.2 * Math.sin(tick * 0.4);
        int ringPoints = 32;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(360.0 / ringPoints * i + tick * 5.0);
            double px = rainCenter.x + Math.cos(angle) * radius * pulse;
            double pz = rainCenter.z + Math.sin(angle) * radius * pulse;
            world.spawnParticles(DUST_ORANGE,
                    px, rainCenter.y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Inner fill marks every 4 ticks
        if (tick % 4 == 0) {
            int fillCount = 8;
            for (int i = 0; i < fillCount; i++) {
                double angle = world.getRandom().nextDouble() * Math.PI * 2;
                double r     = world.getRandom().nextDouble() * radius;
                double px    = rainCenter.x + Math.cos(angle) * r;
                double pz    = rainCenter.z + Math.sin(angle) * r;
                world.spawnParticles(DUST_RED,
                        px, rainCenter.y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Warning sound
        if (tick == 1) {
            world.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.4f);
        }
        if (tick == warnTicks - 5) {
            world.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }

        if (tick >= warnTicks) {
            state = STATE_RAIN;
            world.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 2.5f, 0.8f);
        }
    }

    private void tickRain(ServerWorld world) {
        int rainElapsed = tick - warnTicks;

        // Spawn arrows spread evenly across rain phase
        if (arrowsSpawned < arrowCount) {
            int arrowsThisTick = (int) Math.ceil(
                    (double)(arrowCount - arrowsSpawned) / Math.max(1, rainTicks - rainElapsed));
            arrowsThisTick = Math.min(arrowsThisTick, arrowCount - arrowsSpawned);

            for (int a = 0; a < arrowsThisTick; a++) {
                double angle = world.getRandom().nextDouble() * Math.PI * 2;
                double r     = world.getRandom().nextDouble() * radius;
                double ax    = rainCenter.x + Math.cos(angle) * r;
                double az    = rainCenter.z + Math.sin(angle) * r;
                double ay    = rainCenter.y + height;

                ArrowEntity arrow = new ArrowEntity(world, ax, ay, az);
                arrow.setOwner(boss);
                arrow.setDamage(damage);
                arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
                arrow.setVelocity(0, -2.5, 0);
                world.spawnEntity(arrow);
                arrowsSpawned++;
            }
        }

        // Ambient falling particles in the area
        if (tick % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.getRandom().nextDouble() * Math.PI * 2;
                double r     = world.getRandom().nextDouble() * radius;
                double px    = rainCenter.x + Math.cos(angle) * r;
                double pz    = rainCenter.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.CRIT,
                        px, rainCenter.y + height * 0.3, pz, 1, 0.1, 0.3, 0.1, 0.5);
            }
        }

        if (rainElapsed >= rainTicks) {
            active = false;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
