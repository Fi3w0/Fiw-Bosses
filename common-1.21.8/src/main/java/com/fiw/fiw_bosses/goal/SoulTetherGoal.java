package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class SoulTetherGoal extends Goal {

    private final BossEntity boss;
    private final double targetRadius;
    private final double breakDistance;
    private final double pullStrength;
    private final float pulseDamage;
    private final float snapDamage;
    private final int durationTicks;
    private final int damageInterval;
    private final int maxTargets;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private final List<Player> tethered = new ArrayList<>();

    public SoulTetherGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.targetRadius = params.has("targetRadius") ? params.get("targetRadius").getAsDouble() : 22.0;
        this.breakDistance = params.has("breakDistance") ? params.get("breakDistance").getAsDouble() : 16.0;
        this.pullStrength = params.has("pullStrength") ? params.get("pullStrength").getAsDouble() : 0.16;
        this.pulseDamage = params.has("pulseDamage") ? params.get("pulseDamage").getAsFloat() : 2.0f;
        this.snapDamage = params.has("snapDamage") ? params.get("snapDamage").getAsFloat() : 8.0f;
        this.durationTicks = params.has("durationTicks") ? params.get("durationTicks").getAsInt() : 90;
        this.damageInterval = params.has("damageInterval") ? params.get("damageInterval").getAsInt() : 20;
        this.maxTargets = params.has("maxTargets") ? params.get("maxTargets").getAsInt() : 2;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null && boss.getTarget().isAlive() && !findTargets().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        tethered.clear();
        tethered.addAll(findTargets());

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5f, 0.55f);
            for (Player player : tethered) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.04);
            }
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        Iterator<Player> iterator = tethered.iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                iterator.remove();
                continue;
            }

            double dist = player.distanceTo(boss);
            drawTether(level, player, dist);

            if (dist >= breakDistance) {
                snap(level, player);
                iterator.remove();
                continue;
            }

            pull(player, dist);
            if (pulseDamage > 0 && damageInterval > 0 && tick % damageInterval == 0) {
                player.hurtServer(level, boss.damageSources().magic(), pulseDamage);
                level.sendParticles(ParticleTypes.SOUL,
                        player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.25, 0.35, 0.25, 0.04);
            }
        }

        if (tick >= durationTicks || tethered.isEmpty()) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
        tethered.clear();
    }

    private List<Player> findTargets() {
        if (boss.level().isClientSide()) return List.of();
        ServerLevel level = (ServerLevel) boss.level();
        List<Player> players = level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(targetRadius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(boss)));
        if (players.size() > maxTargets) {
            return new ArrayList<>(players.subList(0, maxTargets));
        }
        return players;
    }

    private void drawTether(ServerLevel level, Player player, double distance) {
        Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.65, 0);
        Vec3 to = player.position().add(0, player.getBbHeight() * 0.55, 0);
        int steps = Math.max(4, (int) (distance * 1.3));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.lerp(to, t);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.REVERSE_PORTAL,
                    p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void pull(Player player, double distance) {
        Vec3 toBoss = boss.position().subtract(player.position());
        toBoss = new Vec3(toBoss.x, 0, toBoss.z);
        if (toBoss.lengthSqr() < 1.0e-6) return;

        double factor = Math.min(1.0, distance / Math.max(1.0, breakDistance));
        Vec3 pull = toBoss.normalize().scale(pullStrength * factor);
        player.push(pull.x, 0.02, pull.z);
        player.hurtMarked = true;
    }

    private void snap(ServerLevel level, Player player) {
        player.hurtServer(level, boss.damageSources().magic(), snapDamage);
        Vec3 toBoss = boss.position().subtract(player.position());
        toBoss = new Vec3(toBoss.x, 0, toBoss.z);
        if (toBoss.lengthSqr() < 1.0e-6) toBoss = new Vec3(0, 0, 1);
        Vec3 pull = toBoss.normalize().scale(1.1);
        player.push(pull.x, 0.45, pull.z);
        player.hurtMarked = true;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.45f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.45, 0.6, 0.45, 0.08);
    }
}
