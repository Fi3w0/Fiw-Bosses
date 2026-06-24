package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class DetectMarkGoal extends Goal {

    private static final int STATE_CAST = 0;
    private static final int STATE_MARK = 1;

    private static final DustParticleOptions DUST_GOLD =
            new DustParticleOptions(Colors.rgb(1.0f, 0.8f, 0.0f), 1.2f);

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

    private UUID   markedUuid;
    private Player markedPlayer;

    public DetectMarkGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.markRadius      = params.has("markRadius")      ? params.get("markRadius").getAsDouble()     : 24.0;
        this.markDuration    = params.has("markDuration")    ? params.get("markDuration").getAsInt()      : 200;
        this.markDamageBonus = params.has("markDamageBonus") ? params.get("markDamageBonus").getAsFloat() : 8.0f;
        this.castTime        = params.has("castTime")        ? params.get("castTime").getAsInt()          : 20;
        this.cooldown        = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return !getNearbyPlayers().isEmpty();
    }

    @Override
    public boolean canContinueToUse() { return active; }

    @Override
    public void start() {
        tick    = 0;
        markAge = 0;
        state   = STATE_CAST;
        active  = true;
        markedUuid   = null;
        markedPlayer = null;

        List<Player> players = getNearbyPlayers();
        if (players.isEmpty()) { active = false; return; }
        markedPlayer = players.stream()
                .max(Comparator.comparingDouble(Player::getHealth))
                .orElse(players.get(0));
        markedUuid = markedPlayer.getUUID();
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (markedUuid != null && (markedPlayer == null || !markedPlayer.isAlive())) {
            markedPlayer = level.getPlayerByUUID(markedUuid);
        }

        if (state == STATE_CAST) {
            tickCast(level);
        } else {
            tickMark(level);
        }
    }

    private void tickCast(ServerLevel level) {
        if (markedPlayer != null && markedPlayer.isAlive()) {
            boss.getLookControl().setLookAt(markedPlayer, 30, 30);

            double progress = (double) tick / castTime;
            double radius   = 3.0 * (1.0 - progress) + 0.3;
            int    points   = 12;
            double baseAngle= Math.toRadians(tick * 15.0);
            for (int i = 0; i < points; i++) {
                double a  = baseAngle + Math.toRadians(i * (360.0 / points));
                double px = markedPlayer.getX() + Math.cos(a) * radius;
                double pz = markedPlayer.getZ() + Math.sin(a) * radius;
                level.sendParticles(ParticleTypes.ENCHANT,
                        px, markedPlayer.getY() + 1.0, pz, 1, 0, 0.2, 0, 0.05);
                level.sendParticles(DUST_GOLD,
                        px, markedPlayer.getY() + 1.0, pz, 1, 0, 0, 0, 0);
            }
        }

        if (tick % 5 == 0) {
            level.sendParticles(ParticleTypes.WITCH,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    4, 0.4, 0.3, 0.4, 0.1);
        }

        if (tick >= castTime) {
            applyMark(level);
            state = STATE_MARK;
        }
    }

    private void applyMark(ServerLevel level) {
        if (markedPlayer == null || !markedPlayer.isAlive()) { active = false; return; }

        markedPlayer.addEffect(
                new MobEffectInstance(MobEffects.GLOWING, markDuration + 20, 0, false, true));
        boss.setMarkTarget(markedUuid, markDamageBonus);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);

        level.sendParticles(DUST_GOLD,
                markedPlayer.getX(), markedPlayer.getY() + 1.0, markedPlayer.getZ(),
                30, 0.6, 0.5, 0.6, 0.0);
        level.sendParticles(ParticleTypes.CRIT,
                markedPlayer.getX(), markedPlayer.getY() + 1.0, markedPlayer.getZ(),
                20, 0.5, 0.4, 0.5, 0.2);
    }

    private void tickMark(ServerLevel level) {
        markAge++;

        if (markAge % 5 == 0 && markedPlayer != null && markedPlayer.isAlive()) {
            for (int i = 0; i < 4; i++) {
                double ox = (level.getRandom().nextDouble() - 0.5) * 0.8;
                double oz = (level.getRandom().nextDouble() - 0.5) * 0.8;
                level.sendParticles(ParticleTypes.SOUL,
                        markedPlayer.getX() + ox, markedPlayer.getY() + 2.1, markedPlayer.getZ() + oz,
                        1, 0.05, 0.15, 0.05, 0.02);
            }
        }

        if (markAge % 20 == 0 && markedPlayer != null && markedPlayer.isAlive()) {
            int remaining = markDuration - markAge + 20;
            if (remaining > 0) {
                markedPlayer.addEffect(
                        new MobEffectInstance(MobEffects.GLOWING, remaining, 0, false, false));
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
            markedPlayer.removeEffect(MobEffects.GLOWING);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        clearMark();
    }

    private List<Player> getNearbyPlayers() {
        return boss.level().getEntitiesOfClass(Player.class,
                new AABB(boss.getX() - markRadius, boss.getY() - 4, boss.getZ() - markRadius,
                        boss.getX() + markRadius, boss.getY() + 4, boss.getZ() + markRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }
}
