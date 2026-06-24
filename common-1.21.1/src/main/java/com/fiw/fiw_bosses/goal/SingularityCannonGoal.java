package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

public class SingularityCannonGoal extends Goal {

    private static final int STATE_CHARGE = 0;
    private static final int STATE_BEAM   = 1;
    private static final double SLOW_RANGE = 20.0;

    private final BossEntity boss;
    private final int    chargeTime;
    private final float  damage;
    private final double range;
    private final double beamWidth;
    private final double beamSpeed;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;

    private double ringRotation;
    private double ringRadius;

    private Vec3        beamPos;
    private Vec3        beamDir;
    private double      beamTraveled;
    private Set<UUID>   hitPlayers;

    public SingularityCannonGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()    : 30;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()      : 25f;
        this.range      = params.has("range")      ? params.get("range").getAsDouble()      : 20.0;
        this.beamWidth  = params.has("beamWidth")  ? params.get("beamWidth").getAsDouble()  : 1.2;
        this.beamSpeed  = params.has("beamSpeed")  ? params.get("beamSpeed").getAsDouble()  : 3.0;
        this.cooldown   = cooldownTicks;
        this.hitPlayers = new HashSet<>();
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
        tick          = 0;
        state         = STATE_CHARGE;
        active        = true;
        ringRotation  = 0.0;
        ringRadius    = 0.5;
        beamPos       = null;
        beamDir       = null;
        beamTraveled  = 0.0;
        hitPlayers    = new HashSet<>();
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
            case STATE_CHARGE -> tickCharge(level);
            case STATE_BEAM   -> tickBeam(level);
        }
    }

    private void tickCharge(ServerLevel level) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 360, 90);
        }

        double progress = (double) tick / chargeTime;
        ringRadius   = 0.5 + progress * 2.5;
        ringRotation += 15.0 + progress * 20.0;

        Vec3 facing  = boss.getLookAngle().normalize();
        Vec3 up      = new Vec3(0, 1, 0);
        Vec3 right   = facing.cross(up).normalize();
        Vec3 realUp  = right.cross(facing).normalize();

        Vec3 ringCenter = boss.position().add(0, 1.2, 0).add(facing.scale(2.0));

        int ringPoints = Math.max(12, (int)(ringRadius * 10));
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(ringRotation + (double) i * (360.0 / ringPoints));
            Vec3 point = ringCenter
                    .add(right.scale(ringRadius * Math.cos(angle)))
                    .add(realUp.scale(ringRadius * Math.sin(angle)));
            level.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.05);
            }
        }

        double innerRadius = ringRadius * 0.5;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(-ringRotation * 1.5 + i * 45.0);
            Vec3 point = ringCenter
                    .add(right.scale(innerRadius * Math.cos(angle)))
                    .add(realUp.scale(innerRadius * Math.sin(angle)));
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }

        double chromaRadius = ringRadius * 1.1;
        float[][] colors = {
            {1f, 0f, 0f},
            {0f, 1f, 0f},
            {0f, 0f, 1f},
            {1f, 1f, 0f},
            {0f, 1f, 1f},
            {1f, 0f, 1f}
        };
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(ringRotation * 0.5 + i * 60.0);
            Vec3 point = ringCenter
                    .add(right.scale(chromaRadius * Math.cos(angle)))
                    .add(realUp.scale(chromaRadius * Math.sin(angle)));
            DustParticleOptions dust = new DustParticleOptions(
                    Colors.rgb(colors[i][0], colors[i][1], colors[i][2]), 1.0f);
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }

        if (tick % 10 == 0) {
            List<Player> nearby = level.getEntitiesOfClass(Player.class,
                    boss.getBoundingBox().inflate(SLOW_RANGE),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && boss.distanceToSqr(p) <= SLOW_RANGE * SLOW_RANGE);
            for (Player p : nearby) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 1, false, false));
            }
        }

        if (tick % 8 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE,
                    0.5f, (float)(1.5 + progress));
        }

        if (tick >= chargeTime) {
            Vec3 lockedFacing;
            if (target != null && target.isAlive()) {
                lockedFacing = target.position().add(0, 1, 0)
                        .subtract(boss.position().add(0, 1.2, 0))
                        .normalize();
            } else {
                lockedFacing = boss.getLookAngle().normalize();
            }
            beamPos      = boss.position().add(0, 1.2, 0);
            beamDir      = lockedFacing;
            beamTraveled = 0.0;
            hitPlayers.clear();

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.8f);

            state = STATE_BEAM;
        }
    }

    private void tickBeam(ServerLevel level) {
        int    steps    = Math.max(1, (int) Math.ceil(beamSpeed));
        double stepDist = beamSpeed / steps;

        for (int s = 0; s < steps; s++) {
            beamPos      = beamPos.add(beamDir.scale(stepDist));
            beamTraveled += stepDist;

            level.sendParticles(ParticleTypes.END_ROD,
                    beamPos.x, beamPos.y, beamPos.z,
                    4, beamWidth * 0.2, beamWidth * 0.2, beamWidth * 0.2, 0.03);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    beamPos.x, beamPos.y, beamPos.z,
                    3, beamWidth * 0.3, beamWidth * 0.3, beamWidth * 0.3, 0.1);
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    beamPos.x, beamPos.y, beamPos.z, 1, 0, 0, 0, 0);

            AABB hitBox = new AABB(
                    beamPos.x - beamWidth, beamPos.y - beamWidth, beamPos.z - beamWidth,
                    beamPos.x + beamWidth, beamPos.y + beamWidth, beamPos.z + beamWidth);
            List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                    p -> p.isAlive() && !hitPlayers.contains(p.getUUID()));

            for (Player player : victims) {
                if (hitPlayers.contains(player.getUUID())) continue;
                hitPlayers.add(player.getUUID());
                player.hurt(boss.damageSources().magic(), damage);
                Vec3 drag = beamDir.scale(2.0);
                player.push(drag.x, drag.y * 0.3, drag.z);
                player.hurtMarked = true;
            }

            if (beamTraveled >= range) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        beamPos.x, beamPos.y, beamPos.z, 2, 0.5, 0.5, 0.5, 0);
                level.sendParticles(ParticleTypes.END_ROD,
                        beamPos.x, beamPos.y, beamPos.z, 40, 1.0, 1.0, 1.0, 0.3);
                level.playSound(null, beamPos.x, beamPos.y, beamPos.z,
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.4f);
                active = false;
                return;
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }
}
