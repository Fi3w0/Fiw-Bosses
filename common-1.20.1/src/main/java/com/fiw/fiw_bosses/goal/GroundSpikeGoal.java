package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private int phase;

    private final List<Vec3> spikePositions = new ArrayList<>();
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
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 8;
    }

    @Override
    public boolean canContinueToUse() {
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
        Vec3 center = (target != null && target.isAlive())
                ? target.position()
                : boss.position().add(boss.getLookAngle().scale(radius * 0.5));

        int innerCount = spikeCount / 3;
        int outerCount = spikeCount - innerCount;

        double innerRadius = radius * 0.4;
        double outerRadius = radius * 0.85;

        for (int i = 0; i < innerCount; i++) {
            double angle = Math.toRadians(360.0 / innerCount * i);
            double sx = center.x + Math.cos(angle) * innerRadius;
            double sz = center.z + Math.sin(angle) * innerRadius;
            spikePositions.add(new Vec3(sx, center.y, sz));
        }

        for (int i = 0; i < outerCount; i++) {
            double angle = Math.toRadians(360.0 / outerCount * i + (360.0 / outerCount / 2.0));
            double sx = center.x + Math.cos(angle) * outerRadius;
            double sz = center.z + Math.sin(angle) * outerRadius;
            spikePositions.add(new Vec3(sx, center.y, sz));
        }

        if (!boss.level().isClientSide) {
            boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 0.4f);
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (phase == 0) {
            double pulseRadius = 0.5 + 0.3 * Math.sin(tick * 0.3);
            DustParticleOptions dustRed = new DustParticleOptions(Colors.rgb(0.8f, 0.2f, 0.1f), 1.0f);
            BlockParticleOption stoneBlock = new BlockParticleOption(
                    ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());

            for (Vec3 pos : spikePositions) {
                int ringPoints = 10;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.toRadians(360.0 / ringPoints * i + tick * 8.0);
                    double px = pos.x + Math.cos(angle) * pulseRadius;
                    double pz = pos.z + Math.sin(angle) * pulseRadius;
                    level.sendParticles(ParticleTypes.CRIT,
                            px, pos.y + 0.05, pz, 1, 0.05, 0.0, 0.05, 0.05);
                    level.sendParticles(dustRed,
                            px, pos.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }

                if (tick % 5 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double rx = pos.x + (level.getRandom().nextDouble() - 0.5) * 0.8;
                        double rz = pos.z + (level.getRandom().nextDouble() - 0.5) * 0.8;
                        level.sendParticles(stoneBlock,
                                rx, pos.y + 0.05, rz, 1, 0.1, 0.05, 0.1, 0.05);
                    }
                }
            }

            if (tick % 10 == 0) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 0.5f, 0.8f);
            }

            if (tick == markTicks - 5) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.4f);
            }

            if (tick >= markTicks) {
                phase = 1;
            }

        } else if (phase == 1) {
            if (tick >= markTicks + spikeTicks) {
                active = false;
                return;
            }

            if (tick == markTicks) {
                BlockParticleOption stoneBlock = new BlockParticleOption(
                        ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());

                for (Vec3 pos : spikePositions) {
                    for (int col = 0; col < 4; col++) {
                        BlockPos bp = BlockPos.containing(pos.x, pos.y + col, pos.z);
                        BlockState savedState = level.getBlockState(bp);
                        BlockState spikeState = col % 2 == 0
                                ? Blocks.STONE.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState();
                        level.setBlock(bp, spikeState, 2);
                        FallingBlockEntity blockEnt = FallingBlockEntity.fall(level, bp, spikeState);
                        level.setBlock(bp, savedState, 2);
                        blockEnt.setDeltaMovement(0, 0.8 + col * 0.15, 0);
                        blockEnt.dropItem = false;
                    }

                    level.sendParticles(ParticleTypes.CRIT,
                            pos.x, pos.y + 0.1, pos.z, 10, 0.5, 0.2, 0.5, 0.3);
                    level.sendParticles(stoneBlock,
                            pos.x, pos.y + 0.1, pos.z, 5, 0.5, 0.2, 0.5, 0.3);
                    level.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
                }

                for (Vec3 pos : spikePositions) {
                    AABB hitBox = new AABB(
                            pos.x - 1.5, pos.y - 0.5, pos.z - 1.5,
                            pos.x + 1.5, pos.y + 4.0, pos.z + 1.5);

                    List<Player> players = level.getEntitiesOfClass(Player.class, hitBox,
                            p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                                    && !hitPlayers.contains(p.getUUID()));

                    for (Player player : players) {
                        hitPlayers.add(player.getUUID());
                        player.hurt(boss.damageSources().magic(), damage);
                        player.push(0, knockback, 0);
                        player.hurtMarked = true;
                        level.sendParticles(ParticleTypes.CRIT,
                                player.getX(), player.getY() + 0.5, player.getZ(),
                                8, 0.4, 0.3, 0.4, 0.2);
                    }
                }

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8f, 0.7f);
            }

            DustParticleOptions dustRed = new DustParticleOptions(Colors.rgb(0.8f, 0.2f, 0.1f), 1.0f);
            for (Vec3 pos : spikePositions) {
                for (int h = 0; h <= 2; h++) {
                    level.sendParticles(ParticleTypes.CRIT,
                            pos.x, pos.y + h + 0.5, pos.z, 1, 0.3, 0.1, 0.3, 0.05);
                    level.sendParticles(dustRed,
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
