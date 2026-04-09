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

public class PhantomDashGoal extends Goal {

    private static final int STATE_WINDUP = 0;
    private static final int STATE_DASHING = 1;
    private static final int WINDUP_TICKS = 10;

    private static final DustParticleOptions DUST_YELLOW =
            new DustParticleOptions(new Vector3f(0.95f, 0.90f, 0.10f), 1.4f);
    private static final DustParticleOptions DUST_WHITE =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 2.0f);

    private final BossEntity boss;
    private final int    dashCount;
    private final double dashDistance;
    private final float  damage;
    private final int    dashDelay;
    private final double hitRadius;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     dashesExecuted;
    private int     delayTimer;

    private Vec3 dashStart;
    private Vec3 dashEnd;

    private final Set<UUID> hitPlayers = new HashSet<>();

    public PhantomDashGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss         = boss;
        this.dashCount    = params.has("dashCount")          ? params.get("dashCount").getAsInt()          : 3;
        this.dashDistance = params.has("dashDistance")       ? params.get("dashDistance").getAsDouble()    : 4.0;
        this.damage       = params.has("damage")             ? params.get("damage").getAsFloat()           : 8.0f;
        this.dashDelay    = params.has("delayBetweenDashes") ? params.get("delayBetweenDashes").getAsInt() : 8;
        this.hitRadius    = params.has("dashHitRadius")      ? params.get("dashHitRadius").getAsDouble()   : 1.5;
        this.cooldown     = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= 20.0;
    }

    @Override
    public boolean canContinueToUse() { return active; }

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
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (state == STATE_WINDUP) {
            tickWindup(level, target);
        } else {
            tickDashing(level, target);
        }
    }

    private void tickWindup(ServerLevel level, LivingEntity target) {
        int    pts  = 8;
        double swirl= Math.toRadians(tick * 20.0);
        for (int i = 0; i < pts; i++) {
            double a = swirl + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * 0.9;
            double pz = boss.getZ() + Math.sin(a) * 0.9;
            level.sendParticles(ParticleTypes.CRIT,   px, boss.getY() + 1.0, pz, 1, 0.05, 0.1, 0.05, 0.1);
            level.sendParticles(DUST_YELLOW,           px, boss.getY() + 1.0, pz, 1, 0, 0, 0, 0);
        }

        if (tick >= WINDUP_TICKS) {
            state      = STATE_DASHING;
            delayTimer = 0;
            prepareDash(target, 0);
        }
    }

    private void tickDashing(ServerLevel level, LivingEntity target) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        executeDash(level);
        dashesExecuted++;

        if (dashesExecuted >= dashCount) {
            active = false;
        } else {
            delayTimer = dashDelay;
            prepareDash(target, dashesExecuted);
        }
    }

    private void prepareDash(LivingEntity target, int dashIndex) {
        dashStart = boss.position();

        Vec3 toTarget;
        if (target != null && target.isAlive()) {
            toTarget = target.position().subtract(dashStart).normalize();
        } else {
            toTarget = boss.getLookAngle().normalize();
        }
        toTarget = new Vec3(toTarget.x, 0, toTarget.z).normalize();

        double sign = (dashIndex % 2 == 0) ? 1.0 : -1.0;
        Vec3 perp = new Vec3(-toTarget.z * sign, 0, toTarget.x * sign);

        double variance = Math.toRadians(20.0) * (boss.getRandom().nextDouble() * 2 - 1);
        double cosV = Math.cos(variance), sinV = Math.sin(variance);
        double dirX = toTarget.x * cosV - toTarget.z * sinV;
        double dirZ = toTarget.x * sinV + toTarget.z * cosV;
        Vec3 dir = new Vec3(dirX + perp.x * 0.5, 0, dirZ + perp.z * 0.5).normalize();

        dashEnd = dashStart.add(dir.scale(dashDistance));
    }

    private void executeDash(ServerLevel level) {
        if (dashStart == null || dashEnd == null) return;

        int    trailSteps = 10;
        double stepSize   = 1.0 / trailSteps;
        for (int s = 0; s <= trailSteps; s++) {
            double t  = s * stepSize;
            double px = dashStart.x + (dashEnd.x - dashStart.x) * t;
            double py = dashStart.y + (dashEnd.y - dashStart.y) * t;
            double pz = dashStart.z + (dashEnd.z - dashStart.z) * t;

            level.sendParticles(ParticleTypes.CRIT,  px, py + 1.0, pz, 2, 0.15, 0.15, 0.15, 0.15);
            level.sendParticles(DUST_YELLOW,          px, py + 1.0, pz, 2, 0.1, 0.1, 0.1, 0);
        }

        level.sendParticles(DUST_WHITE,
                dashEnd.x, dashEnd.y + 1.0, dashEnd.z, 8, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ParticleTypes.END_ROD,
                dashEnd.x, dashEnd.y + 1.0, dashEnd.z, 5, 0.2, 0.2, 0.2, 0.1);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 1.4f);

        boss.teleportTo(dashEnd.x, dashEnd.y, dashEnd.z);

        AABB hitBox = new AABB(
                dashEnd.x - hitRadius, dashEnd.y - 0.5, dashEnd.z - hitRadius,
                dashEnd.x + hitRadius, dashEnd.y + 2.5, dashEnd.z + hitRadius);
        List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !hitPlayers.contains(p.getUUID()));
        for (Player p : victims) {
            hitPlayers.add(p.getUUID());
            p.hurt(boss.damageSources().magic(), damage);
            level.sendParticles(ParticleTypes.CRIT,
                    p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }

        dashStart = boss.position();
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        hitPlayers.clear();
    }
}
