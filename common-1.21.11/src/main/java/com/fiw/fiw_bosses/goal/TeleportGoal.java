package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TeleportGoal extends Goal {

    private final BossEntity boss;
    private final float minDistance;
    private final float maxDistance;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;

    public TeleportGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.minDistance = params.has("minDistance") ? params.get("minDistance").getAsFloat() : 8.0f;
        this.maxDistance = params.has("maxDistance") ? params.get("maxDistance").getAsFloat() : 20.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = boss.distanceTo(target);
        return dist >= minDistance;
    }

    @Override
    public void start() {
        performTeleport();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private void performTeleport() {
        if (boss.level().isClientSide()) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerLevel level = (ServerLevel) boss.level();

        level.sendParticles(ParticleTypes.PORTAL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                40, 0.4, 0.8, 0.4, 0.8);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                20, 0.3, 0.6, 0.3, 0.1);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.6f);

        double targetYaw = Math.toRadians(target.getYRot());
        double behindX = target.getX() + Math.sin(targetYaw) * 2.5;
        double behindZ = target.getZ() - Math.cos(targetYaw) * 2.5;

        BlockPos targetPos = BlockPos.containing(behindX, target.getY(), behindZ);
        boolean found = tryTeleportTo(level, targetPos);

        if (!found) {
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 2 + boss.getRandom().nextDouble() * 3;
                double rx = target.getX() + Math.cos(angle) * dist;
                double rz = target.getZ() + Math.sin(angle) * dist;
                BlockPos rp = BlockPos.containing(rx, target.getY(), rz);
                if (tryTeleportTo(level, rp)) {
                    found = true;
                    break;
                }
            }
        }

        if (found) {
            boss.getLookControl().setLookAt(target, 360, 90);

            level.sendParticles(ParticleTypes.PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    40, 0.4, 0.8, 0.4, 0.8);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    20, 0.3, 0.6, 0.3, 0.1);
            level.sendParticles(ParticleTypes.WITCH,
                    boss.getX(), boss.getY() + 0.5, boss.getZ(),
                    10, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 1.2f);

            String msg = taunt != null ? taunt : "&5Behind you...";
            if (boss.getRandom().nextFloat() < 0.4f) {
                var bossName = boss.getCustomName();
                Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                        .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                        .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(msg));
                for (var player : level.players()) {
                    if (player.distanceToSqr(boss) <= 48 * 48) {
                        player.sendSystemMessage(tauntText);
                    }
                }
            }
        }
    }

    private boolean tryTeleportTo(ServerLevel level, BlockPos basePos) {
        for (int dy = -2; dy <= 3; dy++) {
            BlockPos check = basePos.above(dy);
            if (level.isEmptyBlock(check) && level.isEmptyBlock(check.above())
                    && level.getBlockState(check.below()).isSolid()) {
                boss.teleportTo(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }
}
