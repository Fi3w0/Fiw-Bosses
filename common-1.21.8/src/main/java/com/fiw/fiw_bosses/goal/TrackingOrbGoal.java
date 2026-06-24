package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TrackingOrbGoal extends Goal {

    private static final DustParticleOptions DUST_PURPLE =
            new DustParticleOptions(Colors.rgb(0.6f, 0.1f, 0.9f), 1.2f);

    private static class OrbProjectile {
        Vec3 pos;
        Vec3 dir;
        double traveled;
        boolean done;

        OrbProjectile(Vec3 pos, Vec3 dir) {
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
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
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
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        orbAngle += 3.0;
        double orbRad = Math.toRadians(orbAngle);

        Vec3 orbCenter = boss.position().add(
                Math.cos(orbRad) * orbRadius,
                1.5 + Math.sin(Math.toRadians(orbAngle * 0.7)) * 0.4,
                Math.sin(orbRad) * orbRadius
        );

        level.sendParticles(DUST_PURPLE,
                orbCenter.x, orbCenter.y, orbCenter.z,
                2, 0.12, 0.12, 0.12, 0.0);

        if (tick % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60.0 + orbAngle * 2.0);
                double rx = orbCenter.x + Math.cos(a) * 0.4;
                double rz = orbCenter.z + Math.sin(a) * 0.4;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        rx, orbCenter.y, rz, 1, 0, 0, 0, 0);
            }
        }

        if (tick % 5 == 0) {
            level.sendParticles(ParticleTypes.WITCH,
                    orbCenter.x, orbCenter.y, orbCenter.z,
                    3, 0.2, 0.2, 0.2, 0.05);
        }

        if (tick % fireRate == 0) {
            Player closest = null;
            double closestDist   = 25.0 * 25.0;

            List<Player> candidates = level.getEntitiesOfClass(Player.class,
                    new AABB(boss.getX() - 25, boss.getY() - 10, boss.getZ() - 25,
                            boss.getX() + 25, boss.getY() + 10, boss.getZ() + 25),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

            for (Player p : candidates) {
                double d = p.distanceToSqr(boss);
                if (d < closestDist) {
                    closestDist = d;
                    closest     = p;
                }
            }

            if (closest != null) {
                Vec3 dir = closest.position().add(0, 1, 0)
                        .subtract(orbCenter)
                        .normalize();
                projectiles.add(new OrbProjectile(orbCenter, dir));
                level.playSound(null, orbCenter.x, orbCenter.y, orbCenter.z,
                        SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.7f, 1.3f);
            }
        }

        for (OrbProjectile proj : projectiles) {
            if (proj.done) continue;

            proj.pos      = proj.pos.add(proj.dir.scale(projectileSpeed));
            proj.traveled += projectileSpeed;

            level.sendParticles(DUST_PURPLE,
                    proj.pos.x, proj.pos.y, proj.pos.z,
                    2, 0.08, 0.08, 0.08, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    proj.pos.x, proj.pos.y, proj.pos.z,
                    1, 0, 0, 0, 0);

            AABB hitBox = new AABB(
                    proj.pos.x - 0.6, proj.pos.y - 0.6, proj.pos.z - 0.6,
                    proj.pos.x + 0.6, proj.pos.y + 0.6, proj.pos.z + 0.6);
            List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

            if (!victims.isEmpty()) {
                Player hit = victims.get(0);
                hit.hurtServer(level, boss.damageSources().magic(), (float) damage);
                level.sendParticles(ParticleTypes.WITCH,
                        proj.pos.x, proj.pos.y, proj.pos.z,
                        6, 0.2, 0.2, 0.2, 0.1);
                proj.done = true;
                continue;
            }

            if (proj.traveled >= projectileRange) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        proj.pos.x, proj.pos.y, proj.pos.z,
                        3, 0.2, 0.2, 0.2, 0.05);
                proj.done = true;
            }
        }

        projectiles.removeIf(p -> p.done);

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
