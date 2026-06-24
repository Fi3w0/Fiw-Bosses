package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

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
    private final Component liftMessage;
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

        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target instanceof Player
                && target.isAlive()
                && boss.distanceTo(target) <= approachRange;
    }

    @Override
    public boolean canContinueToUse() { return active; }

    @Override
    public void start() {
        state       = STATE_APPROACH;
        active      = true;
        approachAge = 0;
        liftAge     = 0;
        grabbedUuid = null;

        LivingEntity target = boss.getTarget();
        if (target instanceof Player p) grabbedUuid = p.getUUID();
    }

    @Override
    public void tick() {
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        Player grabbed = resolveGrabbed(level);

        if (grabbed == null || !grabbed.isAlive()
                || grabbed.isSpectator() || grabbed.isCreative()) {
            active = false;
            return;
        }

        switch (state) {
            case STATE_APPROACH -> tickApproach(level, grabbed);
            case STATE_LIFT     -> tickLift(level, grabbed);
            case STATE_THROW    -> tickThrow(level, grabbed);
        }
    }

    private void tickApproach(ServerLevel level, Player target) {
        approachAge++;
        if (approachAge > 40) { active = false; return; }

        boss.getLookControl().setLookAt(target, 30, 30);
        boss.getNavigation().moveTo(target, 1.4);

        if (boss.distanceTo(target) <= grabRange) {
            enterLift(level, target);
        }
    }

    private void enterLift(ServerLevel level, Player target) {
        state   = STATE_LIFT;
        liftAge = 0;

        boss.getNavigation().stop();

        if (liftMessage != null) {
            for (ServerPlayer sp : level.players()) {
                if (sp.distanceToSqr(boss) <= 64 * 64) {
                    sp.sendSystemMessage(liftMessage);
                }
            }
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.2f);
    }

    private void tickLift(ServerLevel level, Player target) {
        liftAge++;

        Vec3 liftPos = boss.position().add(0, 3.2, 0);
        target.teleportTo(liftPos.x, liftPos.y, liftPos.z);
        target.setDeltaMovement(0, 0, 0);
        target.hurtMarked = true;

        int   pts    = 8;
        double offset= Math.toRadians(liftAge * 8.0);
        for (int i = 0; i < pts; i++) {
            double a  = offset + Math.toRadians(i * (360.0 / pts));
            double px = target.getX() + Math.cos(a) * 0.6;
            double pz = target.getZ() + Math.sin(a) * 0.6;
            level.sendParticles(ParticleTypes.ENCHANT,
                    px, target.getY() + 0.5, pz, 1, 0, 0.1, 0, 0.05);
        }
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.2, 0.2, 0.2, 0.05);

        boss.getLookControl().setLookAt(target, 30, 30);

        if (liftAge >= liftDuration) {
            state = STATE_THROW;
        }
    }

    private void tickThrow(ServerLevel level, Player target) {
        Vec3 facing = boss.getLookAngle().normalize();
        double vx = facing.x * throwPower;
        double vz = facing.z * throwPower;

        target.push(vx, 0.9, vz);
        target.hurtMarked = true;
        target.hurt(boss.damageSources().magic(), throwDamage);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.1f);

        active = false;
    }

    private Player resolveGrabbed(ServerLevel level) {
        if (grabbedUuid == null) return null;
        return level.getPlayerByUUID(grabbedUuid);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        boss.getNavigation().stop();
    }
}
