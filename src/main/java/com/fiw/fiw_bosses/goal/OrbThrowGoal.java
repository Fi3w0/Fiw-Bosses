package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrbThrowGoal extends Goal {

    private static final int STATE_ORBIT = 0;
    private static final int STATE_FLY   = 1;

    private final BossEntity boss;
    private final int    orbitTime;
    private final double speed;
    private final double range;
    private final double knockback;
    private final double explosionRadius;
    private final double damage;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;

    private double       orbAngle;
    private Vec3d        orbPos;
    private Vec3d        flyDir;
    private double       traveled;
    private Set<UUID>    hitPlayers;

    private static final DustParticleEffect DUST_GREEN  =
            new DustParticleEffect(new Vector3f(0.2f, 1.0f, 0.3f), 1.2f);
    private static final DustParticleEffect DUST_YELLOW =
            new DustParticleEffect(new Vector3f(0.9f, 0.9f, 0.1f), 1.0f);

    public OrbThrowGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss           = boss;
        this.orbitTime      = params.has("orbitTime")       ? params.get("orbitTime").getAsInt()         : 50;
        this.speed          = params.has("speed")           ? params.get("speed").getAsDouble()          : 1.5;
        this.range          = params.has("range")           ? params.get("range").getAsDouble()          : 22.0;
        this.knockback      = params.has("knockback")       ? params.get("knockback").getAsDouble()      : 3.5;
        this.explosionRadius= params.has("explosionRadius") ? params.get("explosionRadius").getAsDouble(): 6.0;
        this.damage         = params.has("damage")          ? params.get("damage").getAsDouble()         : 12.0;
        this.cooldown       = cooldownTicks;
        this.cooldownTimer  = 0;
        this.hitPlayers     = new HashSet<>();
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
        state    = STATE_ORBIT;
        active   = true;
        orbAngle = 0.0;
        traveled = 0.0;
        orbPos   = boss.getPos().add(0, 1.2, 0);
        flyDir   = null;
        hitPlayers.clear();
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

        switch (state) {
            case STATE_ORBIT -> tickOrbit(world);
            case STATE_FLY   -> tickFly(world);
        }
    }

    private void tickOrbit(ServerWorld world) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }

        orbAngle += 4.5;
        double rad = Math.toRadians(orbAngle);

        Vec3d orbCenter = boss.getPos()
                .add(0, 1.2, 0)
                .add(Math.cos(rad) * 2.2, 0, Math.sin(rad) * 2.2);

        // Orb core — 3 green dust particles
        world.spawnParticles(DUST_GREEN,
                orbCenter.x, orbCenter.y, orbCenter.z,
                3, 0.15, 0.15, 0.15, 0.0);

        // Ring 1 — horizontal circle, 12 END_ROD points, radius 0.6
        double ring1Offset = Math.toRadians(orbAngle * 2.0);
        for (int i = 0; i < 12; i++) {
            double a = ring1Offset + Math.toRadians(i * 30.0);
            double rx = orbCenter.x + Math.cos(a) * 0.6;
            double rz = orbCenter.z + Math.sin(a) * 0.6;
            world.spawnParticles(ParticleTypes.END_ROD,
                    rx, orbCenter.y, rz, 1, 0, 0, 0, 0);
        }

        // Ring 2 — vertical circle, 8 ENCHANT points, radius 0.5
        double ring2Offset = Math.toRadians(orbAngle * 1.5);
        for (int i = 0; i < 8; i++) {
            double a = ring2Offset + Math.toRadians(i * 45.0);
            double ry = orbCenter.y + Math.cos(a) * 0.5;
            double rz = orbCenter.z + Math.sin(a) * 0.5;
            world.spawnParticles(ParticleTypes.ENCHANT,
                    orbCenter.x, ry, rz, 1, 0, 0, 0, 0);
        }

        // Ring 3 — tilted 45° yellow dust, 8 points, radius 0.7
        // Using spherical coordinates: phi = 45° tilt
        double phi = Math.toRadians(45.0);
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double ring3Offset = Math.toRadians(-orbAngle);
        for (int i = 0; i < 8; i++) {
            double theta = ring3Offset + Math.toRadians(i * 45.0);
            double rx = orbCenter.x + 0.7 * sinPhi * Math.cos(theta);
            double ry = orbCenter.y + 0.7 * sinPhi * Math.sin(theta);
            double rz = orbCenter.z + 0.7 * cosPhi;
            world.spawnParticles(DUST_YELLOW, rx, ry, rz, 1, 0, 0, 0, 0);
        }

        // Every 10 ticks: 5 WITCH particles
        if (tick % 10 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    orbCenter.x, orbCenter.y, orbCenter.z,
                    5, 0.3, 0.3, 0.3, 0.1);
        }

        // Transition to fly state
        if (tick >= orbitTime) {
            if (target != null && target.isAlive()) {
                flyDir = target.getPos().add(0, 1, 0)
                        .subtract(boss.getPos().add(0, 1.2, 0))
                        .normalize();
            } else {
                flyDir = boss.getRotationVector().normalize();
            }
            orbPos = boss.getPos().add(0, 1.2, 0);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_ENDER_EYE_LAUNCH, SoundCategory.HOSTILE, 1.5f, 0.8f);
            state = STATE_FLY;
        }
    }

    private void tickFly(ServerWorld world) {
        int    steps    = Math.max(1, (int) Math.ceil(speed));
        double stepDist = speed / steps;

        for (int s = 0; s < steps; s++) {
            orbPos    = orbPos.add(flyDir.multiply(stepDist));
            traveled += stepDist;

            // Trail particles
            world.spawnParticles(DUST_GREEN,
                    orbPos.x, orbPos.y, orbPos.z,
                    3, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD,
                    orbPos.x, orbPos.y, orbPos.z,
                    2, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.ENCHANT,
                    orbPos.x, orbPos.y, orbPos.z,
                    2, 0.15, 0.15, 0.15, 0.08);

            // Spin trail ring — 6 END_ROD points, radius 0.4
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(traveled * 60.0 + i * 60.0);
                double rx = orbPos.x + Math.cos(a) * 0.4;
                double rz = orbPos.z + Math.sin(a) * 0.4;
                world.spawnParticles(ParticleTypes.END_ROD,
                        rx, orbPos.y, rz, 1, 0, 0, 0, 0);
            }

            // Hit detection — box 1.2 radius around orbPos
            Box hitBox = new Box(
                    orbPos.x - 1.2, orbPos.y - 1.2, orbPos.z - 1.2,
                    orbPos.x + 1.2, orbPos.y + 1.2, orbPos.z + 1.2);
            List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && !hitPlayers.contains(p.getUuid()));

            if (!victims.isEmpty()) {
                for (PlayerEntity player : victims) {
                    hitPlayers.add(player.getUuid());
                }
                explode(world, orbPos);
                active = false;
                return;
            }

            // Range check
            if (traveled >= range) {
                explode(world, orbPos);
                active = false;
                return;
            }
        }
    }

    private void explode(ServerWorld world, Vec3d pos) {
        // Visual burst
        world.spawnParticles(DUST_GREEN,
                pos.x, pos.y, pos.z,
                3, explosionRadius * 0.5, explosionRadius * 0.5, explosionRadius * 0.5, 0.0);
        world.spawnParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                80, explosionRadius * 0.4, explosionRadius * 0.4, explosionRadius * 0.4, 0.2);
        world.spawnParticles(ParticleTypes.END_ROD,
                pos.x, pos.y, pos.z,
                30, explosionRadius * 0.3, explosionRadius * 0.3, explosionRadius * 0.3, 0.15);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z,
                2, 0.3, 0.3, 0.3, 0.0);

        // Expanding ring — 32 WITCH points in circle of radius explosionRadius
        for (int i = 0; i < 32; i++) {
            double a = Math.toRadians(i * (360.0 / 32.0));
            double rx = pos.x + Math.cos(a) * explosionRadius;
            double rz = pos.z + Math.sin(a) * explosionRadius;
            world.spawnParticles(ParticleTypes.WITCH,
                    rx, pos.y, rz, 1, 0, 0, 0, 0);
        }

        // Sounds
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.9f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.HOSTILE, 1.5f, 0.7f);

        // Damage and knockback
        double radiusSq = explosionRadius * explosionRadius;
        List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class,
                new Box(pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                        pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.squaredDistanceTo(pos.x, pos.y, pos.z) <= radiusSq);

        for (PlayerEntity player : nearby) {
            // Compute actual distance to explosion center
            double dx = player.getX() - pos.x;
            double dy = player.getY() - pos.y;
            double dz = player.getZ() - pos.z;
            double actualDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double factor = 1.0 - (actualDist / explosionRadius);
            if (factor <= 0) continue;

            player.damage(boss.getDamageSources().magic(), (float)(damage * factor));

            // Knockback direction
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                double kx = (dx / len) * knockback * factor;
                double kz = (dz / len) * knockback * factor;
                player.addVelocity(kx, 0.6 * factor, kz);
                player.velocityModified = true;
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }
}
