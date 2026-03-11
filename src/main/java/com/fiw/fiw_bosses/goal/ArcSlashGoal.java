package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Animated arc slash — sweeps a blade-shaped particle trail through space over
 * several ticks, dealing damage to any entity the tip passes through.
 *
 * JSON params:
 *   arc        (float,  180)  total sweep angle in degrees
 *   radius     (float,  4.5)  reach of the slash in blocks
 *   damage     (float, 12.0)  damage per entity hit
 *   duration   (int,     8)   ticks to complete the full sweep
 *   points     (int,    28)   arc resolution (more = smoother particles)
 *   yOffset    (float,  1.1)  height from ground at the center of the arc
 *   height     (float,  1.0)  vertical bulge at the arc's midpoint
 *   roll       (float,  0.0)  tilt of slash plane in degrees (+= right-to-left upstroke)
 *   hitRadius  (float,  1.2)  collision sphere radius at each arc point
 *   taunt      (string, null) boss chat message at swing start
 */
public class ArcSlashGoal extends Goal {

    private final BossEntity boss;
    private final float arc;
    private final float radius;
    private final float damage;
    private final int duration;
    private final int points;
    private final float yOffset;
    private final float height;
    private final float roll;
    private final float hitRadius;
    private final int cooldown;
    private final String taunt;

    private int cooldownTimer;
    private int slashTick;

    // Locked at the moment the slash fires — prevents the boss turning mid-swing
    private Vec3d slashOrigin;
    private Vec3d slashForward;   // horizontal forward unit vector
    private Vec3d slashRight;     // horizontal right unit vector (perp to forward)

    private static final int WINDUP_TICKS = 8;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public ArcSlashGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.arc       = params.has("arc")       ? params.get("arc").getAsFloat()       : 180.0f;
        this.radius    = params.has("radius")    ? params.get("radius").getAsFloat()    : 4.5f;
        this.damage    = params.has("damage")    ? params.get("damage").getAsFloat()    : 12.0f;
        this.duration  = params.has("duration")  ? params.get("duration").getAsInt()    : 8;
        this.points    = params.has("points")    ? params.get("points").getAsInt()      : 28;
        this.yOffset   = params.has("yOffset")   ? params.get("yOffset").getAsFloat()   : 1.1f;
        this.height    = params.has("height")    ? params.get("height").getAsFloat()    : 1.0f;
        this.roll      = params.has("roll")      ? params.get("roll").getAsFloat()      : 0.0f;
        this.hitRadius = params.has("hitRadius") ? params.get("hitRadius").getAsFloat() : 1.2f;
        this.cooldown  = cooldownTicks;
        this.taunt     = params.has("taunt")     ? params.get("taunt").getAsString()    : null;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 3;
    }

    @Override
    public void start() {
        slashTick = -WINDUP_TICKS;
        alreadyHit.clear();

        // Orient toward target; update every windup tick until swing locks
        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);

        snapOrientation();

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Low pitch "charge" sound during windup
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 0.35f);

            if (taunt != null) {
                sendTaunt(world, taunt);
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return slashTick < duration;
    }

    @Override
    public void tick() {
        slashTick++;
        // Boss stands still for the full animation
        boss.setVelocity(0, boss.getVelocity().y, 0);
        boss.velocityModified = true;

        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // ── WINDUP ──────────────────────────────────────────────────────────
        if (slashTick <= 0) {
            // Keep tracking target while winding up; lock at tick 0
            LivingEntity target = boss.getTarget();
            if (target != null) {
                boss.getLookControl().lookAt(target, 360, 90);
                snapOrientation();
            }

            float windupT = (float)(WINDUP_TICKS + slashTick) / WINDUP_TICKS; // 0→1

            // Faint preview outline of the full arc (ghosts of where the slash will go)
            if (windupT > 0.4f) {
                for (int i = 0; i <= points; i += 2) {
                    double t = (double) i / points;
                    Vec3d preview = arcPoint(-arc / 2.0 + t * arc, t);
                    world.spawnParticles(ParticleTypes.SMOKE,
                            preview.x, preview.y, preview.z, 1, 0, 0, 0, 0);
                }
            }

            // Particles gather at the sweep start position
            Vec3d startPos = arcPoint(-arc / 2.0, 0.0);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    startPos.x, startPos.y, startPos.z,
                    (int)(1 + windupT * 5), 0.22, 0.22, 0.22, 0.06);
            world.spawnParticles(ParticleTypes.CRIT,
                    startPos.x, startPos.y, startPos.z,
                    2, 0.12, 0.12, 0.12, 0.08);
            return;
        }

        // ── SWING ────────────────────────────────────────────────────────────
        if (slashTick == 1) {
            // The actual slash whoosh — high pitch, loud
            world.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0f, 0.9f);
        }

        // How many arc points to cover this tick
        double prevT = (double)(slashTick - 1) / duration;
        double currT = (double) slashTick / duration;
        int iStart = (int)(prevT * points);
        int iEnd   = Math.min(points, (int)(currT * points) + 1);

        for (int pi = iStart; pi <= iEnd; pi++) {
            double t = (double) pi / points;               // 0→1 along the arc
            double thetaDeg = -arc / 2.0 + t * arc;       // degrees from center
            Vec3d pos = arcPoint(thetaDeg, t);

            // ── Particle layers ──────────────────────────────────────────
            // 1. SWEEP_ATTACK — the big vanilla sword-sweep glyph
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);

            // 2. CRIT — bright blade-edge sparks (tight spread to stay on arc)
            world.spawnParticles(ParticleTypes.CRIT,
                    pos.x, pos.y, pos.z, 4, 0.07, 0.07, 0.07, 0.20);

            // 3. ENCHANTED_HIT — shimmering shimmer layer
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y, pos.z, 2, 0.07, 0.07, 0.07, 0.14);

            // 4. LARGE_SMOKE — trailing wake behind the blade (every other point)
            if (pi % 2 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.004);
            }

            // 5. FLASH at the midpoint of the arc (halfway through the swing)
            if (pi == points / 2) {
                world.spawnParticles(ParticleTypes.FLASH,
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }

            // ── Hit detection ─────────────────────────────────────────────
            Box hitBox = new Box(
                    pos.x - hitRadius, pos.y - hitRadius - 0.5, pos.z - hitRadius,
                    pos.x + hitRadius, pos.y + hitRadius + 0.5, pos.z + hitRadius);

            List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, hitBox,
                    e -> e != boss && e.isAlive() && !boss.isMinion(e)
                            && !alreadyHit.contains(e.getUuid()));

            for (LivingEntity victim : victims) {
                victim.damage(boss.getDamageSources().mobAttack(boss), damage);
                alreadyHit.add(victim.getUuid());

                // Knockback away from boss
                Vec3d knock = victim.getPos().subtract(boss.getPos()).normalize();
                victim.addVelocity(knock.x * 0.8, 0.4, knock.z * 0.8);
                victim.velocityModified = true;

                // Impact burst at hit entity
                world.spawnParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(),
                        16, 0.45, 0.45, 0.45, 0.28);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(),
                        8, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(),
                        1, 0, 0, 0, 0);
                world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.3f, 0.75f);
            }
        }

        // End-of-swing flash and high-pitch crack
        if (slashTick == duration) {
            Vec3d endPos = arcPoint(arc / 2.0, 1.0);
            world.spawnParticles(ParticleTypes.FLASH, endPos.x, endPos.y, endPos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CRIT,
                    endPos.x, endPos.y, endPos.z, 8, 0.3, 0.3, 0.3, 0.3);
            world.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * World-space arc point.
     *
     * @param thetaDeg  Angle along arc in degrees relative to boss facing.
     *                  -arc/2 = sweep start,  +arc/2 = sweep end.
     * @param t         Normalized progress 0→1 (used for vertical curve + roll).
     */
    private Vec3d arcPoint(double thetaDeg, double t) {
        double theta = Math.toRadians(thetaDeg);

        // Horizontal: rotate around the locked forward/right basis
        double hx = slashOrigin.x
                + radius * (Math.cos(theta) * slashForward.x + Math.sin(theta) * slashRight.x);
        double hz = slashOrigin.z
                + radius * (Math.cos(theta) * slashForward.z + Math.sin(theta) * slashRight.z);

        // Vertical: parabola peaks at mid-sweep (sin curve 0→1→0)
        double vertArc = Math.sin(Math.PI * t);

        // Roll: tilts the parabola so one side is higher than the other
        // lateralFrac goes -1 (start) → 0 (center) → +1 (end)
        double lateralFrac = t * 2.0 - 1.0;
        double rollOffset = lateralFrac * Math.sin(Math.toRadians(roll)) * height;

        double hy = slashOrigin.y + yOffset + height * vertArc + rollOffset;

        return new Vec3d(hx, hy, hz);
    }

    /** Locks slashForward/slashRight/slashOrigin to the current boss orientation. */
    private void snapOrientation() {
        Vec3d fwd = boss.getRotationVec(1.0f);
        slashForward = new Vec3d(fwd.x, 0, fwd.z).normalize();
        slashRight   = new Vec3d(-slashForward.z, 0, slashForward.x);
        slashOrigin  = boss.getPos();
    }

    private void sendTaunt(ServerWorld world, String message) {
        Text text = Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(message));
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(boss) <= 48 * 48) {
                player.sendMessage(text, false);
            }
        }
    }
}
