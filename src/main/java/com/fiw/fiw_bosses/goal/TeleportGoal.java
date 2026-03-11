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
import net.minecraft.util.math.BlockPos;

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
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
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
    public boolean shouldContinue() {
        return false;
    }

    private void performTeleport() {
        if (boss.getWorld().isClient) return;

        LivingEntity target = boss.getTarget();
        if (target == null) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        // Origin effects — dark particles + sound
        world.spawnParticles(ParticleTypes.PORTAL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                40, 0.4, 0.8, 0.4, 0.8);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                boss.getX(), boss.getY() + 1.0, boss.getZ(),
                20, 0.3, 0.6, 0.3, 0.1);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);

        // Try to teleport behind the target
        double targetYaw = Math.toRadians(target.getYaw());
        double behindX = target.getX() + Math.sin(targetYaw) * 2.5;
        double behindZ = target.getZ() - Math.cos(targetYaw) * 2.5;

        BlockPos targetPos = new BlockPos((int) behindX, (int) target.getY(), (int) behindZ);
        boolean found = tryTeleportTo(world, targetPos);

        // Fallback: random position near target
        if (!found) {
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 2 + boss.getRandom().nextDouble() * 3;
                double rx = target.getX() + Math.cos(angle) * dist;
                double rz = target.getZ() + Math.sin(angle) * dist;
                BlockPos rp = new BlockPos((int) rx, (int) target.getY(), (int) rz);
                if (tryTeleportTo(world, rp)) {
                    found = true;
                    break;
                }
            }
        }

        if (found) {
            // Face target
            boss.getLookControl().lookAt(target, 360, 90);

            // Destination effects
            world.spawnParticles(ParticleTypes.PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    40, 0.4, 0.8, 0.4, 0.8);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    20, 0.3, 0.6, 0.3, 0.1);
            world.spawnParticles(ParticleTypes.WITCH,
                    boss.getX(), boss.getY() + 0.5, boss.getZ(),
                    10, 0.5, 0.5, 0.5, 0.05);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 1.2f);

            // Taunt
            String msg = taunt != null ? taunt : "&5Behind you...";
            if (boss.getRandom().nextFloat() < 0.4f) {
                Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                        .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                        .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(msg));
                for (var player : world.getPlayers()) {
                    if (player.squaredDistanceTo(boss) <= 48 * 48) {
                        player.sendMessage(tauntText, false);
                    }
                }
            }
        }
    }

    private boolean tryTeleportTo(ServerWorld world, BlockPos basePos) {
        for (int dy = -2; dy <= 3; dy++) {
            BlockPos check = basePos.up(dy);
            if (world.isAir(check) && world.isAir(check.up())
                    && world.getBlockState(check.down()).isSolidBlock(world, check.down())) {
                boss.teleport(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }
}
