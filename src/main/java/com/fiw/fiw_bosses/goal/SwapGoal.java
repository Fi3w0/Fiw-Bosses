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
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Swaps the boss's position with the target's position — instantly confusing players
 * by teleporting the boss where they stood and the player where the boss stood.
 *
 * Both swap positions get a burst of portal + poof particles and a teleport sound
 * so players can see what happened.
 *
 * JSON params:
 *   maxDistance  (float, 20.0)  only swaps if target is within this range
 *   minDistance  (float,  3.0)  won't swap if target is already this close (no-op swap)
 *   taunt        (string, null)
 */
public class SwapGoal extends Goal {

    private final BossEntity boss;
    private final float maxDistance;
    private final float minDistance;
    private final String taunt;
    private final int cooldown;
    private int cooldownTimer;

    public SwapGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.maxDistance = params.has("maxDistance") ? params.get("maxDistance").getAsFloat() : 20.0f;
        this.minDistance = params.has("minDistance") ? params.get("minDistance").getAsFloat() : 3.0f;
        this.taunt       = params.has("taunt")       ? params.get("taunt").getAsString()       : null;
        this.cooldown    = cooldownTicks;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = boss.distanceTo(target);
        return dist >= minDistance && dist <= maxDistance;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return;
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();

        Vec3d bossPos   = boss.getPos();
        Vec3d targetPos = target.getPos();

        // Departure bursts at both positions
        spawnSwapBurst(world, bossPos);
        spawnSwapBurst(world, targetPos);

        // Swap
        boss.teleport(targetPos.x, targetPos.y, targetPos.z);
        target.teleport(bossPos.x, bossPos.y, bossPos.z);

        // Arrival bursts at swapped positions
        spawnSwapBurst(world, targetPos);
        spawnSwapBurst(world, bossPos);

        // Sounds at both locations
        world.playSound(null, bossPos.x, bossPos.y, bossPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.2f, 0.6f);
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.2f, 1.4f);

        if (taunt != null) {
            var bossName = boss.getCustomName();
            net.minecraft.text.MutableText nameText = bossName != null ? bossName.copy() : Text.literal("Boss");
            Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(nameText)
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(taunt));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48)
                    player.sendMessage(tauntText, false);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void spawnSwapBurst(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0, pos.z, 30, 0.4, 0.6, 0.4, 0.3);
        world.spawnParticles(ParticleTypes.POOF,
                pos.x, pos.y + 0.5, pos.z, 12, 0.3, 0.4, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.1);
    }
}
