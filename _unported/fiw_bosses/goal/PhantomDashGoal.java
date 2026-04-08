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

/**
 * 3 rapid zigzag dashes toward the target — lightning/anime style.
 * Unpredictable: each dash alternates left/right with a random angle variation.
 *
 * JSON params:
 *   dashCount          (int,    3)     number of dashes
 *   dashDistance       (double, 4.0)   blocks per dash
 *   damage             (float,  8.0)   damage if a player is inside dashHitRadius at endpoint
 *   delayBetweenDashes (int,    8)     ticks pause between each dash
 *   dashHitRadius      (double, 1.5)   hit detection radius at dash endpoint
 */
public class PhantomDashGoal extends Goal {

    private static final int STATE_WINDUP = 0;
    private static final int STATE_DASHING = 1;

    private static final int WINDUP_TICKS = 10;

    private static final DustParticleEffect DUST_YELLOW =
            new DustParticleEffect(new Vector3f(0.95f, 0.90f, 0.10f), 1.4f);
    private static final DustParticleEffect DUST_WHITE =
            new DustParticleEffect(new Vector3f(1.0f, 1.0f, 1.0f), 2.0f);

    private final BossEntity boss;
    private final int    dashCount;
    private final double dashDistance;
    private final float  damage;
    private final int    dashDelay;
    private final double hitRadius;
    private final int    cooldown;

    private int        cooldownTimer;
    private int        tick;
    private int        state;
    private boolean    active;
    private int        dashesExecuted;
    private int        delayTimer;

    private Vec3d         dashStart;
    private Vec3d         dashEnd;

    private final Set<UUID> hitPlayers = new HashSet<>();

    public PhantomDashGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss         = boss;
        this.dashCount    = params.has("dashCount")          ? params.get("dashCount").getAsInt()          : 3;
        this.dashDistance = params.has("dashDistance")       ? params.get("dashDistance").getAsDouble()    : 4.0;
        this.damage       = params.has("damage")             ? params.get("damage").getAsFloat()           : 8.0f;
        this.dashDelay    = params.has("delayBetweenDashes") ? params.get("delayBetweenDashes").getAsInt() : 8;
        this.hitRadius    = params.has("dashHitRadius")      ? params.get("dashHitRadius").getAsDouble()   : 1.5;
        this.cooldown     = cooldownTicks;
        this.setControls(EnumSet.of(Control.LOOK, Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= 20.0;
    }

    @Override
    public boolean shouldContinue() { return active; }

    @Override
    public void start() {
        tick           = 0;
        state          = STATE_WINDUP;
        active         = true;
        dashesExecuted = 0;
        delayTimer     = 0;
        hitPlayers.clear();
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            boss.getLookControl().lookAt(target, 30, 30);
        }

        if (state == STATE_WINDUP) {
            tickWindup(world, target);
        } else {
            tickDashing(world, target);
        }
    }

    private void tickWindup(ServerWorld world, LivingEntity target) {
        // Electric pulse around boss
        int    pts  = 8;
        double swirl= Math.toRadians(tick * 20.0);
        for (int i = 0; i < pts; i++) {
            double a = swirl + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * 0.9;
            double pz = boss.getZ() + Math.sin(a) * 0.9;
            world.spawnParticles(ParticleTypes.CRIT,   px, boss.getY() + 1.0, pz, 1, 0.05, 0.1, 0.05, 0.1);
            world.spawnParticles(DUST_YELLOW,           px, boss.getY() + 1.0, pz, 1, 0, 0, 0, 0);
        }

        if (tick >= WINDUP_TICKS) {
            state      = STATE_DASHING;
            delayTimer = 0;
            prepareDash(target, 0);
        }
    }

    private void tickDashing(ServerWorld world, LivingEntity target) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // Execute next dash
        executeDash(world);
        dashesExecuted++;

        if (dashesExecuted >= dashCount) {
            active = false;
        } else {
            delayTimer = dashDelay;
            prepareDash(target, dashesExecuted);
        }
    }

    /**
     * Computes dashEnd for the next dash: direction toward target + zigzag perpendicular,
     * with a small random angle variation for unpredictability.
     */
    private void prepareDash(LivingEntity target, int dashIndex) {
        dashStart = boss.getPos();

        Vec3d toTarget;
        if (target != null && target.isAlive()) {
            toTarget = target.getPos().subtract(dashStart).normalize();
        } else {
            toTarget = boss.getRotationVector().normalize();
        }
        // Flatten to horizontal
        toTarget = new Vec3d(toTarget.x, 0, toTarget.z).normalize();

        // Perpendicular: alternate left/right
        double sign = (dashIndex % 2 == 0) ? 1.0 : -1.0;
        Vec3d perp = new Vec3d(-toTarget.z * sign, 0, toTarget.x * sign);

        // Random angle variation ±20°
        double variance = Math.toRadians(20.0) * (boss.getRandom().nextDouble() * 2 - 1);
        double cosV = Math.cos(variance), sinV = Math.sin(variance);
        double dirX = toTarget.x * cosV - toTarget.z * sinV;
        double dirZ = toTarget.x * sinV + toTarget.z * cosV;
        Vec3d dir = new Vec3d(dirX + perp.x * 0.5, 0, dirZ + perp.z * 0.5).normalize();

        dashEnd = dashStart.add(dir.multiply(dashDistance));
    }

    private void executeDash(ServerWorld world) {
        if (dashStart == null || dashEnd == null) return;

        // Particle trail along dash path
        int    trailSteps = 10;
        double stepSize   = 1.0 / trailSteps;
        for (int s = 0; s <= trailSteps; s++) {
            double t  = s * stepSize;
            double px = dashStart.x + (dashEnd.x - dashStart.x) * t;
            double py = dashStart.y + (dashEnd.y - dashStart.y) * t;
            double pz = dashStart.z + (dashEnd.z - dashStart.z) * t;

            world.spawnParticles(ParticleTypes.CRIT,  px, py + 1.0, pz, 2, 0.15, 0.15, 0.15, 0.15);
            world.spawnParticles(DUST_YELLOW,          px, py + 1.0, pz, 2, 0.1, 0.1, 0.1, 0);
        }

        // Flash at endpoint
        world.spawnParticles(DUST_WHITE,
                dashEnd.x, dashEnd.y + 1.0, dashEnd.z, 8, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.END_ROD,
                dashEnd.x, dashEnd.y + 1.0, dashEnd.z, 5, 0.2, 0.2, 0.2, 0.1);

        // Sound
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.2f, 1.4f);

        // Teleport
        boss.requestTeleport(dashEnd.x, dashEnd.y, dashEnd.z);

        // Hit detection at endpoint
        Box hitBox = new Box(
                dashEnd.x - hitRadius, dashEnd.y - 0.5, dashEnd.z - hitRadius,
                dashEnd.x + hitRadius, dashEnd.y + 2.5, dashEnd.z + hitRadius);
        List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !hitPlayers.contains(p.getUuid()));
        for (PlayerEntity p : victims) {
            hitPlayers.add(p.getUuid());
            p.damage(boss.getDamageSources().magic(), damage);
            world.spawnParticles(ParticleTypes.CRIT,
                    p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }

        // Prepare for the next dash starting from current position
        dashStart = boss.getPos();
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        hitPlayers.clear();
    }
}
