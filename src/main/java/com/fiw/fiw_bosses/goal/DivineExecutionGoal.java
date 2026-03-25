package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Grab — boss seizes one nearby player, lifts them 3 blocks, delivers a message,
 * then hurls them away with damage.
 *
 * JSON params:
 *   grabRange      (double,  4.0)   max distance to grab a player
 *   approachRange  (double,  6.0)   boss moves toward player within this distance
 *   liftDuration   (int,    60)     ticks held in the air (3 s)
 *   throwDamage    (float,  20.0)   damage on throw
 *   throwPower     (double,  2.5)   horizontal velocity magnitude
 *   liftMessage    (String,  "")    message broadcast when player is lifted; empty = skip
 */
public class DivineExecutionGoal extends Goal {

    private static final int STATE_APPROACH = 0;
    private static final int STATE_LIFT     = 1;
    private static final int STATE_THROW    = 2;

    private final BossEntity boss;
    private final double grabRange;
    private final double approachRange;
    private final int    liftDuration;
    private final float  throwDamage;
    private final double throwPower;
    private final Text   liftMessage;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     state;
    private boolean active;
    private int     approachAge;
    private int     liftAge;

    private UUID    grabbedUuid;

    public DivineExecutionGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss         = boss;
        this.grabRange    = params.has("grabRange")     ? params.get("grabRange").getAsDouble()    : 4.0;
        this.approachRange= params.has("approachRange") ? params.get("approachRange").getAsDouble(): 6.0;
        this.liftDuration = params.has("liftDuration")  ? params.get("liftDuration").getAsInt()   : 60;
        this.throwDamage  = params.has("throwDamage")   ? params.get("throwDamage").getAsFloat()  : 20.0f;
        this.throwPower   = params.has("throwPower")    ? params.get("throwPower").getAsDouble()  : 2.5;
        this.cooldown     = cooldownTicks;

        String msg = params.has("liftMessage") ? params.get("liftMessage").getAsString() : "";
        this.liftMessage = msg.isEmpty() ? null : TextUtil.parseColorCodes(msg);

        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target instanceof PlayerEntity
                && target.isAlive()
                && boss.distanceTo(target) <= approachRange;
    }

    @Override
    public boolean shouldContinue() { return active; }

    @Override
    public void start() {
        state       = STATE_APPROACH;
        active      = true;
        approachAge = 0;
        liftAge     = 0;
        grabbedUuid = null;

        LivingEntity target = boss.getTarget();
        if (target instanceof PlayerEntity p) grabbedUuid = p.getUuid();
    }

    @Override
    public void tick() {
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        PlayerEntity grabbed = resolveGrabbed(world);

        // Abort if target invalid
        if (grabbed == null || !grabbed.isAlive()
                || grabbed.isSpectator() || grabbed.isCreative()) {
            active = false;
            return;
        }

        switch (state) {
            case STATE_APPROACH -> tickApproach(world, grabbed);
            case STATE_LIFT     -> tickLift(world, grabbed);
            case STATE_THROW    -> tickThrow(world, grabbed);
        }
    }

    // ── Approach ─────────────────────────────────────────────────────────────

    private void tickApproach(ServerWorld world, PlayerEntity target) {
        approachAge++;
        if (approachAge > 40) { active = false; return; } // give up after 2 s

        boss.getLookControl().lookAt(target, 30, 30);
        boss.getNavigation().startMovingTo(target, 1.4);

        if (boss.distanceTo(target) <= grabRange) {
            enterLift(world, target);
        }
    }

    // ── Lift ─────────────────────────────────────────────────────────────────

    private void enterLift(ServerWorld world, PlayerEntity target) {
        state   = STATE_LIFT;
        liftAge = 0;

        boss.getNavigation().stop();

        if (liftMessage != null) {
            for (ServerPlayerEntity sp : world.getPlayers()) {
                if (sp.squaredDistanceTo(boss) <= 64 * 64) {
                    sp.sendMessage(liftMessage, false);
                }
            }
        }

        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.HOSTILE, 1.0f, 1.2f);
    }

    private void tickLift(ServerWorld world, PlayerEntity target) {
        liftAge++;

        // Keep player hovering above boss
        Vec3d liftPos = boss.getPos().add(0, 3.2, 0);
        target.teleport(liftPos.x, liftPos.y, liftPos.z);
        target.setVelocity(0, 0, 0);
        target.velocityModified = true;

        // Particle cage around player
        int   pts    = 8;
        double offset= Math.toRadians(liftAge * 8.0);
        for (int i = 0; i < pts; i++) {
            double a  = offset + Math.toRadians(i * (360.0 / pts));
            double px = target.getX() + Math.cos(a) * 0.6;
            double pz = target.getZ() + Math.sin(a) * 0.6;
            world.spawnParticles(ParticleTypes.ENCHANT,
                    px, target.getY() + 0.5, pz, 1, 0, 0.1, 0, 0.05);
        }
        world.spawnParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.2, 0.2, 0.2, 0.05);

        boss.getLookControl().lookAt(target, 30, 30);

        if (liftAge >= liftDuration) {
            state = STATE_THROW;
        }
    }

    // ── Throw ─────────────────────────────────────────────────────────────────

    private void tickThrow(ServerWorld world, PlayerEntity target) {
        // Direction: boss facing + slight upward
        Vec3d facing = boss.getRotationVector().normalize();
        double vx = facing.x * throwPower;
        double vz = facing.z * throwPower;

        target.addVelocity(vx, 0.9, vz);
        target.velocityModified = true;
        target.damage(boss.getDamageSources().magic(), throwDamage);

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 1.1f);

        active = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerEntity resolveGrabbed(ServerWorld world) {
        if (grabbedUuid == null) return null;
        return world.getPlayerByUuid(grabbedUuid);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        boss.getNavigation().stop();
    }
}
