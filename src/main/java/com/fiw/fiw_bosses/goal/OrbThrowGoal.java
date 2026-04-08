package com.fiw.fiw_bosses.goal;

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
    private Vec3         orbPos;
    private Vec3         flyDir;
    private double       traveled;
    private Set<UUID>    hitPlayers;

    private static final DustParticleOptions DUST_GREEN  =
            new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.3f), 1.2f);
    private static final DustParticleOptions DUST_YELLOW =
            new DustParticleOptions(new Vector3f(0.9f, 0.9f, 0.1f), 1.0f);

    public OrbThrowGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss           = boss;
        this.orbitTime      = params.has("orbitTime")       ? params.get("orbitTime").getAsInt()         : 50;
        this.speed          = params.has("speed")           ? params.get("speed").getAsDouble()          : 1.5;
        this.range          = params.has("range")           ? params.get("range").getAsDouble()          : 22.0;
        this.knockback      = params.has("knockback")       ? params.get("knockback").getAsDouble()      : 3.5;
        this.explosionRadius= params.has("explosionRadius") ? params.get("explosionRadius").getAsDouble(): 6.0;
        this.damage         = params.has("damage")          ? params.get("damage").getAsDouble()         : 12.0;
        this.cooldown       = cooldownTicks;
        this.hitPlayers     = new HashSet<>();
        this.setFlags(EnumSet.of(Flag.LOOK));
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
        state    = STATE_ORBIT;
        active   = true;
        orbAngle = 0.0;
        traveled = 0.0;
        orbPos   = boss.position().add(0, 1.2, 0);
        flyDir   = null;
        hitPlayers.clear();
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        switch (state) {
            case STATE_ORBIT -> tickOrbit(level);
            case STATE_FLY   -> tickFly(level);
        }
    }

    private void tickOrbit(ServerLevel level) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 360, 90);
        }

        orbAngle += 4.5;
        double rad = Math.toRadians(orbAngle);

        Vec3 orbCenter = boss.position()
                .add(0, 1.2, 0)
                .add(Math.cos(rad) * 2.2, 0, Math.sin(rad) * 2.2);

        level.sendParticles(DUST_GREEN,
                orbCenter.x, orbCenter.y, orbCenter.z,
                3, 0.15, 0.15, 0.15, 0.0);

        double ring1Offset = Math.toRadians(orbAngle * 2.0);
        for (int i = 0; i < 12; i++) {
            double a = ring1Offset + Math.toRadians(i * 30.0);
            double rx = orbCenter.x + Math.cos(a) * 0.6;
            double rz = orbCenter.z + Math.sin(a) * 0.6;
            level.sendParticles(ParticleTypes.END_ROD,
                    rx, orbCenter.y, rz, 1, 0, 0, 0, 0);
        }

        double ring2Offset = Math.toRadians(orbAngle * 1.5);
        for (int i = 0; i < 8; i++) {
            double a = ring2Offset + Math.toRadians(i * 45.0);
            double ry = orbCenter.y + Math.cos(a) * 0.5;
            double rz = orbCenter.z + Math.sin(a) * 0.5;
            level.sendParticles(ParticleTypes.ENCHANT,
                    orbCenter.x, ry, rz, 1, 0, 0, 0, 0);
        }

        double phi = Math.toRadians(45.0);
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double ring3Offset = Math.toRadians(-orbAngle);
        for (int i = 0; i < 8; i++) {
            double theta = ring3Offset + Math.toRadians(i * 45.0);
            double rx = orbCenter.x + 0.7 * sinPhi * Math.cos(theta);
            double ry = orbCenter.y + 0.7 * sinPhi * Math.sin(theta);
            double rz = orbCenter.z + 0.7 * cosPhi;
            level.sendParticles(DUST_YELLOW, rx, ry, rz, 1, 0, 0, 0, 0);
        }

        if (tick % 10 == 0) {
            level.sendParticles(ParticleTypes.WITCH,
                    orbCenter.x, orbCenter.y, orbCenter.z,
                    5, 0.3, 0.3, 0.3, 0.1);
        }

        if (tick >= orbitTime) {
            if (target != null && target.isAlive()) {
                flyDir = target.position().add(0, 1, 0)
                        .subtract(boss.position().add(0, 1.2, 0))
                        .normalize();
            } else {
                flyDir = boss.getLookAngle().normalize();
            }
            orbPos = boss.position().add(0, 1.2, 0);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_EYE_LAUNCH, SoundSource.HOSTILE, 1.5f, 0.8f);
            state = STATE_FLY;
        }
    }

    private void tickFly(ServerLevel level) {
        int    steps    = Math.max(1, (int) Math.ceil(speed));
        double stepDist = speed / steps;

        for (int s = 0; s < steps; s++) {
            orbPos    = orbPos.add(flyDir.scale(stepDist));
            traveled += stepDist;

            level.sendParticles(DUST_GREEN,
                    orbPos.x, orbPos.y, orbPos.z,
                    3, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.END_ROD,
                    orbPos.x, orbPos.y, orbPos.z,
                    2, 0.1, 0.1, 0.1, 0.05);
            level.sendParticles(ParticleTypes.ENCHANT,
                    orbPos.x, orbPos.y, orbPos.z,
                    2, 0.15, 0.15, 0.15, 0.08);

            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(traveled * 60.0 + i * 60.0);
                double rx = orbPos.x + Math.cos(a) * 0.4;
                double rz = orbPos.z + Math.sin(a) * 0.4;
                level.sendParticles(ParticleTypes.END_ROD,
                        rx, orbPos.y, rz, 1, 0, 0, 0, 0);
            }

            AABB hitBox = new AABB(
                    orbPos.x - 1.2, orbPos.y - 1.2, orbPos.z - 1.2,
                    orbPos.x + 1.2, orbPos.y + 1.2, orbPos.z + 1.2);
            List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && !hitPlayers.contains(p.getUUID()));

            if (!victims.isEmpty()) {
                for (Player player : victims) {
                    hitPlayers.add(player.getUUID());
                }
                explode(level, orbPos);
                active = false;
                return;
            }

            if (traveled >= range) {
                explode(level, orbPos);
                active = false;
                return;
            }
        }
    }

    private void explode(ServerLevel level, Vec3 pos) {
        level.sendParticles(DUST_GREEN,
                pos.x, pos.y, pos.z,
                3, explosionRadius * 0.5, explosionRadius * 0.5, explosionRadius * 0.5, 0.0);
        level.sendParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                80, explosionRadius * 0.4, explosionRadius * 0.4, explosionRadius * 0.4, 0.2);
        level.sendParticles(ParticleTypes.END_ROD,
                pos.x, pos.y, pos.z,
                30, explosionRadius * 0.3, explosionRadius * 0.3, explosionRadius * 0.3, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z,
                2, 0.3, 0.3, 0.3, 0.0);

        for (int i = 0; i < 32; i++) {
            double a = Math.toRadians(i * (360.0 / 32.0));
            double rx = pos.x + Math.cos(a) * explosionRadius;
            double rz = pos.z + Math.sin(a) * explosionRadius;
            level.sendParticles(ParticleTypes.WITCH,
                    rx, pos.y, rz, 1, 0, 0, 0, 0);
        }

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.9f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.5f, 0.7f);

        double radiusSq = explosionRadius * explosionRadius;
        List<Player> nearby = level.getEntitiesOfClass(Player.class,
                new AABB(pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                        pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.distanceToSqr(pos.x, pos.y, pos.z) <= radiusSq);

        for (Player player : nearby) {
            double dx = player.getX() - pos.x;
            double dy = player.getY() - pos.y;
            double dz = player.getZ() - pos.z;
            double actualDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double factor = 1.0 - (actualDist / explosionRadius);
            if (factor <= 0) continue;

            player.hurt(boss.damageSources().magic(), (float)(damage * factor));

            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                double kx = (dx / len) * knockback * factor;
                double kz = (dz / len) * knockback * factor;
                player.push(kx, 0.6 * factor, kz);
                player.hasImpulse = true;
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }
}
