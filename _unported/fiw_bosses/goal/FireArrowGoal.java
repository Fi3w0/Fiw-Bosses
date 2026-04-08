package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Charges a fire arrow, then fires it toward the target.
 * On impact (or reaching max range) the arrow explodes in a burst of fire.
 *
 * JSON params:
 *   chargeTime      (int,    25)   ticks spent charging before firing
 *   damage          (float, 20.0)  base explosion damage
 *   explosionRadius (float,  4.0)  blast radius in blocks
 *   speed           (double, 2.5)  blocks per tick the arrow travels
 *   range           (double, 25.0) max travel distance before exploding
 */
public class FireArrowGoal extends Goal {

    private static final int STATE_CHARGE = 0;
    private static final int STATE_FLY    = 1;

    private final BossEntity boss;
    private final int chargeTime;
    private final float damage;
    private final float explosionRadius;
    private final double speed;
    private final double range;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private int state;
    private boolean active;

    private LivingEntity lockedTarget;
    private Vec3d arrowPos;
    private Vec3d arrowDir;
    private double traveled;

    public FireArrowGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.chargeTime      = params.has("chargeTime")      ? params.get("chargeTime").getAsInt()      : 25;
        this.damage          = params.has("damage")          ? params.get("damage").getAsFloat()          : 20.0f;
        this.explosionRadius = params.has("explosionRadius") ? params.get("explosionRadius").getAsFloat() : 4.0f;
        this.speed           = params.has("speed")           ? params.get("speed").getAsDouble()          : 2.5;
        this.range           = params.has("range")           ? params.get("range").getAsDouble()          : 25.0;
        this.cooldown        = cooldownTicks;
        this.cooldownTimer   = 0;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_CHARGE;
        active       = true;
        lockedTarget = boss.getTarget();
        traveled     = 0;
        arrowPos     = null;
        arrowDir     = null;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        if (state == STATE_CHARGE) {
            // Lock look at target
            if (lockedTarget != null) boss.getLookControl().lookAt(lockedTarget, 360, 90);

            // Swirling FLAME particles — radius shrinks as charge progresses
            double chargeProgress = (double) tick / chargeTime;
            double swirlR = 2.5 * (1.0 - chargeProgress) + 0.5;
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(i * 120.0 + tick * 18.0);
                double px = boss.getX() + Math.cos(angle) * swirlR;
                double pz = boss.getZ() + Math.sin(angle) * swirlR;
                world.spawnParticles(ParticleTypes.FLAME,
                        px, boss.getY() + 1.0, pz, 1, 0.05, 0.05, 0.05, 0.02);
            }
            // Center lava particle
            world.spawnParticles(ParticleTypes.LAVA,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0.1, 0.1, 0.1, 0.0);

            // Mid-charge pling
            if (tick == chargeTime / 2) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.HOSTILE, 1.0f, 2.0f);
            }

            // Fire the arrow
            if (tick >= chargeTime) {
                Vec3d origin = boss.getPos().add(0, 1.4, 0);
                Vec3d targetPos = lockedTarget != null
                        ? lockedTarget.getPos().add(0, 1.0, 0)
                        : boss.getPos().add(boss.getRotationVector().multiply(range));
                arrowDir = targetPos.subtract(origin).normalize();
                arrowPos = origin;
                traveled = 0;

                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.2f, 1.0f);
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.1f);

                state = STATE_FLY;
            }

        } else if (state == STATE_FLY) {
            int substeps = (int) Math.ceil(speed);
            double stepSize = speed / substeps;

            for (int s = 0; s < substeps; s++) {
                arrowPos = arrowPos.add(arrowDir.multiply(stepSize));
                traveled += stepSize;

                // Visual trail per substep
                world.spawnParticles(ParticleTypes.FLAME,
                        arrowPos.x, arrowPos.y, arrowPos.z, 3, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticles(ParticleTypes.LAVA,
                        arrowPos.x, arrowPos.y, arrowPos.z, 1, 0.05, 0.05, 0.05, 0.0);

                // Hit check — 1-block radius box
                Box hitBox = new Box(
                        arrowPos.x - 1.0, arrowPos.y - 1.0, arrowPos.z - 1.0,
                        arrowPos.x + 1.0, arrowPos.y + 1.0, arrowPos.z + 1.0);

                List<PlayerEntity> hit = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                        p -> p.isAlive());

                if (!hit.isEmpty()) {
                    explode(world, arrowPos);
                    active = false;
                    return;
                }

                if (traveled >= range) {
                    explode(world, arrowPos);
                    active = false;
                    return;
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }

    // ── Explosion ────────────────────────────────────────────────────────────

    private void explode(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);

        double spread = explosionRadius * 0.4;
        world.spawnParticles(ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 60, spread, spread, spread, 0.2);

        double lavaSpread = explosionRadius * 0.3;
        world.spawnParticles(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, 20, lavaSpread, lavaSpread, lavaSpread, 0.0);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 1.2f);

        // Damage players with distance falloff
        Box blastBox = new Box(
                pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius);

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, blastBox,
                p -> p.isAlive());

        for (PlayerEntity player : players) {
            double dist = player.getPos().distanceTo(pos);
            if (dist > explosionRadius) continue;

            float falloff = (float) (1.0 - dist / explosionRadius);
            player.damage(boss.getDamageSources().magic(), damage * falloff);

            // Knockback
            Vec3d knock = player.getPos().subtract(pos).normalize().multiply(1.5 * falloff);
            player.addVelocity(knock.x, knock.y, knock.z);
            player.velocityModified = true;
        }
    }
}
