package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class JudgmentMarkGoal extends Goal {

    private static final int STATE_CAST  = 0;
    private static final int STATE_DELAY = 1;

    private final BossEntity boss;
    private final double castRadius;
    private final int    castTime;
    private final int    detonationDelay;
    private final float  damage;
    private final double dodgeRange;
    private final Component preCastMessage;
    private final Component postHitMessage;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;

    private final List<UUID> markedUuids = new ArrayList<>();

    public JudgmentMarkGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.castRadius      = params.has("castRadius")      ? params.get("castRadius").getAsDouble()      : 32.0;
        this.castTime        = params.has("castTime")        ? params.get("castTime").getAsInt()           : 60;
        this.detonationDelay = params.has("detonationDelay") ? params.get("detonationDelay").getAsInt()    : 100;
        this.damage          = params.has("damage")          ? params.get("damage").getAsFloat()           : 30.0f;
        this.dodgeRange      = params.has("dodgeRange")      ? params.get("dodgeRange").getAsDouble()      : 28.0;
        this.cooldown        = cooldownTicks;

        String pre  = params.has("preCastMessage")  ? params.get("preCastMessage").getAsString()  : "";
        String post = params.has("postHitMessage")  ? params.get("postHitMessage").getAsString()  : "";
        this.preCastMessage = pre.isEmpty()  ? null : TextUtil.parseColorCodes(pre);
        this.postHitMessage = post.isEmpty() ? null : TextUtil.parseColorCodes(post);

        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return !getPlayersInRange(castRadius).isEmpty();
    }

    @Override
    public boolean canContinueToUse() { return active; }

    @Override
    public void start() {
        tick   = 0;
        state  = STATE_CAST;
        active = true;
        markedUuids.clear();

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        List<Player> targets = getPlayersInRange(castRadius);
        int glowDuration = castTime + detonationDelay + 40;
        for (Player p : targets) {
            markedUuids.add(p.getUUID());
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowDuration, 0, false, true));
        }

        if (preCastMessage != null) broadcast(level, preCastMessage);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.4f);
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        switch (state) {
            case STATE_CAST  -> tickCast(level);
            case STATE_DELAY -> tickDelay(level);
        }
    }

    private void tickCast(ServerLevel level) {
        double progress = (double) tick / castTime;
        double radius   = progress * 3.0;
        int    pts      = 24;
        double baseAng  = Math.toRadians(tick * 6.0);
        for (int i = 0; i < pts; i++) {
            double a  = baseAng + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * radius;
            double pz = boss.getZ() + Math.sin(a) * radius;
            level.sendParticles(ParticleTypes.ENCHANT,
                    px, boss.getY() + 1.2, pz, 1, 0, 0.1, 0, 0.05);
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    px, boss.getY() + 0.3, pz, 1, 0, 0, 0, 0.02);
        }

        resolveMarked(level).forEach(p -> {
            if (tick % 4 == 0) {
                level.sendParticles(ParticleTypes.SOUL,
                        p.getX(), p.getY() + 2.5, p.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            }
            int warningPts = 10;
            double pulse   = 0.5 + 0.3 * Math.sin(tick * 0.3);
            for (int i = 0; i < warningPts; i++) {
                double a  = Math.toRadians(i * (360.0 / warningPts) + tick * 5.0);
                level.sendParticles(ParticleTypes.CRIT,
                        p.getX() + Math.cos(a) * pulse, p.getY() + 0.05,
                        p.getZ() + Math.sin(a) * pulse, 1, 0, 0, 0, 0.02);
            }
        });

        if (tick % 20 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.5f);
        }

        if (tick >= castTime) {
            state = STATE_DELAY;
        }
    }

    private void tickDelay(ServerLevel level) {
        int delayTick = tick - castTime;

        if (delayTick % 5 == 0) {
            for (int i = 0; i < 8; i++) {
                double a  = level.getRandom().nextDouble() * Math.PI * 2;
                double r  = 1.5 + level.getRandom().nextDouble() * 1.5;
                level.sendParticles(ParticleTypes.EXPLOSION,
                        boss.getX() + Math.cos(a) * r, boss.getY() + 1.0,
                        boss.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }

        if (delayTick % 3 == 0) {
            resolveMarked(level).forEach(p -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0 + delayTick * 8.0);
                    level.sendParticles(ParticleTypes.CRIT,
                            p.getX() + Math.cos(a) * 0.6, p.getY() + 0.05,
                            p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0.05);
                }
            });
        }

        if (delayTick % 30 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.8f, 0.7f);
        }

        if (delayTick >= detonationDelay) {
            detonate(level);
        }
    }

    private void detonate(ServerLevel level) {
        double dodgeSq = dodgeRange * dodgeRange;

        for (UUID uuid : markedUuids) {
            Player player = level.getPlayerByUUID(uuid);
            if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative())
                continue;

            boolean dodgedRange = player.distanceToSqr(boss) > dodgeSq;
            boolean dodgedLOS   = !boss.getSensing().hasLineOfSight(player);

            if (dodgedRange || dodgedLOS) {
                level.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        15, 0.4, 0.5, 0.4, 0.1);
            } else {
                player.hurtServer(level, boss.damageSources().magic(), damage);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        1, 0, 0, 0, 0);
            }

            player.removeEffect(MobEffects.GLOWING);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 2.0, 1.0, 2.0, 0.05);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 0.7f);

        if (postHitMessage != null) broadcast(level, postHitMessage);

        active = false;
    }

    private List<Player> resolveMarked(ServerLevel level) {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : markedUuids) {
            Player p = level.getPlayerByUUID(uuid);
            if (p != null && p.isAlive()) result.add(p);
        }
        return result;
    }

    private List<Player> getPlayersInRange(double radius) {
        return boss.level().getEntitiesOfClass(Player.class,
                new AABB(boss.getX() - radius, boss.getY() - 4, boss.getZ() - radius,
                        boss.getX() + radius, boss.getY() + 4, boss.getZ() + radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(boss) <= castRadius * castRadius) {
                sp.sendSystemMessage(message);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        if (!boss.level().isClientSide()) {
            ServerLevel sl = (ServerLevel) boss.level();
            for (Player p : resolveMarked(sl)) {
                p.removeEffect(MobEffects.GLOWING);
            }
        }
        markedUuids.clear();
    }
}
