package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A fast forward-traveling slash that follows a straight path from the boss.
 * Spawns a vertical blade of particles that advances each tick, dealing damage
 * to entities caught in its path. Players must dodge sideways to survive.
 *
 * JSON params:
 *   damage       (float, 14.0)   damage on hit
 *   speed        (float,  1.8)   blocks per tick the slash advances
 *   range        (float, 20.0)   max distance it travels
 *   width        (float,  1.4)   half-width of the hitbox perpendicular to travel
 *   chargeTime   (int,    10)    windup ticks before firing
 *   taunt        (string, null)
 */
public class SlashWaveGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final float speed;
    private final float range;
    private final float width;
    private final int chargeTime;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;

    private Vec3d origin;
    private Vec3d forward;       // unit vector in the direction of travel
    private Vec3d right;         // perpendicular unit vector (for blade width)
    private double traveled;     // total blocks traveled so far
    private Vec3d slashPos;      // current front of the slash

    private final Set<UUID> alreadyHit = new HashSet<>();

    private static final int BLADE_HEIGHT = 3;    // how many vertical "layers" to draw

    public SlashWaveGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()     : 14.0f;
        this.speed      = params.has("speed")      ? params.get("speed").getAsFloat()      : 1.8f;
        this.range      = params.has("range")      ? params.get("range").getAsFloat()      : 20.0f;
        this.width      = params.has("width")      ? params.get("width").getAsFloat()      : 1.4f;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()   : 10;
        this.taunt      = params.has("taunt")      ? params.get("taunt").getAsString()     : null;
        this.cooldown   = cooldownTicks;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= range + 5;
    }

    @Override
    public void start() {
        tick = 0;
        traveled = 0;
        alreadyHit.clear();

        // Lock orientation toward target at start of charge
        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);
        lockOrientation();

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Charge sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.25f);

            if (taunt != null) {
                var bossName = boss.getCustomName();
                net.minecraft.text.Text text = net.minecraft.text.Text.literal("[")
                        .formatted(net.minecraft.util.Formatting.DARK_GRAY)
                        .append(bossName != null ? bossName.copy() : net.minecraft.text.Text.literal("Boss"))
                        .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.DARK_GRAY))
                        .append(com.fiw.fiw_bosses.util.TextUtil.parseColorCodes(taunt));
                for (var p : world.getPlayers()) {
                    if (p.squaredDistanceTo(boss) <= 48 * 48) p.sendMessage(text, false);
                }
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return tick < chargeTime || traveled < range;
    }

    @Override
    public void tick() {
        tick++;
        boss.setVelocity(0, boss.getVelocity().y, 0);
        boss.velocityModified = true;

        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // ── CHARGE ────────────────────────────────────────────────────────────
        if (tick <= chargeTime) {
            float t = (float) tick / chargeTime;
            // Keep tracking target during charge; lock on final charge tick
            LivingEntity target = boss.getTarget();
            if (target != null && tick < chargeTime) {
                boss.getLookControl().lookAt(target, 360, 90);
                lockOrientation();
            }

            // Energy gathering particles at boss hand level
            Vec3d handPos = origin.add(forward.multiply(0.8)).add(0, 1.1, 0);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    handPos.x, handPos.y, handPos.z,
                    (int)(1 + t * 6), 0.15, 0.2, 0.15, 0.08);
            world.spawnParticles(ParticleTypes.CRIT,
                    handPos.x, handPos.y, handPos.z,
                    2, 0.1, 0.15, 0.1, 0.1);

            if (tick == chargeTime) {
                // Fire! Slash whoosh sound
                world.playSound(null, origin.x, origin.y, origin.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.5f, 1.4f);
                world.playSound(null, origin.x, origin.y, origin.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.5f, 1.1f);
                slashPos = origin.add(forward.multiply(0.5));
            }
            return;
        }

        // ── TRAVEL ────────────────────────────────────────────────────────────
        // Advance the slash tip each tick
        Vec3d prevPos = slashPos;
        slashPos = slashPos.add(forward.multiply(speed));
        traveled += speed;

        // Draw the blade as a vertical fan of particles at the current tip
        for (int layer = 0; layer < BLADE_HEIGHT; layer++) {
            double layerY = slashPos.y + 0.3 + layer * 0.65;

            // Center point + two side points for blade width
            for (double side = -width; side <= width; side += width) {
                double px = slashPos.x + right.x * side;
                double pz = slashPos.z + right.z * side;

                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, layerY, pz, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.CRIT, px, layerY, pz, 3, 0.1, 0.1, 0.1, 0.18);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, px, layerY, pz, 2, 0.08, 0.08, 0.08, 0.1);
            }
        }

        // Trail smoke behind the slash
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                prevPos.x, prevPos.y + 1.0, prevPos.z,
                2, 0.1, 0.4, 0.1, 0.02);

        // Flash at mid-range
        if (traveled >= range * 0.5 - speed && traveled <= range * 0.5 + speed) {
            world.spawnParticles(ParticleTypes.FLASH,
                    slashPos.x, slashPos.y + 1.0, slashPos.z, 1, 0, 0, 0, 0);
        }

        // ── HIT DETECTION ─────────────────────────────────────────────────────
        Box hitBox = new Box(
                slashPos.x - width - 0.3, slashPos.y - 0.3, slashPos.z - width - 0.3,
                slashPos.x + width + 0.3, slashPos.y + BLADE_HEIGHT * 0.65 + 0.5, slashPos.z + width + 0.3);

        List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, hitBox,
                e -> e != boss && e.isAlive() && !boss.isMinion(e) && !alreadyHit.contains(e.getUuid()));

        for (LivingEntity victim : victims) {
            victim.damage(boss.getDamageSources().mobAttack(boss), damage);
            alreadyHit.add(victim.getUuid());

            // Knockback sideways (perpendicular to slash direction adds disorientation)
            Vec3d knock = victim.getPos().subtract(slashPos);
            double sideComponent = knock.dotProduct(right);
            Vec3d knockDir = (sideComponent >= 0 ? right : right.negate())
                    .add(forward.multiply(0.4)).normalize();
            victim.addVelocity(knockDir.x * 0.9, 0.5, knockDir.z * 0.9);
            victim.velocityModified = true;

            world.spawnParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(),
                    14, 0.5, 0.4, 0.5, 0.28);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(),
                    1, 0, 0, 0, 0);
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }

        // End burst when slash reaches max range
        if (traveled >= range) {
            world.spawnParticles(ParticleTypes.FLASH,
                    slashPos.x, slashPos.y + 1.0, slashPos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CRIT,
                    slashPos.x, slashPos.y + 1.0, slashPos.z,
                    10, 0.5, 0.6, 0.5, 0.3);
            world.playSound(null, slashPos.x, slashPos.y, slashPos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void lockOrientation() {
        Vec3d fwd = boss.getRotationVec(1.0f);
        forward = new Vec3d(fwd.x, 0, fwd.z).normalize();
        right   = new Vec3d(-forward.z, 0, forward.x);
        origin  = boss.getPos();
        slashPos = origin;
    }
}
