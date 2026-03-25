package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * ULTIMATE — Marks all nearby players simultaneously then detonates after a delay.
 * Players dodge by hiding behind blocks (LOS) or running far away.
 *
 * JSON params:
 *   castRadius      (double, 32.0)   radius to find/mark players
 *   castTime        (int,    60)     ticks for cast phase (3 s default)
 *   detonationDelay (int,   100)     ticks after cast before detonation (5 s)
 *   damage          (float, 30.0)    damage to non-dodging players
 *   dodgeRange      (double, 28.0)   players beyond this distance auto-dodge
 *   preCastMessage  (String, "")     message at start of cast; empty = skip
 *   postHitMessage  (String, "")     message after detonation; empty = skip
 */
public class JudgmentMarkGoal extends Goal {

    private static final int STATE_CAST  = 0;
    private static final int STATE_DELAY = 1;
    private static final int STATE_DONE  = 2;

    private final BossEntity boss;
    private final double castRadius;
    private final int    castTime;
    private final int    detonationDelay;
    private final float  damage;
    private final double dodgeRange;
    private final Text   preCastMessage;
    private final Text   postHitMessage;
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

        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return !getPlayersInRange(castRadius).isEmpty();
    }

    @Override
    public boolean shouldContinue() { return active; }

    @Override
    public void start() {
        tick   = 0;
        state  = STATE_CAST;
        active = true;
        markedUuids.clear();

        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Mark all players in range
        List<PlayerEntity> targets = getPlayersInRange(castRadius);
        int glowDuration = castTime + detonationDelay + 40;
        for (PlayerEntity p : targets) {
            markedUuids.add(p.getUuid());
            p.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.GLOWING, glowDuration, 0, false, true));
        }

        // Pre-cast message
        if (preCastMessage != null) broadcast(world, preCastMessage);

        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.4f);
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        switch (state) {
            case STATE_CAST  -> tickCast(world);
            case STATE_DELAY -> tickDelay(world);
        }
    }

    // ── Cast phase ───────────────────────────────────────────────────────────

    private void tickCast(ServerWorld world) {
        // Expanding spiral at boss
        double progress = (double) tick / castTime;
        double radius   = progress * 3.0;
        int    pts      = 24;
        double baseAng  = Math.toRadians(tick * 6.0);
        for (int i = 0; i < pts; i++) {
            double a  = baseAng + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * radius;
            double pz = boss.getZ() + Math.sin(a) * radius;
            world.spawnParticles(ParticleTypes.ENCHANT,
                    px, boss.getY() + 1.2, pz, 1, 0, 0.1, 0, 0.05);
            world.spawnParticles(ParticleTypes.DRAGON_BREATH,
                    px, boss.getY() + 0.3, pz, 1, 0, 0, 0, 0.02);
        }

        // SOUL rain above each marked player
        resolveMarked(world).forEach(p -> {
            if (tick % 4 == 0) {
                world.spawnParticles(ParticleTypes.SOUL,
                        p.getX(), p.getY() + 2.5, p.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            }
            // Pulsing CRIT ring underfoot
            int warningPts = 10;
            double pulse   = 0.5 + 0.3 * Math.sin(tick * 0.3);
            for (int i = 0; i < warningPts; i++) {
                double a  = Math.toRadians(i * (360.0 / warningPts) + tick * 5.0);
                world.spawnParticles(ParticleTypes.CRIT,
                        p.getX() + Math.cos(a) * pulse, p.getY() + 0.05,
                        p.getZ() + Math.sin(a) * pulse, 1, 0, 0, 0, 0.02);
            }
        });

        if (tick % 20 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }

        if (tick >= castTime) {
            state = STATE_DELAY;
        }
    }

    // ── Detonation-delay phase ───────────────────────────────────────────────

    private void tickDelay(ServerWorld world) {
        int delayTick = tick - castTime;

        // Intensify: explosion particles in sphere around boss
        if (delayTick % 5 == 0) {
            for (int i = 0; i < 8; i++) {
                double a  = world.getRandom().nextDouble() * Math.PI * 2;
                double r  = 1.5 + world.getRandom().nextDouble() * 1.5;
                world.spawnParticles(ParticleTypes.EXPLOSION,
                        boss.getX() + Math.cos(a) * r, boss.getY() + 1.0,
                        boss.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }

        // Flashing CRIT under each marked player
        if (delayTick % 3 == 0) {
            resolveMarked(world).forEach(p -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0 + delayTick * 8.0);
                    world.spawnParticles(ParticleTypes.CRIT,
                            p.getX() + Math.cos(a) * 0.6, p.getY() + 0.05,
                            p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0.05);
                }
            });
        }

        if (delayTick % 30 == 0) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.8f, 0.7f);
        }

        if (delayTick >= detonationDelay) {
            detonate(world);
        }
    }

    // ── Detonation ───────────────────────────────────────────────────────────

    private void detonate(ServerWorld world) {
        double dodgeSq = dodgeRange * dodgeRange;

        for (UUID uuid : markedUuids) {
            PlayerEntity player = (PlayerEntity) world.getPlayerByUuid(uuid);
            if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative())
                continue;

            boolean dodgedRange = player.squaredDistanceTo(boss) > dodgeSq;
            boolean dodgedLOS   = !boss.canSee(player);

            if (dodgedRange || dodgedLOS) {
                // Dodged — show a "blocked" visual at player
                world.spawnParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        15, 0.4, 0.5, 0.4, 0.1);
            } else {
                player.damage(boss.getDamageSources().magic(), damage);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        1, 0, 0, 0, 0);
            }

            // Remove Glowing
            player.removeStatusEffect(StatusEffects.GLOWING);
        }

        // Big explosion at boss
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH,
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 2.0, 1.0, 2.0, 0.05);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 2.0f, 0.7f);

        if (postHitMessage != null) broadcast(world, postHitMessage);

        state  = STATE_DONE;
        active = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<PlayerEntity> resolveMarked(ServerWorld world) {
        List<PlayerEntity> result = new ArrayList<>();
        for (UUID uuid : markedUuids) {
            var p = world.getPlayerByUuid(uuid);
            if (p != null && p.isAlive()) result.add(p);
        }
        return result;
    }

    private List<PlayerEntity> getPlayersInRange(double radius) {
        return boss.getWorld().getEntitiesByClass(PlayerEntity.class,
                new Box(boss.getX() - radius, boss.getY() - 4, boss.getZ() - radius,
                        boss.getX() + radius, boss.getY() + 4, boss.getZ() + radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private void broadcast(ServerWorld world, Text message) {
        for (ServerPlayerEntity sp : world.getPlayers()) {
            if (sp.squaredDistanceTo(boss) <= castRadius * castRadius) {
                sp.sendMessage(message, false);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        // Safety cleanup: remove Glowing from any still-marked players
        if (!boss.getWorld().isClient) {
            ServerWorld sw = (ServerWorld) boss.getWorld();
            for (PlayerEntity p : resolveMarked(sw)) {
                p.removeStatusEffect(StatusEffects.GLOWING);
            }
        }
        markedUuids.clear();
    }
}
