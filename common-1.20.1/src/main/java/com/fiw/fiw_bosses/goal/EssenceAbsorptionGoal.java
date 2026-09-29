package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class EssenceAbsorptionGoal extends Goal {

    private static final int STATE_WINDUP  = 0;
    private static final int STATE_FLY     = 1;
    private static final int STATE_SEARCH  = 2;

    private static final int WINDUP_TICKS = 15;

    private final BossEntity boss;
    private final double projectileSpeed;
    private final float  healAmount;
    private final float  damage;
    private final int    weaknessDuration;
    private final int    searchDuration;
    private final double range;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     searchAge;
    private double  traveled;

    private Vec3 projPos;
    private Vec3 projVel;

    public EssenceAbsorptionGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.projectileSpeed = params.has("projectileSpeed")  ? params.get("projectileSpeed").getAsDouble()  : 0.35;
        this.healAmount      = params.has("healAmount")       ? params.get("healAmount").getAsFloat()        : 6.0f;
        this.damage          = params.has("damage")           ? params.get("damage").getAsFloat()            : 8.0f;
        this.weaknessDuration= params.has("weaknessDuration") ? params.get("weaknessDuration").getAsInt()    : 120;
        this.searchDuration  = params.has("searchDuration")   ? params.get("searchDuration").getAsInt()      : 80;
        this.range           = params.has("range")            ? params.get("range").getAsDouble()            : 20.0;
        this.cooldown        = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= range;
    }

    @Override
    public boolean canContinueToUse() { return active; }

    @Override
    public void start() {
        tick       = 0;
        state      = STATE_WINDUP;
        active     = true;
        searchAge  = 0;
        traveled   = 0;
        projPos    = null;
        projVel    = null;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        switch (state) {
            case STATE_WINDUP  -> tickWindup(level);
            case STATE_FLY     -> tickFly(level);
            case STATE_SEARCH  -> tickSearch(level);
        }
    }

    private void tickWindup(ServerLevel level) {
        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 30, 30);

        double a = Math.toRadians(tick * 18.0);
        for (int i = 0; i < 5; i++) {
            double ang = a + Math.toRadians(i * 72.0);
            double px = boss.getX() + Math.cos(ang) * 0.9;
            double pz = boss.getZ() + Math.sin(ang) * 0.9;
            level.sendParticles(ParticleTypes.SOUL,
                    px, boss.getY() + 1.0 + Math.sin(tick * 0.3) * 0.3, pz, 1, 0, 0.05, 0, 0.01);
        }

        if (tick >= WINDUP_TICKS) {
            Vec3 start = boss.position().add(0, 1.2, 0);
            Vec3 dir;
            if (target != null && target.isAlive()) {
                dir = target.position().add(0, 1.0, 0).subtract(start).normalize();
            } else {
                dir = boss.getLookAngle();
            }
            projPos = start;
            projVel = dir.scale(projectileSpeed);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.8f, 0.5f);
            state = STATE_FLY;
        }
    }

    private void tickFly(ServerLevel level) {
        moveProjectile(level);
        if (!active) return;

        if (traveled >= range) {
            state     = STATE_SEARCH;
            searchAge = 0;
        }
    }

    private void tickSearch(ServerLevel level) {
        searchAge++;
        if (searchAge > searchDuration) { active = false; return; }

        Player nearest = findNearestPlayer(level, 15.0);
        if (nearest != null) {
            Vec3 desired = nearest.position().add(0, 1.0, 0)
                    .subtract(projPos).normalize().scale(projectileSpeed);
            projVel = steerToward(projVel, desired, Math.toRadians(5.0));
        }

        moveProjectile(level);
    }

    private void moveProjectile(ServerLevel level) {
        projPos   = projPos.add(projVel);
        traveled += projVel.length();

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                projPos.x, projPos.y, projPos.z, 2, 0.1, 0.1, 0.1, 0.01);
        level.sendParticles(ParticleTypes.SOUL,
                projPos.x, projPos.y, projPos.z, 3, 0.1, 0.1, 0.1, 0.01);

        if (tick % 10 == 0) {
            for (int i = 0; i < 8; i++) {
                double a  = Math.toRadians(i * 45.0);
                double px = projPos.x + Math.cos(a) * 0.4;
                double pz = projPos.z + Math.sin(a) * 0.4;
                level.sendParticles(ParticleTypes.SOUL, px, projPos.y, pz, 1, 0, 0, 0, 0);
            }
        }

        AABB hitBox = new AABB(projPos.x - 0.5, projPos.y - 0.5, projPos.z - 0.5,
                               projPos.x + 0.5, projPos.y + 0.5, projPos.z + 0.5);
        List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (!victims.isEmpty()) {
            onHit(level, victims.get(0));
        }
    }

    private void onHit(ServerLevel level, Player target) {
        target.hurt(boss.damageSources().magic(), damage);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessDuration, 1, false, true));
        boss.heal(healAmount);

        level.sendParticles(ParticleTypes.SOUL,
                projPos.x, projPos.y, projPos.z, 20, 0.5, 0.5, 0.5, 0.05);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                projPos.x, projPos.y, projPos.z, 15, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, projPos.x, projPos.y, projPos.z,
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.0f, 1.4f);

        active = false;
    }

    private Player findNearestPlayer(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Player.class,
                new AABB(projPos.x - radius, projPos.y - radius, projPos.z - radius,
                        projPos.x + radius, projPos.y + radius, projPos.z + radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative())
                .stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(projPos.x, projPos.y, projPos.z)))
                .orElse(null);
    }

    private Vec3 steerToward(Vec3 current, Vec3 desired, double maxAngle) {
        double dot = current.normalize().dot(desired.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.acos(dot);
        if (angle <= maxAngle) return desired.normalize().scale(projectileSpeed);

        Vec3 axis = current.normalize().cross(desired.normalize()).normalize();
        if (axis.lengthSqr() < 1e-6) return current;
        return rotateAround(current.normalize(), axis, maxAngle).scale(projectileSpeed);
    }

    private Vec3 rotateAround(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3 term1 = v.scale(cos);
        Vec3 term2 = axis.cross(v).scale(sin);
        Vec3 term3 = axis.scale(axis.dot(v) * (1 - cos));
        return term1.add(term2).add(term3);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
