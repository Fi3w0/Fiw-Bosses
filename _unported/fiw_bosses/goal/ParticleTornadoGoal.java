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
import java.util.List;

/**
 * Creates a rising tornado of particles centered on the boss.
 * Wider at the top, narrow at the base — rotates each tick.
 * Optional damage to players caught inside the funnel.
 * Boss freezes in place while the tornado is active.
 *
 * JSON params:
 *   maxRadius      (float,  4.0)   radius at the top of the tornado
 *   height         (float,  6.0)   total height of the tornado in blocks
 *   duration       (int,  100)     ticks the tornado lasts
 *   rotationSpeed  (float,  8.0)   degrees rotated per tick
 *   disks          (int,    12)    horizontal rings forming the tornado
 *   damage         (float,  0.0)   damage per tick to players inside (0 = visual only)
 *   taunt          (string, null)
 */
public class ParticleTornadoGoal extends Goal {

    private final BossEntity boss;
    private final float maxRadius;
    private final float height;
    private final int duration;
    private final float rotationSpeed;
    private final int disks;
    private final float damage;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private double rotation;   // current base rotation angle in degrees
    private Vec3d origin;      // locked position at start

    public ParticleTornadoGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss          = boss;
        this.maxRadius     = params.has("maxRadius")     ? params.get("maxRadius").getAsFloat()    : 4.0f;
        this.height        = params.has("height")        ? params.get("height").getAsFloat()        : 6.0f;
        this.duration      = params.has("duration")      ? params.get("duration").getAsInt()        : 100;
        this.rotationSpeed = params.has("rotationSpeed") ? params.get("rotationSpeed").getAsFloat() : 8.0f;
        this.disks         = params.has("disks")         ? params.get("disks").getAsInt()           : 12;
        this.damage        = params.has("damage")        ? params.get("damage").getAsFloat()        : 0.0f;
        this.taunt         = params.has("taunt")         ? params.get("taunt").getAsString()        : null;
        this.cooldown      = cooldownTicks;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        rotation = 0;
        origin = boss.getPos();

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            world.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.5f, 0.4f);

            if (taunt != null) {
                var bossName = boss.getCustomName();
                Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                        .append(bossName != null ? bossName.copy() : Text.literal("Boss"))
                        .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(taunt));
                for (var player : world.getPlayers()) {
                    if (player.squaredDistanceTo(boss) <= 48 * 48)
                        player.sendMessage(tauntText, false);
                }
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return tick < duration;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        rotation = (rotation + rotationSpeed) % 360.0;
        double rotRad = Math.toRadians(rotation);

        // Draw each disk stacked vertically
        for (int d = 0; d < disks; d++) {
            double t = (double) d / (disks - 1);            // 0 (bottom) → 1 (top)
            double diskY    = origin.y + t * height;
            double diskRadius = maxRadius * t;               // narrow at base, wide at top

            // Points around this disk's ring
            int points = Math.max(6, (int)(diskRadius * 6));
            for (int p = 0; p < points; p++) {
                double angle = rotRad + Math.toRadians((360.0 / points) * p);
                double px = origin.x + Math.cos(angle) * diskRadius;
                double pz = origin.z + Math.sin(angle) * diskRadius;

                // Alternate particle types for visual interest
                if (d % 3 == 0) {
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, diskY, pz, 1, 0, 0, 0, 0);
                } else if (d % 3 == 1) {
                    world.spawnParticles(ParticleTypes.FLAME, px, diskY, pz, 1, 0, 0, 0, 0.01);
                } else {
                    world.spawnParticles(ParticleTypes.SMOKE, px, diskY, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        // Cloud base at bottom
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                origin.x, origin.y + 0.1, origin.z,
                3, maxRadius * 0.4, 0.1, maxRadius * 0.4, 0.02);

        // Damage players inside the funnel every 5 ticks
        if (damage > 0 && tick % 5 == 0) {
            Box funnel = new Box(origin.x - maxRadius, origin.y, origin.z - maxRadius,
                                 origin.x + maxRadius, origin.y + height, origin.z + maxRadius);
            List<LivingEntity> inside = world.getEntitiesByClass(LivingEntity.class, funnel,
                    e -> e != boss && e.isAlive() && !boss.isMinion(e)
                         && isInsideTornado(e));
            for (LivingEntity entity : inside) {
                entity.damage(boss.getDamageSources().mobAttack(boss), damage);
                // Spin velocity
                Vec3d toCenter = origin.subtract(entity.getPos()).normalize();
                entity.addVelocity(toCenter.x * 0.3, 0.15, toCenter.z * 0.3);
                entity.velocityModified = true;
            }
        }

        // Wind sound every 20 ticks
        if (tick % 20 == 0) {
            world.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 0.8f,
                    0.6f + boss.getRandom().nextFloat() * 0.4f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Dispersal burst
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    origin.x, origin.y + height * 0.5, origin.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SOUL,
                    origin.x, origin.y + 1, origin.z,
                    20, maxRadius * 0.5, height * 0.3, maxRadius * 0.5, 0.1);
        }
    }

    /** Checks if entity is roughly within the cone of the tornado at its height. */
    private boolean isInsideTornado(LivingEntity entity) {
        double relY = entity.getY() - origin.y;
        if (relY < 0 || relY > height) return false;
        double t = relY / height;
        double allowedRadius = maxRadius * t + 1.0;
        double dx = entity.getX() - origin.x;
        double dz = entity.getZ() - origin.z;
        return dx * dx + dz * dz <= allowedRadius * allowedRadius;
    }
}
