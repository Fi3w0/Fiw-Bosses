package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MovingTornadoGoal extends Goal {

    private static final int PHASE_WINDUP = 0;
    private static final int PHASE_ACTIVE = 1;

    private final BossEntity boss;
    private final double radius;
    private final double height;
    private final double speed;
    private final int    duration;
    private final double damage;
    private final double pullStrength;
    private final int    windupTicks;
    private final int    cooldown;

    private int     cooldownTimer;
    private Vec3d   tornadoPos;
    private Vec3d   tornadoDir;
    private int     tick;
    private boolean active;
    private int     phase;
    private double  spinAngle;
    private final Set<UUID> absorbedPlayers = new HashSet<>();

    public MovingTornadoGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss         = boss;
        this.radius       = params.has("radius")       ? params.get("radius").getAsDouble()       : 4.0;
        this.height       = params.has("height")       ? params.get("height").getAsDouble()       : 8.0;
        this.speed        = params.has("speed")        ? params.get("speed").getAsDouble()        : 0.4;
        this.duration     = params.has("duration")     ? params.get("duration").getAsInt()        : 180;
        this.damage       = params.has("damage")       ? params.get("damage").getAsDouble()       : 2.0;
        this.pullStrength = params.has("pullStrength") ? params.get("pullStrength").getAsDouble() : 0.8;
        this.windupTicks  = params.has("windupTicks")  ? params.get("windupTicks").getAsInt()     : 20;
        this.cooldown     = cooldownTicks;
        this.cooldownTimer= 0;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick     = 0;
        active   = true;
        phase    = PHASE_WINDUP;
        spinAngle= 0.0;
        absorbedPlayers.clear();
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        spinAngle += 8.0;

        if (phase == PHASE_WINDUP) {
            tickWindup(world);
        } else {
            tickActive(world);
        }
    }

    private void tickWindup(ServerWorld world) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }

        Vec3d targetPos = (target != null && target.isAlive())
                ? target.getPos()
                : boss.getPos().add(boss.getRotationVector().multiply(6.0));

        // Warning swirl at target ground area — 8 WARPED_SPORE in fast-spinning circle
        double warnRad = Math.toRadians(spinAngle * 3.0); // fast spin for warning
        for (int i = 0; i < 8; i++) {
            double a = warnRad + Math.toRadians(i * 45.0);
            double wx = targetPos.x + Math.cos(a) * 2.0;
            double wz = targetPos.z + Math.sin(a) * 2.0;
            world.spawnParticles(ParticleTypes.WARPED_SPORE,
                    wx, targetPos.y + 0.1, wz, 1, 0, 0, 0, 0);
        }

        // Transition at end of windup
        if (tick == windupTicks) {
            tornadoPos = targetPos.add(0, 0.5, 0);

            // Compute a flat direction from boss to target
            double dx = targetPos.x - boss.getX();
            double dz = targetPos.z - boss.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                tornadoDir = new Vec3d(dx / len, 0, dz / len);
            } else {
                Vec3d facing = boss.getRotationVector();
                double fLen  = Math.sqrt(facing.x * facing.x + facing.z * facing.z);
                tornadoDir   = fLen > 0.001
                        ? new Vec3d(facing.x / fLen, 0, facing.z / fLen)
                        : new Vec3d(1, 0, 0);
            }

            world.playSound(null, tornadoPos.x, tornadoPos.y, tornadoPos.z,
                    SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.HOSTILE, 1.5f, 0.4f);
            phase = PHASE_ACTIVE;
        }
    }

    private void tickActive(ServerWorld world) {
        int activeTicks = tick - windupTicks;
        if (activeTicks >= duration) {
            active = false;
            return;
        }

        // Advance tornado
        tornadoPos = tornadoPos.add(tornadoDir.multiply(speed));

        // Funnel visuals — narrow at base, wide at top
        for (double h = 0; h <= height; h += 0.6) {
            double widthAtH = radius * (h / height);
            int    points   = Math.max(4, (int)(widthAtH * 6));

            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians(spinAngle * (1.0 + h * 0.1) + i * (360.0 / points));
                double px = tornadoPos.x + widthAtH * Math.cos(angle);
                double py = tornadoPos.y + h;
                double pz = tornadoPos.z + widthAtH * Math.sin(angle);

                if (tick % 2 == 0) {
                    world.spawnParticles(ParticleTypes.WARPED_SPORE,
                            px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
                }
                if (tick % 4 == 0 && ((int)(h / 0.6)) % 2 == 0) {
                    world.spawnParticles(ParticleTypes.PORTAL,
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        // Central flame column every 3 ticks
        if (tick % 3 == 0) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    tornadoPos.x, tornadoPos.y + height * 0.5, tornadoPos.z,
                    5, radius * 0.3, radius * 0.3, radius * 0.3, 0.0);
        }

        // Player absorption
        double doubleRadius = radius * 2.0;
        Box absorbBox = new Box(
                tornadoPos.x - doubleRadius, tornadoPos.y,       tornadoPos.z - doubleRadius,
                tornadoPos.x + doubleRadius, tornadoPos.y + height, tornadoPos.z + doubleRadius);

        List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class, absorbBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (PlayerEntity player : nearby) {
            double dx   = player.getX() - tornadoPos.x;
            double dz   = player.getZ() - tornadoPos.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist <= radius) {
                // Pull toward tornado center
                double len = dist > 0.001 ? dist : 0.001;
                Vec3d toward = new Vec3d(-dx / len, 0, -dz / len);
                player.addVelocity(toward.x * pullStrength, 0.25, toward.z * pullStrength);
                player.velocityModified = true;

                // Inner vortex — extra upward lift
                if (dist <= radius * 0.5) {
                    player.addVelocity(0, 0.15, 0);
                    player.velocityModified = true;
                }

                // Damage every 20 ticks
                if (tick % 20 == 0) {
                    player.damage(boss.getDamageSources().magic(), (float) damage);
                    absorbedPlayers.add(player.getUuid());
                }
            }
        }

        // Wind sound every 30 ticks
        if (tick % 30 == 0) {
            world.playSound(null, tornadoPos.x, tornadoPos.y, tornadoPos.z,
                    SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }
}
