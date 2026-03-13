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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TrackingOrbGoal extends Goal {

    private static final DustParticleEffect DUST_PURPLE =
            new DustParticleEffect(new Vector3f(0.6f, 0.1f, 0.9f), 1.2f);

    private static class OrbProjectile {
        Vec3d pos;
        Vec3d dir;
        double traveled;
        boolean done;

        OrbProjectile(Vec3d pos, Vec3d dir) {
            this.pos      = pos;
            this.dir      = dir;
            this.traveled = 0.0;
            this.done     = false;
        }
    }

    private final BossEntity boss;
    private final int    duration;
    private final int    fireRate;
    private final double damage;
    private final double projectileSpeed;
    private final double projectileRange;
    private final double orbRadius;
    private final int    cooldown;

    private int     cooldownTimer;
    private double  orbAngle;
    private int     tick;
    private boolean active;
    private final List<OrbProjectile> projectiles = new ArrayList<>();

    public TrackingOrbGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.duration        = params.has("duration")        ? params.get("duration").getAsInt()           : 220;
        this.fireRate        = params.has("fireRate")        ? params.get("fireRate").getAsInt()           : 35;
        this.damage          = params.has("damage")          ? params.get("damage").getAsDouble()          : 10.0;
        this.projectileSpeed = params.has("projectileSpeed") ? params.get("projectileSpeed").getAsDouble() : 1.6;
        this.projectileRange = params.has("projectileRange") ? params.get("projectileRange").getAsDouble() : 22.0;
        this.orbRadius       = params.has("orbRadius")       ? params.get("orbRadius").getAsDouble()       : 2.0;
        this.cooldown        = cooldownTicks;
        this.cooldownTimer   = 0;
        // No controls — runs alongside other goals
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
        tick     = 0;
        active   = true;
        orbAngle = 0.0;
        projectiles.clear();
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

        orbAngle += 3.0;
        double orbRad = Math.toRadians(orbAngle);

        // Figure-8 orbit — orbCenter moves in a figure-8 around the boss
        Vec3d orbCenter = boss.getPos().add(
                Math.cos(orbRad) * orbRadius,
                1.5 + Math.sin(Math.toRadians(orbAngle * 0.7)) * 0.4,
                Math.sin(orbRad) * orbRadius
        );

        // Orb visuals — 2 purple dust particles
        world.spawnParticles(DUST_PURPLE,
                orbCenter.x, orbCenter.y, orbCenter.z,
                2, 0.12, 0.12, 0.12, 0.0);

        // Every other tick — small REVERSE_PORTAL ring
        if (tick % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60.0 + orbAngle * 2.0);
                double rx = orbCenter.x + Math.cos(a) * 0.4;
                double rz = orbCenter.z + Math.sin(a) * 0.4;
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        rx, orbCenter.y, rz, 1, 0, 0, 0, 0);
            }
        }

        // Every 5 ticks — 3 WITCH ambient
        if (tick % 5 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    orbCenter.x, orbCenter.y, orbCenter.z,
                    3, 0.2, 0.2, 0.2, 0.05);
        }

        // Fire projectile every fireRate ticks
        if (tick % fireRate == 0) {
            PlayerEntity closest = null;
            double closestDist   = 25.0 * 25.0;

            List<PlayerEntity> candidates = world.getEntitiesByClass(PlayerEntity.class,
                    new Box(boss.getX() - 25, boss.getY() - 10, boss.getZ() - 25,
                            boss.getX() + 25, boss.getY() + 10, boss.getZ() + 25),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

            for (PlayerEntity p : candidates) {
                double d = p.squaredDistanceTo(boss);
                if (d < closestDist) {
                    closestDist = d;
                    closest     = p;
                }
            }

            if (closest != null) {
                Vec3d dir = closest.getPos().add(0, 1, 0)
                        .subtract(orbCenter)
                        .normalize();
                projectiles.add(new OrbProjectile(orbCenter, dir));
                world.playSound(null, orbCenter.x, orbCenter.y, orbCenter.z,
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.7f, 1.3f);
            }
        }

        // Tick all active projectiles
        for (OrbProjectile proj : projectiles) {
            if (proj.done) continue;

            proj.pos      = proj.pos.add(proj.dir.multiply(projectileSpeed));
            proj.traveled += projectileSpeed;

            // Projectile visuals
            world.spawnParticles(DUST_PURPLE,
                    proj.pos.x, proj.pos.y, proj.pos.z,
                    2, 0.08, 0.08, 0.08, 0.0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    proj.pos.x, proj.pos.y, proj.pos.z,
                    1, 0, 0, 0, 0);

            // Hit detection — box 0.6 radius
            Box hitBox = new Box(
                    proj.pos.x - 0.6, proj.pos.y - 0.6, proj.pos.z - 0.6,
                    proj.pos.x + 0.6, proj.pos.y + 0.6, proj.pos.z + 0.6);
            List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

            if (!victims.isEmpty()) {
                PlayerEntity hit = victims.get(0);
                hit.damage(boss.getDamageSources().magic(), (float) damage);
                world.spawnParticles(ParticleTypes.WITCH,
                        proj.pos.x, proj.pos.y, proj.pos.z,
                        6, 0.2, 0.2, 0.2, 0.1);
                proj.done = true;
                continue;
            }

            // Range check
            if (proj.traveled >= projectileRange) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        proj.pos.x, proj.pos.y, proj.pos.z,
                        3, 0.2, 0.2, 0.2, 0.05);
                proj.done = true;
            }
        }

        // Remove finished projectiles
        projectiles.removeIf(p -> p.done);

        // Duration check
        if (tick >= duration) {
            active = false;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
        projectiles.clear();
    }
}
