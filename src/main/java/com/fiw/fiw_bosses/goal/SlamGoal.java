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
 * Guaranteed 100% activation slam — fires every cooldown with NO random chance roll.
 * Ideal as an ultimate, or paired with dodge for a guaranteed counter-attack.
 *
 * When triggered the boss:
 *   1. Freezes in place (MOVE + LOOK controls)
 *   2. Windup: dark energy gathering particles, rising rumble
 *   3. If target is far: instant shadow-step teleport beside them
 *   4. SLAM: ground-crack shockwave ring, AoE damage + knockback, screen-shake sound
 *   5. Aftershock: two additional smaller rings expand outward
 *
 * JSON params:
 *   radius        (float, 5.0)   AoE hit radius in blocks
 *   damage        (float, 20.0)  damage dealt at ground zero (falls off with distance)
 *   knockback     (float, 2.5)   radial knockback + upward launch strength
 *   windupTicks   (int,   16)    charge-up ticks before the slam lands
 *   teleportRange (float, 6.0)   if target is farther than this, shadow-step first
 *   taunt         (string, null) boss chat message at windup start
 */
public class SlamGoal extends Goal {

    private final BossEntity boss;
    private final float radius;
    private final float damage;
    private final float knockback;
    private final int cooldown;
    private final int windupTicks;
    private final float teleportRange;
    private final String taunt;

    private int cooldownTimer;
    private int tick;
    private boolean slamDone;

    // aftershock rings expand over 3 extra ticks after slam
    private static final int AFTERSHOCK_TICKS = 6;

    public SlamGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius       = params.has("radius")        ? params.get("radius").getAsFloat()        : 5.0f;
        this.damage       = params.has("damage")        ? params.get("damage").getAsFloat()         : 20.0f;
        this.knockback    = params.has("knockback")     ? params.get("knockback").getAsFloat()      : 2.5f;
        this.cooldown     = cooldownTicks;
        this.windupTicks  = params.has("windupTicks")   ? params.get("windupTicks").getAsInt()      : 16;
        this.teleportRange= params.has("teleportRange") ? params.get("teleportRange").getAsFloat()  : 6.0f;
        this.taunt        = params.has("taunt")         ? params.get("taunt").getAsString()         : null;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    // ── 100% guaranteed: only cooldown + target required ──────────────────────
    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return false;
        }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        slamDone = false;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            // Taunt
            if (taunt != null) {
                Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                        .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                        .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(taunt));
                for (var player : world.getPlayers()) {
                    if (player.squaredDistanceTo(boss) <= 48 * 48) {
                        player.sendMessage(tauntText, false);
                    }
                }
            }

            // Rising charge sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.4f);
        }
    }

    @Override
    public boolean shouldContinue() {
        return tick < windupTicks + AFTERSHOCK_TICKS;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        Vec3d bossPos = boss.getPos();

        // ── WINDUP PHASE ──────────────────────────────────────────────────────
        if (tick <= windupTicks) {
            float progress = (float) tick / windupTicks;

            // Dark energy vortex pulling in toward the boss
            int vortexCount = 3 + (int)(progress * 8);
            for (int i = 0; i < vortexCount; i++) {
                double angle = Math.toRadians(boss.getRandom().nextFloat() * 360);
                double dist = radius * (1.0 - progress * 0.6) + boss.getRandom().nextFloat();
                double px = bossPos.x + Math.cos(angle) * dist;
                double pz = bossPos.z + Math.sin(angle) * dist;
                double py = bossPos.y + 0.1 + boss.getRandom().nextFloat() * 1.5;
                world.spawnParticles(ParticleTypes.SOUL, px, py, pz, 1, 0, 0, 0, 0);
            }

            // Gathering dark orbs that orbit and spiral inward
            double spiralAngle = Math.toRadians(tick * 30);
            double spiralDist  = radius * (1.0 - progress * 0.7);
            for (int arm = 0; arm < 3; arm++) {
                double armAngle = spiralAngle + Math.toRadians(arm * 120);
                double px = bossPos.x + Math.cos(armAngle) * spiralDist;
                double pz = bossPos.z + Math.sin(armAngle) * spiralDist;
                double py = bossPos.y + 0.5 + progress * 0.8;
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, px, py, pz, 1, 0.05, 0.05, 0.05, 0.01);
            }

            // Ground warning ring — expands to full radius over windup
            int ringPoints = 24;
            double warningR = radius * 0.2 + radius * 0.8 * progress;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.toRadians((360.0 / ringPoints) * i);
                double px = bossPos.x + Math.cos(angle) * warningR;
                double pz = bossPos.z + Math.sin(angle) * warningR;
                world.spawnParticles(ParticleTypes.SMOKE,
                        px, bossPos.y + 0.05, pz, 1, 0, 0, 0, 0);
            }

            // Halfway buildup — escalate sound
            if (tick == windupTicks / 2) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, SoundCategory.HOSTILE, 1.2f, 0.6f);
            }
        }

        // ── SLAM ──────────────────────────────────────────────────────────────
        if (tick == windupTicks && !slamDone) {
            slamDone = true;
            performSlam(world, bossPos);
        }

        // ── AFTERSHOCK RINGS ──────────────────────────────────────────────────
        if (tick > windupTicks) {
            int afterTick = tick - windupTicks;
            double ringProgress = (double) afterTick / AFTERSHOCK_TICKS;

            // Two expanding rings at different speeds
            for (int ring = 0; ring < 2; ring++) {
                double ringR = radius * (0.3 + ring * 0.4 + ringProgress * (0.7 - ring * 0.2));
                int count = 20 + ring * 8;
                for (int i = 0; i < count; i++) {
                    double angle = Math.toRadians((360.0 / count) * i);
                    double px = bossPos.x + Math.cos(angle) * ringR;
                    double pz = bossPos.z + Math.sin(angle) * ringR;
                    world.spawnParticles(ring == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                            px, bossPos.y + 0.1, pz, 1, 0, 0.05, 0, 0);
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void performSlam(ServerWorld world, Vec3d bossPos) {
        // Shadow-step teleport if target is too far
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            double dist = boss.distanceTo(target);
            if (dist > teleportRange) {
                Vec3d dir = target.getPos().subtract(bossPos).normalize();
                double tx = target.getX() - dir.x * 1.8;
                double tz = target.getZ() - dir.z * 1.8;
                boss.teleport(tx, target.getY(), tz);
                bossPos = boss.getPos();

                // Shadow-step smoke burst
                world.spawnParticles(ParticleTypes.POOF, bossPos.x, bossPos.y + 1, bossPos.z,
                        12, 0.4, 0.5, 0.4, 0.05);
                world.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
        }

        // SLAM sounds — layered
        world.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5f, 0.5f);
        world.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.0f, 0.3f);
        world.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.7f);

        Vec3d center = bossPos;

        // AoE damage + radial knockback
        Box aoeBox = boss.getBoundingBox().expand(radius);
        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, aoeBox,
                e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

        for (LivingEntity entity : entities) {
            double dist = entity.distanceTo(boss);
            if (dist <= radius) {
                float falloff = 1.0f - (float)(dist / radius) * 0.4f;
                entity.damage(boss.getDamageSources().mobAttack(boss), damage * falloff);

                Vec3d dir = entity.getPos().subtract(center).normalize();
                double yLaunch = 0.5 + (1.0 - dist / radius) * 0.6;
                entity.addVelocity(dir.x * knockback, yLaunch, dir.z * knockback);
                entity.velocityModified = true;
            }
        }

        // ── Impact particles ──────────────────────────────────────────────────

        // Ground crack rings (3 concentric)
        for (int ring = 0; ring < 3; ring++) {
            double r = radius * (0.25 + ring * 0.3);
            int count = 12 + ring * 8;
            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians((360.0 / count) * i);
                double px = center.x + Math.cos(angle) * r;
                double pz = center.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.EXPLOSION, px, center.y + 0.05, pz, 1, 0, 0, 0, 0);
            }
        }

        // Central impact flash
        world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);

        // Upward debris column
        world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                center.x, center.y, center.z, 8, radius * 0.3, 0.1, radius * 0.3, 0.08);

        // Dark soul fragments scatter
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.2, center.z, 16, radius * 0.35, 0.4, radius * 0.35, 0.12);

        // Lava embers fling out
        world.spawnParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.1, center.z, 6, radius * 0.2, 0.0, radius * 0.2, 0.0);
    }
}
