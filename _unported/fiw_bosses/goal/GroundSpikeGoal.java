package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Boss marks an area with pulsing indicators, then FallingBlockEntity spikes
 * erupt from the ground sending players upward. Visually solid block entities,
 * no terrain modification.
 *
 * JSON params:
 *   radius        (double, 10.0)  radius around target to distribute spikes
 *   spikeCount    (int,     8)    number of spike columns
 *   damage        (float,  12.0)  damage dealt to players in spike area
 *   knockback     (float,   2.5)  upward knockback on hit
 *   markTicks     (int,    40)    ticks spent in mark/warning phase
 *   spikeTicks    (int,    20)    ticks the spike phase lasts
 *   cooldownTicks (int,   100)    cooldown between uses
 */
public class GroundSpikeGoal extends Goal {

    private final BossEntity boss;
    private final double radius;
    private final int spikeCount;
    private final float damage;
    private final float knockback;
    private final int markTicks;
    private final int spikeTicks;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private int phase; // 0=mark, 1=spike, 2=done

    private final List<Vec3d> spikePositions = new ArrayList<>();
    private final Set<UUID> hitPlayers = new HashSet<>();

    public GroundSpikeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.radius     = params.has("radius")     ? params.get("radius").getAsDouble()    : 10.0;
        this.spikeCount = params.has("spikeCount") ? params.get("spikeCount").getAsInt()   : 8;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()     : 12.0f;
        this.knockback  = params.has("knockback")  ? params.get("knockback").getAsFloat()  : 2.5f;
        this.markTicks  = params.has("markTicks")  ? params.get("markTicks").getAsInt()    : 40;
        this.spikeTicks = params.has("spikeTicks") ? params.get("spikeTicks").getAsInt()   : 20;
        this.cooldown   = cooldownTicks;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 8;
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        phase = 0;
        spikePositions.clear();
        hitPlayers.clear();

        LivingEntity target = boss.getTarget();
        Vec3d center = (target != null && target.isAlive())
                ? target.getPos()
                : boss.getPos().add(boss.getRotationVector().multiply(radius * 0.5));

        // Distribute spike positions in two concentric rings
        int innerCount = spikeCount / 3;
        int outerCount = spikeCount - innerCount;

        double innerRadius = radius * 0.4;
        double outerRadius = radius * 0.85;

        for (int i = 0; i < innerCount; i++) {
            double angle = Math.toRadians(360.0 / innerCount * i);
            double sx = center.x + Math.cos(angle) * innerRadius;
            double sz = center.z + Math.sin(angle) * innerRadius;
            spikePositions.add(new Vec3d(sx, center.y, sz));
        }

        for (int i = 0; i < outerCount; i++) {
            double angle = Math.toRadians(360.0 / outerCount * i + (360.0 / outerCount / 2.0));
            double sx = center.x + Math.cos(angle) * outerRadius;
            double sz = center.z + Math.sin(angle) * outerRadius;
            spikePositions.add(new Vec3d(sx, center.y, sz));
        }

        if (!boss.getWorld().isClient) {
            boss.getWorld().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.0f, 0.4f);
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 30, 30);
        }

        // ── MARK PHASE ───────────────────────────────────────────────────────
        if (phase == 0) {
            double pulseRadius = 0.5 + 0.3 * Math.sin(tick * 0.3);
            DustParticleEffect dustRed = new DustParticleEffect(new Vector3f(0.8f, 0.2f, 0.1f), 1.0f);
            BlockStateParticleEffect stoneBlock = new BlockStateParticleEffect(
                    ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());

            for (Vec3d pos : spikePositions) {
                // Pulsing ring of CRIT + dust particles around each spike position
                int ringPoints = 10;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.toRadians(360.0 / ringPoints * i + tick * 8.0);
                    double px = pos.x + Math.cos(angle) * pulseRadius;
                    double pz = pos.z + Math.sin(angle) * pulseRadius;
                    world.spawnParticles(ParticleTypes.CRIT,
                            px, pos.y + 0.05, pz, 1, 0.05, 0.0, 0.05, 0.05);
                    world.spawnParticles(dustRed,
                            px, pos.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // Ground crack effect every 5 ticks
                if (tick % 5 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double rx = pos.x + (world.getRandom().nextDouble() - 0.5) * 0.8;
                        double rz = pos.z + (world.getRandom().nextDouble() - 0.5) * 0.8;
                        world.spawnParticles(stoneBlock,
                                rx, pos.y + 0.05, rz, 1, 0.1, 0.05, 0.1, 0.05);
                    }
                }
            }

            // Rumble sound every 10 ticks
            if (tick % 10 == 0) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 0.5f, 0.8f);
            }

            // Warning sound just before spike eruption
            if (tick == markTicks - 5) {
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 0.4f);
            }

            if (tick >= markTicks) {
                phase = 1;
            }

        // ── SPIKE PHASE ──────────────────────────────────────────────────────
        } else if (phase == 1) {
            if (tick >= markTicks + spikeTicks) {
                active = false;
                return;
            }

            // First tick of spike phase: spawn all the falling block columns
            if (tick == markTicks) {
                BlockStateParticleEffect stoneBlock = new BlockStateParticleEffect(
                        ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());

                for (Vec3d pos : spikePositions) {
                    // Spawn a 4-block-tall spike column using FallingBlockEntity.
                    // spawnFromBlock() calls the private constructor and spawns the entity.
                    // We save/restore the block state at each position so terrain is unchanged.
                    for (int col = 0; col < 4; col++) {
                        BlockPos bp = BlockPos.ofFloored(pos.x, pos.y + col, pos.z);
                        BlockState savedState = world.getBlockState(bp);
                        BlockState spikeState = col % 2 == 0
                                ? Blocks.STONE.getDefaultState()
                                : Blocks.COBBLESTONE.getDefaultState();
                        // Temporarily place the spike block so spawnFromBlock can read it
                        world.setBlockState(bp, spikeState, 2);
                        FallingBlockEntity blockEnt = FallingBlockEntity.spawnFromBlock(world, bp, spikeState);
                        // Restore the original block state immediately (no permanent terrain change)
                        world.setBlockState(bp, savedState, 2);
                        blockEnt.setVelocity(0, 0.8 + col * 0.15, 0);
                        blockEnt.dropItem = false;
                        blockEnt.setDestroyedOnLanding();
                    }

                    // Burst particles at base of each spike
                    world.spawnParticles(ParticleTypes.CRIT,
                            pos.x, pos.y + 0.1, pos.z, 10, 0.5, 0.2, 0.5, 0.3);
                    world.spawnParticles(stoneBlock,
                            pos.x, pos.y + 0.1, pos.z, 5, 0.5, 0.2, 0.5, 0.3);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.6f);
                }

                // Damage and launch players caught in the spike area
                for (Vec3d pos : spikePositions) {
                    Box hitBox = new Box(
                            pos.x - 1.5, pos.y - 0.5, pos.z - 1.5,
                            pos.x + 1.5, pos.y + 4.0, pos.z + 1.5);

                    List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, hitBox,
                            p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                                    && !hitPlayers.contains(p.getUuid()));

                    for (PlayerEntity player : players) {
                        hitPlayers.add(player.getUuid());
                        player.damage(boss.getDamageSources().magic(), damage);
                        player.addVelocity(0, knockback, 0);
                        player.velocityModified = true;
                        world.spawnParticles(ParticleTypes.CRIT,
                                player.getX(), player.getY() + 0.5, player.getZ(),
                                8, 0.4, 0.3, 0.4, 0.2);
                    }
                }

                // Overall eruption sound
                world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.8f, 0.7f);
            }

            // Maintain crack/glow particles during spike phase
            DustParticleEffect dustRed = new DustParticleEffect(new Vector3f(0.8f, 0.2f, 0.1f), 1.0f);
            for (Vec3d pos : spikePositions) {
                for (int h = 0; h <= 2; h++) {
                    world.spawnParticles(ParticleTypes.CRIT,
                            pos.x, pos.y + h + 0.5, pos.z, 1, 0.3, 0.1, 0.3, 0.05);
                    world.spawnParticles(dustRed,
                            pos.x, pos.y + h + 0.5, pos.z, 1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
