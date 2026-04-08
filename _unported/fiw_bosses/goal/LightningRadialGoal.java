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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LightningRadialGoal extends Goal {

    private static final int STATE_JUMP    = 0;
    private static final int STATE_CHANNEL = 1;
    private static final int STATE_FIRE    = 2;

    // ── Inner class ──────────────────────────────────────────────────────────

    private static class BladeTracker {
        Vec3d pos;
        final Vec3d dir;
        double traveled;
        boolean done;
        final Set<UUID> hitSet = new HashSet<>();

        BladeTracker(Vec3d pos, Vec3d dir) {
            this.pos  = pos;
            this.dir  = dir;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private final BossEntity boss;
    private final int    bladeCount;
    private final double bladeRange;
    private final float  damage;
    private final int    channelTime;
    private final double bladeSpeed;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;

    private double jumpOriginY;
    private double bladeGroundY;
    private double spiralAngle;
    private List<BladeTracker> blades;

    // ── Constructor ──────────────────────────────────────────────────────────

    public LightningRadialGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.bladeCount  = params.has("bladeCount")  ? params.get("bladeCount").getAsInt()    : 20;
        this.bladeRange  = params.has("bladeRange")  ? params.get("bladeRange").getAsDouble() : 12.0;
        this.damage      = params.has("damage")      ? params.get("damage").getAsFloat()      : 16f;
        this.channelTime = params.has("channelTime") ? params.get("channelTime").getAsInt()   : 16;
        this.bladeSpeed  = params.has("bladeSpeed")  ? params.get("bladeSpeed").getAsDouble() : 1.5;
        this.cooldown    = cooldownTicks;
        this.cooldownTimer = 0;
        this.blades      = new ArrayList<>();
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    // ── Goal lifecycle ───────────────────────────────────────────────────────

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_JUMP;
        active       = true;
        blades.clear();
        spiralAngle  = 0.0;
        jumpOriginY  = boss.getY();

        boss.addVelocity(0, 0.6, 0);
        boss.velocityModified = true;
        boss.setInvulnerable(true);

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.5f, 1.5f);
        }
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void tick() {
        tick++;
        spiralAngle += 18.0;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        switch (state) {
            case STATE_JUMP    -> tickJump(world);
            case STATE_CHANNEL -> tickChannel(world);
            case STATE_FIRE    -> tickFire(world);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
        boss.setInvulnerable(false);
    }

    // ── State handlers ───────────────────────────────────────────────────────

    private void tickJump(ServerWorld world) {
        double cx = boss.getX();
        double cy = boss.getY() + 1.0;
        double cz = boss.getZ();

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                cx, cy, cz, 4, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.END_ROD,
                cx, cy, cz, 2, 0.2, 0.2, 0.2, 0.05);

        if (tick >= 8 || boss.getVelocity().y <= 0) {
            bladeGroundY = jumpOriginY + 0.3;
            boss.setInvulnerable(false);
            state = STATE_CHANNEL;
            tick  = 0;
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 1.2f, 0.5f);
        }
    }

    private void tickChannel(ServerWorld world) {
        double cx = boss.getX();
        double cy = boss.getY();
        double cz = boss.getZ();

        double spiralR = 0.5 + (double) tick / channelTime * 2.5;

        // 6 spiral points
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60.0 + spiralAngle);
            double px = cx + Math.cos(angle) * spiralR;
            double pz = cz + Math.sin(angle) * spiralR;
            double py = cy + 1.0;

            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    px, py, pz, 2, 0.1, 0.2, 0.1, 0.1);
            world.spawnParticles(ParticleTypes.END_ROD,
                    px, py, pz, 1, 0, 0, 0, 0.02);
        }

        // Boss center particles
        world.spawnParticles(ParticleTypes.END_ROD,
                cx, cy + 1.0, cz, 3, 0.2, 0.3, 0.2, 0.05);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                cx, cy + 1.0, cz, 5, 0.3, 0.3, 0.3, 0.1);

        if (tick >= channelTime) {
            // Launch blades
            Vec3d origin = new Vec3d(boss.getX(), bladeGroundY, boss.getZ());
            for (int i = 0; i < bladeCount; i++) {
                double angle = 2.0 * Math.PI * i / bladeCount;
                Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
                blades.add(new BladeTracker(origin, dir));
            }

            state = STATE_FIRE;
            tick  = 0;

            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 1.5f);
        }
    }

    private void tickFire(ServerWorld world) {
        boolean anyAlive = false;

        for (BladeTracker blade : blades) {
            if (blade.done) continue;
            anyAlive = true;

            int    steps    = Math.max(1, (int) Math.ceil(bladeSpeed));
            double stepDist = bladeSpeed / steps;

            for (int s = 0; s < steps; s++) {
                blade.pos      = blade.pos.add(blade.dir.multiply(stepDist));
                blade.traveled += stepDist;

                double bx = blade.pos.x;
                double by = bladeGroundY;
                double bz = blade.pos.z;

                // Blade visuals
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        bx, by + 0.2, bz, 2, 0.1, 0.1, 0.1, 0.08);
                world.spawnParticles(ParticleTypes.END_ROD,
                        bx, by + 0.3, bz, 1, 0.05, 0.15, 0.05, 0.02);

                // Ash trail every ~2 blocks
                if (blade.traveled % 2.0 < stepDist) {
                    world.spawnParticles(ParticleTypes.ASH,
                            bx, by + 0.5, bz, 3, 0.3, 0.3, 0.3, 0.05);
                }

                // Hit detection box
                Box hitBox = new Box(
                        bx - 0.7, by - 0.5, bz - 0.7,
                        bx + 0.7, by + 2.5, bz + 0.7);
                List<PlayerEntity> victims = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                        p -> p.isAlive() && !blade.hitSet.contains(p.getUuid()));

                for (PlayerEntity player : victims) {
                    if (blade.hitSet.contains(player.getUuid())) continue;
                    blade.hitSet.add(player.getUuid());
                    player.damage(boss.getDamageSources().magic(), damage);
                    player.addVelocity(blade.dir.x * 1.2, 0.3, blade.dir.z * 1.2);
                    player.velocityModified = true;
                    world.spawnParticles(ParticleTypes.ASH,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            8, 0.3, 0.5, 0.3, 0.1);
                }

                // Blade expired
                if (blade.traveled >= bladeRange) {
                    blade.done = true;
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            bx, by + 0.3, bz, 5, 0.3, 0.3, 0.3, 0.1);
                    break;
                }
            }
        }

        if (!anyAlive) {
            active = false;
        }
    }
}
