package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Boss scans nearby players and marks the one with the most HP.
 * Marked player gets Glowing + boss deals extra magic damage to them on every melee hit.
 *
 * JSON params:
 *   markRadius      (double, 24.0)  scan radius
 *   markDuration    (int,   200)    ticks the mark lasts (≈ 10 s)
 *   markDamageBonus (float,  8.0)   extra magic damage on each boss melee hit
 *   castTime        (int,    20)    windup ticks before mark is applied
 */
public class DetectMarkGoal extends Goal {

    private static final int STATE_CAST = 0;
    private static final int STATE_MARK = 1;

    private static final DustParticleEffect DUST_GOLD =
            new DustParticleEffect(0xFFCC00, 1.2f);

    private final BossEntity boss;
    private final double markRadius;
    private final int    markDuration;
    private final float  markDamageBonus;
    private final int    castTime;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     markAge;

    private UUID          markedUuid;
    private PlayerEntity  markedPlayer;

    public DetectMarkGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss           = boss;
        this.markRadius     = params.has("markRadius")      ? params.get("markRadius").getAsDouble()     : 24.0;
        this.markDuration   = params.has("markDuration")    ? params.get("markDuration").getAsInt()      : 200;
        this.markDamageBonus= params.has("markDamageBonus") ? params.get("markDamageBonus").getAsFloat() : 8.0f;
        this.castTime       = params.has("castTime")        ? params.get("castTime").getAsInt()          : 20;
        this.cooldown       = cooldownTicks;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return !getNearbyPlayers().isEmpty();
    }

    @Override
    public boolean shouldContinue() { return active; }

    @Override
    public void start() {
        tick    = 0;
        markAge = 0;
        state   = STATE_CAST;
        active  = true;
        markedUuid   = null;
        markedPlayer = null;

        // Find the player with the most current HP
        List<PlayerEntity> players = getNearbyPlayers();
        if (players.isEmpty()) { active = false; return; }
        markedPlayer = players.stream()
                .max(Comparator.comparingDouble(PlayerEntity::getHealth))
                .orElse(players.get(0));
        markedUuid = markedPlayer.getUuid();
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getEntityWorld().isClient()) return;
        ServerWorld world = (ServerWorld) boss.getEntityWorld();

        // Re-resolve player reference each tick in case of reconnect
        if (markedUuid != null && (markedPlayer == null || !markedPlayer.isAlive())) {
            markedPlayer = (PlayerEntity) world.getPlayerByUuid(markedUuid);
        }

        if (state == STATE_CAST) {
            tickCast(world);
        } else {
            tickMark(world);
        }
    }

    private void tickCast(ServerWorld world) {
        if (markedPlayer != null && markedPlayer.isAlive()) {
            boss.getLookControl().lookAt(markedPlayer, 30, 30);
        }

        // Build-up spirals converging on the target
        if (markedPlayer != null && markedPlayer.isAlive()) {
            double progress = (double) tick / castTime;
            double radius   = 3.0 * (1.0 - progress) + 0.3;
            int    points   = 12;
            double baseAngle= Math.toRadians(tick * 15.0);
            for (int i = 0; i < points; i++) {
                double a  = baseAngle + Math.toRadians(i * (360.0 / points));
                double px = markedPlayer.getX() + Math.cos(a) * radius;
                double pz = markedPlayer.getZ() + Math.sin(a) * radius;
                world.spawnParticles(ParticleTypes.ENCHANT,
                        px, markedPlayer.getY() + 1.0, pz, 1, 0, 0.2, 0, 0.05);
                world.spawnParticles(DUST_GOLD,
                        px, markedPlayer.getY() + 1.0, pz, 1, 0, 0, 0, 0);
            }
        }

        if (tick % 5 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    4, 0.4, 0.3, 0.4, 0.1);
        }

        if (tick >= castTime) {
            applyMark(world);
            state = STATE_MARK;
        }
    }

    private void applyMark(ServerWorld world) {
        if (markedPlayer == null || !markedPlayer.isAlive()) { active = false; return; }

        markedPlayer.addStatusEffect(
                new StatusEffectInstance(StatusEffects.GLOWING, markDuration + 20, 0, false, true));
        boss.setMarkTarget(markedUuid, markDamageBonus);

        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1.2f, 0.6f);

        // Big burst at player
        world.spawnParticles(DUST_GOLD,
                markedPlayer.getX(), markedPlayer.getY() + 1.0, markedPlayer.getZ(),
                30, 0.6, 0.5, 0.6, 0.0);
        world.spawnParticles(ParticleTypes.CRIT,
                markedPlayer.getX(), markedPlayer.getY() + 1.0, markedPlayer.getZ(),
                20, 0.5, 0.4, 0.5, 0.2);
    }

    private void tickMark(ServerWorld world) {
        markAge++;

        // Floating SOUL particles above marked player
        if (markAge % 5 == 0 && markedPlayer != null && markedPlayer.isAlive()) {
            for (int i = 0; i < 4; i++) {
                double ox = (world.getRandom().nextDouble() - 0.5) * 0.8;
                double oz = (world.getRandom().nextDouble() - 0.5) * 0.8;
                world.spawnParticles(ParticleTypes.SOUL,
                        markedPlayer.getX() + ox, markedPlayer.getY() + 2.1, markedPlayer.getZ() + oz,
                        1, 0.05, 0.15, 0.05, 0.02);
            }
        }

        // Refresh Glowing each 20 ticks so it doesn't expire early
        if (markAge % 20 == 0 && markedPlayer != null && markedPlayer.isAlive()) {
            int remaining = markDuration - markAge + 20;
            if (remaining > 0) {
                markedPlayer.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.GLOWING, remaining, 0, false, false));
            }
        }

        if (markAge >= markDuration || markedPlayer == null || !markedPlayer.isAlive()) {
            clearMark();
            active = false;
        }
    }

    private void clearMark() {
        boss.clearMarkTarget();
        if (markedPlayer != null && markedPlayer.isAlive()) {
            markedPlayer.removeStatusEffect(StatusEffects.GLOWING);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        clearMark();
    }

    private List<PlayerEntity> getNearbyPlayers() {
        return boss.getEntityWorld().getEntitiesByClass(PlayerEntity.class,
                new Box(boss.getX() - markRadius, boss.getY() - 4, boss.getZ() - markRadius,
                        boss.getX() + markRadius, boss.getY() + 4, boss.getZ() + markRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }
}
