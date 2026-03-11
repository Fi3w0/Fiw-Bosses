package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
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

public class BeamGoal extends Goal {

    private final BossEntity boss;
    private final float damage;      // damage per tick while inside beam
    private final float width;       // beam cylinder half-width (hitbox)
    private final int duration;      // ticks beam fires after windup
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int beamTick;
    // Locked target position — beam stays aimed at where the target was at fire time
    private Vec3d lockedDest;
    private static final int WINDUP_TICKS = 20;

    public BeamGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 3.0f;
        this.width = params.has("width") ? params.get("width").getAsFloat() : 0.9f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 50;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        // MOVE + LOOK: boss freezes in place and locks eyes on target
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.squaredDistanceTo(target) >= 3 * 3
                && boss.squaredDistanceTo(target) <= 35 * 35;
    }

    @Override
    public void start() {
        beamTick = -WINDUP_TICKS;
        lockedDest = null;

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().lookAt(target, 360, 90);

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Charging-up sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 1.0f);

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
        }
    }

    @Override
    public boolean shouldContinue() {
        return beamTick < duration;
    }

    @Override
    public void tick() {
        beamTick++;

        // Freeze boss in place during windup and beam
        boss.setVelocity(0, boss.getVelocity().y, 0);
        boss.velocityModified = true;

        LivingEntity target = boss.getTarget();
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        Vec3d origin = new Vec3d(boss.getX(), boss.getEyeY() - 0.2, boss.getZ());

        // ── WINDUP ──────────────────────────────────────────────────────────
        if (beamTick <= 0) {
            if (target != null) boss.getLookControl().lookAt(target, 360, 90);

            // Energy gathers at eyes — spiraling particles contracting inward
            float progress = (float)(WINDUP_TICKS + beamTick) / WINDUP_TICKS; // 0→1
            double gatherRadius = 2.5 * (1.0 - progress);
            int gatherCount = 6;
            for (int i = 0; i < gatherCount; i++) {
                double angle = Math.toRadians((360.0 / gatherCount) * i + beamTick * 15);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        origin.x + Math.cos(angle) * gatherRadius,
                        origin.y + Math.sin(angle) * gatherRadius * 0.4,
                        origin.z + Math.sin(angle) * gatherRadius,
                        1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.END_ROD,
                    origin.x, origin.y, origin.z,
                    2 + (int)(progress * 4), 0.08, 0.08, 0.08, 0.0);

            // Lock target position the moment windup ends
            if (beamTick == 0 && target != null) {
                lockedDest = new Vec3d(target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ());
                boss.getLookControl().lookAt(lockedDest.x, lockedDest.y, lockedDest.z, 360, 90);
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.8f, 0.6f);
            }
            return;
        }

        // ── BEAM FIRING ─────────────────────────────────────────────────────
        if (lockedDest == null) return;

        Vec3d dest = lockedDest;
        Vec3d dir = dest.subtract(origin).normalize();
        double beamLen = origin.distanceTo(dest);

        // Outer soft glow layer (sparse, slight spread)
        int outerSteps = (int) Math.ceil(beamLen * 5);
        for (int s = 0; s <= outerSteps; s++) {
            double t = (double) s / outerSteps;
            double px = origin.x + dir.x * beamLen * t;
            double py = origin.y + dir.y * beamLen * t;
            double pz = origin.z + dir.z * beamLen * t;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 2, 0.1, 0.1, 0.1, 0.0);
        }

        // Inner bright core (dense, zero spread — these make it look like a solid beam)
        int coreSteps = (int) Math.ceil(beamLen * 10);
        for (int s = 0; s <= coreSteps; s++) {
            double t = (double) s / coreSteps;
            double px = origin.x + dir.x * beamLen * t;
            double py = origin.y + dir.y * beamLen * t;
            double pz = origin.z + dir.z * beamLen * t;
            world.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
        }

        // Muzzle flash at beam origin
        world.spawnParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);

        // Impact burst at destination
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                dest.x, dest.y, dest.z, 6, 0.25, 0.25, 0.25, 0.1);
        world.spawnParticles(ParticleTypes.END_ROD,
                dest.x, dest.y, dest.z, 2, 0.15, 0.15, 0.15, 0.05);

        // Damage check — hit any player inside the beam cylinder
        Box broad = new Box(
                Math.min(origin.x, dest.x) - width, Math.min(origin.y, dest.y) - 1.5,
                Math.min(origin.z, dest.z) - width,
                Math.max(origin.x, dest.x) + width, Math.max(origin.y, dest.y) + 1.5,
                Math.max(origin.z, dest.z) + width
        );
        List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, broad,
                p -> p.isAlive() && isOnBeam(
                        new Vec3d(p.getX(), p.getY() + p.getHeight() / 2.0, p.getZ()),
                        origin, dir, beamLen));

        for (PlayerEntity victim : victims) {
            victim.damage(boss.getDamageSources().magic(), damage);
            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    4, 0.2, 0.2, 0.2, 0.05);
        }

        // Looping beam hum
        if (beamTick % 8 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 0.6f, 1.3f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Beam shutdown burst
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    boss.getX(), boss.getEyeY() - 0.2, boss.getZ(),
                    20, 0.4, 0.4, 0.4, 0.15);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
    }

    /** Returns true if point is within width of the beam line segment. */
    private boolean isOnBeam(Vec3d point, Vec3d origin, Vec3d dir, double len) {
        Vec3d toPoint = point.subtract(origin);
        double proj = toPoint.dotProduct(dir);
        if (proj < 0 || proj > len) return false;
        Vec3d closest = origin.add(dir.multiply(proj));
        return closest.squaredDistanceTo(point) <= width * width;
    }
}
