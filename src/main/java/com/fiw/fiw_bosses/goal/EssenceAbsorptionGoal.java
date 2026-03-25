package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Vampiric ability. Boss launches a slow soul-particle projectile.
 *  • On player hit: steals HP + applies Weakness.
 *  • On miss: projectile steers toward the nearest player for searchDuration ticks.
 *
 * JSON params:
 *   projectileSpeed  (double, 0.35)  blocks per tick
 *   healAmount       (float,  6.0)   HP boss gains on hit
 *   damage           (float,  8.0)   HP player loses on hit
 *   weaknessDuration (int,   120)    ticks of Weakness II on victim
 *   searchDuration   (int,    80)    ticks the projectile steers before dissipating
 *   range            (double, 20.0)  max travel range before entering search
 */
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

    private Vec3d projPos;
    private Vec3d projVel;

    public EssenceAbsorptionGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.projectileSpeed = params.has("projectileSpeed")  ? params.get("projectileSpeed").getAsDouble()  : 0.35;
        this.healAmount      = params.has("healAmount")       ? params.get("healAmount").getAsFloat()        : 6.0f;
        this.damage          = params.has("damage")           ? params.get("damage").getAsFloat()            : 8.0f;
        this.weaknessDuration= params.has("weaknessDuration") ? params.get("weaknessDuration").getAsInt()    : 120;
        this.searchDuration  = params.has("searchDuration")   ? params.get("searchDuration").getAsInt()      : 80;
        this.range           = params.has("range")            ? params.get("range").getAsDouble()            : 20.0;
        this.cooldown        = cooldownTicks;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= range;
    }

    @Override
    public boolean shouldContinue() { return active; }

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
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        switch (state) {
            case STATE_WINDUP  -> tickWindup(world);
            case STATE_FLY     -> tickFly(world);
            case STATE_SEARCH  -> tickSearch(world);
        }
    }

    // ── Windup ────────────────────────────────────────────────────────────────

    private void tickWindup(ServerWorld world) {
        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 30, 30);

        // Soul spiral around boss
        double a = Math.toRadians(tick * 18.0);
        for (int i = 0; i < 5; i++) {
            double ang = a + Math.toRadians(i * 72.0);
            double px = boss.getX() + Math.cos(ang) * 0.9;
            double pz = boss.getZ() + Math.sin(ang) * 0.9;
            world.spawnParticles(ParticleTypes.SOUL,
                    px, boss.getY() + 1.0 + Math.sin(tick * 0.3) * 0.3, pz, 1, 0, 0.05, 0, 0.01);
        }

        if (tick >= WINDUP_TICKS) {
            // Launch projectile
            Vec3d start = boss.getPos().add(0, 1.2, 0);
            Vec3d dir;
            if (target != null && target.isAlive()) {
                dir = target.getPos().add(0, 1.0, 0).subtract(start).normalize();
            } else {
                dir = boss.getRotationVector();
            }
            projPos = start;
            projVel = dir.multiply(projectileSpeed);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 0.8f, 0.5f);
            state = STATE_FLY;
        }
    }

    // ── Fly (straight) ───────────────────────────────────────────────────────

    private void tickFly(ServerWorld world) {
        moveProjectile(world);
        if (!active) return;

        if (traveled >= range) {
            state     = STATE_SEARCH;
            searchAge = 0;
        }
    }

    // ── Search (homing) ───────────────────────────────────────────────────────

    private void tickSearch(ServerWorld world) {
        searchAge++;
        if (searchAge > searchDuration) { active = false; return; }

        // Steer toward nearest player (max 5° turn per tick)
        PlayerEntity nearest = findNearestPlayer(world, 15.0);
        if (nearest != null) {
            Vec3d desired = nearest.getPos().add(0, 1.0, 0)
                    .subtract(projPos).normalize().multiply(projectileSpeed);
            projVel = steerToward(projVel, desired, Math.toRadians(5.0));
        }

        moveProjectile(world);
    }

    // ── Shared projectile step ────────────────────────────────────────────────

    private void moveProjectile(ServerWorld world) {
        projPos   = projPos.add(projVel);
        traveled += projVel.length();

        // Trail
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                projPos.x, projPos.y, projPos.z, 2, 0.1, 0.1, 0.1, 0.01);
        world.spawnParticles(ParticleTypes.SOUL,
                projPos.x, projPos.y, projPos.z, 3, 0.1, 0.1, 0.1, 0.01);

        // Pulse ring every 10 ticks
        if (tick % 10 == 0) {
            for (int i = 0; i < 8; i++) {
                double a  = Math.toRadians(i * 45.0);
                double px = projPos.x + Math.cos(a) * 0.4;
                double pz = projPos.z + Math.sin(a) * 0.4;
                world.spawnParticles(ParticleTypes.SOUL, px, projPos.y, pz, 1, 0, 0, 0, 0);
            }
        }

        // Hit detection
        Box hitBox = new Box(projPos.x - 0.5, projPos.y - 0.5, projPos.z - 0.5,
                             projPos.x + 0.5, projPos.y + 0.5, projPos.z + 0.5);
        List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (!victims.isEmpty()) {
            onHit(world, victims.get(0));
        }
    }

    private void onHit(ServerWorld world, PlayerEntity target) {
        target.damage(boss.getDamageSources().magic(), damage);
        target.addStatusEffect(
                new StatusEffectInstance(StatusEffects.WEAKNESS, weaknessDuration, 1, false, true));
        boss.heal(healAmount);

        // Burst particles
        world.spawnParticles(ParticleTypes.SOUL,
                projPos.x, projPos.y, projPos.z, 20, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                projPos.x, projPos.y, projPos.z, 15, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, projPos.x, projPos.y, projPos.z,
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.4f);

        active = false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private PlayerEntity findNearestPlayer(ServerWorld world, double radius) {
        return world.getEntitiesByClass(PlayerEntity.class,
                new Box(projPos.x - radius, projPos.y - radius, projPos.z - radius,
                        projPos.x + radius, projPos.y + radius, projPos.z + radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative())
                .stream()
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(projPos.x, projPos.y, projPos.z)))
                .orElse(null);
    }

    /** Gradually steers currentVel toward desiredVel by at most maxAngle radians. */
    private Vec3d steerToward(Vec3d current, Vec3d desired, double maxAngle) {
        double dot = current.normalize().dotProduct(desired.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.acos(dot);
        if (angle <= maxAngle) return desired.normalize().multiply(projectileSpeed);

        // Cross product to find rotation axis, then rotate by maxAngle
        Vec3d axis = current.normalize().crossProduct(desired.normalize()).normalize();
        if (axis.lengthSquared() < 1e-6) return current; // already aligned or opposite
        return rotateAround(current.normalize(), axis, maxAngle).multiply(projectileSpeed);
    }

    private Vec3d rotateAround(Vec3d v, Vec3d axis, double angle) {
        // Rodrigues rotation formula
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3d term1 = v.multiply(cos);
        Vec3d term2 = axis.crossProduct(v).multiply(sin);
        Vec3d term3 = axis.multiply(axis.dotProduct(v) * (1 - cos));
        return term1.add(term2).add(term3);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
