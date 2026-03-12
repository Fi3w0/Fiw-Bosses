package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

    // Charge-phase fields
    private double ringRotation;
    private double ringRadius;

    // Beam-phase fields
    private Vec3d        beamPos;
    private Vec3d        beamDir;
    private double       beamTraveled;
    private Vec3d        lockedFacing;
    private Set<UUID>    hitPlayers;

    public SingularityCannonGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()    : 30;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()      : 25f;
        this.range      = params.has("range")      ? params.get("range").getAsDouble()      : 20.0;
        this.beamWidth  = params.has("beamWidth")  ? params.get("beamWidth").getAsDouble()  : 1.2;
        this.beamSpeed  = params.has("beamSpeed")  ? params.get("beamSpeed").getAsDouble()  : 3.0;
        this.cooldown   = cooldownTicks;
        this.cooldownTimer = 0;
        this.hitPlayers = new HashSet<>();
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
        tick          = 0;
        state         = STATE_CHARGE;
        active        = true;
        ringRotation  = 0.0;
        ringRadius    = 0.5;
        beamPos       = null;
        beamDir       = null;
        beamTraveled  = 0.0;
        lockedFacing  = null;
        hitPlayers    = new HashSet<>();
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
            case STATE_CHARGE -> tickCharge(world);
            case STATE_BEAM   -> tickBeam(world);
        }
    }

    private void tickCharge(ServerWorld world) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }

        double progress = (double) tick / chargeTime;
        ringRadius   = 0.5 + progress * 2.5;
        ringRotation += 15.0 + progress * 20.0;

        Vec3d facing  = boss.getRotationVector().normalize();
        Vec3d up      = new Vec3d(0, 1, 0);
        Vec3d right   = facing.crossProduct(up).normalize();
        Vec3d realUp  = right.crossProduct(facing).normalize();

        Vec3d ringCenter = boss.getPos().add(0, 1.2, 0).add(facing.multiply(2.0));

        // Outer ring
        int ringPoints = Math.max(12, (int)(ringRadius * 10));
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(ringRotation + (double) i * (360.0 / ringPoints));
            Vec3d point = ringCenter
                    .add(right.multiply(ringRadius * Math.cos(angle)))
                    .add(realUp.multiply(ringRadius * Math.sin(angle)));
            world.spawnParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.05);
            }
        }

        // Inner counter-rotating ring (8 points, radius * 0.5)
        double innerRadius = ringRadius * 0.5;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(-ringRotation * 1.5 + i * 45.0);
            Vec3d point = ringCenter
                    .add(right.multiply(innerRadius * Math.cos(angle)))
                    .add(realUp.multiply(innerRadius * Math.sin(angle)));
            world.spawnParticles(ParticleTypes.SONIC_BOOM,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }

        // Chromatic aberration ring (6 points, radius * 1.1)
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
            Vec3d point = ringCenter
                    .add(right.multiply(chromaRadius * Math.cos(angle)))
                    .add(realUp.multiply(chromaRadius * Math.sin(angle)));
            DustParticleEffect dust = new DustParticleEffect(
                    new Vector3f(colors[i][0], colors[i][1], colors[i][2]), 1.0f);
            world.spawnParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }

        // Apply SLOWNESS to nearby players every 10 ticks
        if (tick % 10 == 0) {
            List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class,
                    boss.getBoundingBox().expand(SLOW_RANGE),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && boss.squaredDistanceTo(p) <= SLOW_RANGE * SLOW_RANGE);
            for (PlayerEntity p : nearby) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 15, 1, false, false));
            }
        }

        // Ambient sound every 8 ticks
        if (tick % 8 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE,
                    0.5f, (float)(1.5 + progress));
        }

        // Transition to beam state
        if (tick >= chargeTime) {
            if (target != null && target.isAlive()) {
                lockedFacing = target.getPos().add(0, 1, 0)
                        .subtract(boss.getPos().add(0, 1.2, 0))
                        .normalize();
            } else {
                lockedFacing = boss.getRotationVector().normalize();
            }
            beamPos      = boss.getPos().add(0, 1.2, 0);
            beamDir      = lockedFacing;
            beamTraveled = 0.0;
            hitPlayers.clear();

            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 1.5f, 1.8f);

            state = STATE_BEAM;
        }
    }

    private void tickBeam(ServerWorld world) {
        int    steps    = Math.max(1, (int) Math.ceil(beamSpeed));
        double stepDist = beamSpeed / steps;

        for (int s = 0; s < steps; s++) {
            beamPos      = beamPos.add(beamDir.multiply(stepDist));
            beamTraveled += stepDist;

            // Visual particles at beam head
            world.spawnParticles(ParticleTypes.END_ROD,
                    beamPos.x, beamPos.y, beamPos.z,
                    4, beamWidth * 0.2, beamWidth * 0.2, beamWidth * 0.2, 0.03);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    beamPos.x, beamPos.y, beamPos.z,
                    3, beamWidth * 0.3, beamWidth * 0.3, beamWidth * 0.3, 0.1);
            world.spawnParticles(ParticleTypes.SONIC_BOOM,
                    beamPos.x, beamPos.y, beamPos.z, 1, 0, 0, 0, 0);

            // Hit detection
            Box hitBox = new Box(
                    beamPos.x - beamWidth, beamPos.y - beamWidth, beamPos.z - beamWidth,
                    beamPos.x + beamWidth, beamPos.y + beamWidth, beamPos.z + beamWidth);
            List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                    p -> p.isAlive() && !hitPlayers.contains(p.getUuid()));

            for (PlayerEntity player : victims) {
                if (hitPlayers.contains(player.getUuid())) continue;
                hitPlayers.add(player.getUuid());
                player.damage(boss.getDamageSources().magic(), damage);
                Vec3d drag = beamDir.multiply(2.0);
                player.addVelocity(drag.x, drag.y * 0.3, drag.z);
                player.velocityModified = true;
            }

            // End of beam range — explosion
            if (beamTraveled >= range) {
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        beamPos.x, beamPos.y, beamPos.z, 2, 0.5, 0.5, 0.5, 0);
                world.spawnParticles(ParticleTypes.END_ROD,
                        beamPos.x, beamPos.y, beamPos.z, 40, 1.0, 1.0, 1.0, 0.3);
                world.playSound(null, beamPos.x, beamPos.y, beamPos.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 1.4f);
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
