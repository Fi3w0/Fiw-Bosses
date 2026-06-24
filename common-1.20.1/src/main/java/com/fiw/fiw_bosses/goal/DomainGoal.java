package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class DomainGoal extends Goal {

    private static final UUID DOMAIN_SPEED_ID =
            UUID.fromString("c3d8a4e6-9b5c-4f3a-be4f-2b3c4d5e6f70");

    private final BossEntity boss;
    private final float radius;
    private final int duration;
    private final float domainSpeed;
    private final float pushDamage;
    private final float pullDamage;
    private final boolean darkness;
    private final boolean blindness;
    private final String taunt;
    private final JsonArray attacksJson;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private double rotation;

    private Vec3 domainCenter;

    private final List<ServerPlayer> capturedPlayers = new ArrayList<>();
    private final List<DomainGoalEntry> domainGoals = new ArrayList<>();

    private boolean hasCustomAttacks;
    private double originalBaseSpeed;

    public DomainGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.radius      = params.has("radius")      ? params.get("radius").getAsFloat()      : 15.0f;
        this.duration    = params.has("duration")    ? params.get("duration").getAsInt()      : 300;
        this.domainSpeed = params.has("domainSpeed") ? params.get("domainSpeed").getAsFloat() : 0.36f;
        this.pushDamage  = params.has("pushDamage")  ? params.get("pushDamage").getAsFloat()  : 3.0f;
        this.pullDamage  = params.has("pullDamage")  ? params.get("pullDamage").getAsFloat()  : 3.0f;
        this.darkness    = !params.has("darkness")   || params.get("darkness").getAsBoolean();
        this.blindness   = params.has("blindness")   && params.get("blindness").getAsBoolean();
        this.taunt       = params.has("taunt")       ? params.get("taunt").getAsString()      : null;
        this.attacksJson = params.has("attacks")     ? params.get("attacks").getAsJsonArray() : new JsonArray();
        this.cooldown    = cooldownTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        rotation = 0;
        capturedPlayers.clear();
        domainGoals.clear();

        domainCenter = boss.position().add(0, 1.0, 0);

        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            originalBaseSpeed = speedAttr.getBaseValue();
            speedAttr.removeModifier(DOMAIN_SPEED_ID);
            speedAttr.addTransientModifier(new AttributeModifier(
                    DOMAIN_SPEED_ID,
                    "fiw_bosses_domain_speed",
                    domainSpeed - originalBaseSpeed,
                    AttributeModifier.Operation.ADDITION));
        }

        AABB scanBox = new AABB(
                domainCenter.x - radius, domainCenter.y - radius, domainCenter.z - radius,
                domainCenter.x + radius, domainCenter.y + radius, domainCenter.z + radius);
        for (Player p : level.getEntitiesOfClass(Player.class, scanBox,
                pp -> pp.isAlive() && !pp.isSpectator() && !pp.isCreative()
                        && pp.distanceToSqr(domainCenter.x, domainCenter.y, domainCenter.z) <= radius * radius)) {
            if (p instanceof ServerPlayer spe) capturedPlayers.add(spe);
        }

        hasCustomAttacks = attacksJson.size() > 0;
        if (hasCustomAttacks) {
            for (var elem : attacksJson) {
                JsonObject entry = elem.getAsJsonObject();
                String type    = entry.has("type")     ? entry.get("type").getAsString()          : "";
                int    cd      = entry.has("cooldown") ? entry.get("cooldown").getAsInt()          : 40;
                JsonObject ap  = entry.has("params")   ? entry.get("params").getAsJsonObject()     : new JsonObject();
                try {
                    Goal g = BossGoalFactory.create(type, boss, cd, ap);
                    domainGoals.add(new DomainGoalEntry(g, cd));
                } catch (Exception ex) {
                    FiwBossesCore.LOGGER.warn("Domain: unknown attack type '{}'", type);
                }
            }
            boss.scheduleGoalAction(this::applyDomainGoals);
        }

        level.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.4f);

        for (int burst = 0; burst < 5; burst++) {
            double br = radius * (burst + 1) / 5.0;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24.0;
                double bx = domainCenter.x + br * Math.cos(angle);
                double bz = domainCenter.z + br * Math.sin(angle);
                level.sendParticles(ParticleTypes.PORTAL, bx, domainCenter.y, bz, 1, 0, 0, 0, 0.05);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, bx, domainCenter.y + br * 0.5, bz, 1, 0, 0, 0, 0.05);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                domainCenter.x, domainCenter.y, domainCenter.z, 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
                domainCenter.x, domainCenter.y, domainCenter.z,
                80, radius * 0.5, radius * 0.4, radius * 0.5, 0.3);

        if (taunt != null) sendTaunt(level, taunt);
    }

    @Override
    public boolean canContinueToUse() {
        if (tick >= duration) return false;
        if (!capturedPlayers.isEmpty()) {
            capturedPlayers.removeIf(p -> !p.isAlive() || p.isSpectator());
            if (capturedPlayers.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        rotation = (rotation + 2.5) % 360.0;

        if (tick % 2 == 0) spawnSphere(level);

        double bossDist = boss.position().distanceTo(new Vec3(domainCenter.x, boss.getY(), domainCenter.z));
        if (bossDist > radius * 0.85) {
            Vec3 toCenter = new Vec3(domainCenter.x - boss.getX(), 0, domainCenter.z - boss.getZ()).normalize();
            boss.push(toCenter.x * 0.6, 0, toCenter.z * 0.6);
            boss.hurtMarked = true;
        }

        double pullThreshold = (radius + 1.5);
        double pushThreshold = (radius - 0.5);

        AABB nearbyBox = new AABB(
                domainCenter.x - radius * 2, domainCenter.y - radius * 1.5, domainCenter.z - radius * 2,
                domainCenter.x + radius * 2, domainCenter.y + radius * 1.5, domainCenter.z + radius * 2);

        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, nearbyBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (Player player : nearbyPlayers) {
            double dist = Math.sqrt(player.distanceToSqr(domainCenter.x, domainCenter.y, domainCenter.z));
            boolean isCaptured = capturedPlayers.contains(player);

            if (isCaptured) {
                if (tick % 20 == 0) {
                    if (darkness) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                    if (blindness) player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                }

                if (dist > pullThreshold) {
                    Vec3 toCenter = new Vec3(
                            domainCenter.x - player.getX(),
                            domainCenter.y - player.getY(),
                            domainCenter.z - player.getZ()).normalize();
                    player.push(toCenter.x * 1.4, toCenter.y * 0.4 + 0.3, toCenter.z * 1.4);
                    player.hurtMarked = true;
                    player.hurt(boss.damageSources().magic(), pullDamage);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            8, 0.3, 0.5, 0.3, 0.1);
                }
            } else {
                if (dist < pushThreshold && dist > 0.1) {
                    Vec3 awayFromCenter = new Vec3(
                            player.getX() - domainCenter.x,
                            0,
                            player.getZ() - domainCenter.z).normalize();
                    player.push(awayFromCenter.x * 1.6, 0.5, awayFromCenter.z * 1.6);
                    player.hurtMarked = true;
                    if (dist < pushThreshold - 1.0 && tick % 5 == 0) {
                        player.hurt(boss.damageSources().magic(), pushDamage);
                        level.sendParticles(ParticleTypes.PORTAL,
                                player.getX(), player.getY() + 1, player.getZ(),
                                6, 0.3, 0.5, 0.3, 0.1);
                    }
                }
            }
        }

        for (DomainGoalEntry entry : domainGoals) {
            entry.tick(boss);
        }

        if (tick % 60 == 0) {
            level.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.3f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        Vec3 center = domainCenter != null ? domainCenter : boss.position().add(0, 1.0, 0);

        for (DomainGoalEntry entry : domainGoals) {
            entry.forceStop();
        }

        var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(DOMAIN_SPEED_ID);

        if (hasCustomAttacks && boss.getPhaseManager() != null) {
            boss.getPhaseManager().restoreCurrentPhase();
        }

        for (ServerPlayer p : capturedPlayers) {
            if (!p.isAlive()) continue;
            if (darkness) p.removeEffect(MobEffects.DARKNESS);
            if (blindness) p.removeEffect(MobEffects.BLINDNESS);
        }
        capturedPlayers.clear();

        level.playSound(null, center.x, center.y, center.z,
                ModRefs.DOMAIN_BREAK, SoundSource.HOSTILE, 3.0f, 1.0f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1.2f, 1.5f);

        for (int ring = 0; ring < 6; ring++) {
            double br = radius * (6 - ring) / 6.0;
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2 * i / 32.0;
                double bx = center.x + br * Math.cos(angle);
                double bz = center.z + br * Math.sin(angle);
                level.sendParticles(ParticleTypes.PORTAL, bx, center.y, bz, 1, 0, 0, 0, 0.05);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, bx, center.y + br * 0.3, bz, 1, 0, 0, 0, 0.05);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 4, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z,
                120, radius * 0.5, radius * 0.4, radius * 0.5, 0.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z,
                60, radius * 0.3, radius * 0.3, radius * 0.3, 0.3);
        level.sendParticles(ParticleTypes.SOUL,
                center.x, center.y, center.z,
                40, radius * 0.4, radius * 0.3, radius * 0.4, 0.2);
    }

    private void spawnSphere(ServerLevel level) {
        double cx = domainCenter.x;
        double cy = domainCenter.y;
        double cz = domainCenter.z;

        int outerLatLines = 20;
        for (int lat = 0; lat < outerLatLines; lat++) {
            double phi = Math.PI * lat / (outerLatLines - 1);
            double ringRadius = radius * Math.sin(phi);
            double ringY = cy + radius * Math.cos(phi);
            int points = Math.max(6, (int)(ringRadius * 4.5));
            double angleStep = Math.PI * 2 / points;

            for (int p = 0; p < points; p++) {
                double theta = angleStep * p + Math.toRadians(rotation + lat * 13.0);
                double px = cx + ringRadius * Math.cos(theta);
                double pz = cz + ringRadius * Math.sin(theta);

                int mod = lat % 4;
                if (mod == 0) {
                    level.sendParticles(ParticleTypes.PORTAL, px, ringY, pz, 1, 0, 0, 0, 0);
                } else if (mod == 1) {
                    level.sendParticles(ParticleTypes.WARPED_SPORE, px, ringY, pz, 1, 0, 0, 0, 0);
                } else if (mod == 2) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, ringY, pz, 1, 0, 0, 0, 0);
                } else {
                    level.sendParticles(ParticleTypes.WARPED_SPORE, px, ringY, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        double innerR = radius * 0.72;
        int innerLatLines = 12;
        for (int lat = 0; lat < innerLatLines; lat++) {
            double phi = Math.PI * lat / (innerLatLines - 1);
            double ringRadius = innerR * Math.sin(phi);
            double ringY = cy + innerR * Math.cos(phi);
            int points = Math.max(4, (int)(ringRadius * 3.0));
            double angleStep = Math.PI * 2 / points;

            for (int p = 0; p < points; p++) {
                double theta = angleStep * p - Math.toRadians(rotation * 0.7 + lat * 20.0);
                double px = cx + ringRadius * Math.cos(theta);
                double pz = cz + ringRadius * Math.sin(theta);
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, ringY, pz, 1, 0, 0, 0, 0);
            }
        }

        if (tick % 4 == 0) {
            int eqPoints = 48;
            for (int i = 0; i < eqPoints; i++) {
                double theta = Math.PI * 2 * i / eqPoints + Math.toRadians(rotation * 1.5);
                double px = cx + radius * Math.cos(theta);
                double pz = cz + radius * Math.sin(theta);
                level.sendParticles(ParticleTypes.SOUL, px, cy, pz, 1, 0, 0.1, 0, 0.01);
                level.sendParticles(ParticleTypes.PORTAL, px, cy + 0.3, pz, 1, 0.05, 0.05, 0.05, 0);
            }
        }

        if (tick % 3 == 0) {
            double groundY = cy - radius + 1.5;
            int gPoints = 32;
            double gRadius = radius * 0.95;
            for (int i = 0; i < gPoints; i++) {
                double theta = Math.PI * 2 * i / gPoints + Math.toRadians(rotation * 2.0);
                double px = cx + gRadius * Math.cos(theta);
                double pz = cz + gRadius * Math.sin(theta);
                level.sendParticles(ParticleTypes.WARPED_SPORE, px, groundY, pz, 1, 0, 0, 0, 0.02);
            }
            double gRadius2 = radius * 0.55;
            for (int i = 0; i < 20; i++) {
                double theta = Math.PI * 2 * i / 20 - Math.toRadians(rotation * 1.5);
                double px = cx + gRadius2 * Math.cos(theta);
                double pz = cz + gRadius2 * Math.sin(theta);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, groundY, pz, 1, 0, 0, 0, 0.01);
            }
        }

        if (tick % 3 == 1) {
            double topY = cy + radius;
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10 + Math.toRadians(rotation * 3.0);
                double capR = radius * 0.2;
                level.sendParticles(ParticleTypes.PORTAL,
                        cx + capR * Math.cos(angle), topY, cz + capR * Math.sin(angle),
                        1, 0.05, 0.05, 0.05, 0);
            }
            level.sendParticles(ParticleTypes.SOUL, cx, topY, cz, 2, 0.1, 0, 0.1, 0.02);

            double botY = cy - radius;
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10 - Math.toRadians(rotation * 3.0);
                double capR = radius * 0.2;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        cx + capR * Math.cos(angle), botY, cz + capR * Math.sin(angle),
                        1, 0.05, 0.05, 0.05, 0);
            }
            level.sendParticles(ParticleTypes.PORTAL, cx, botY, cz, 2, 0.1, 0, 0.1, 0.02);
        }

        if (tick % 5 == 0) {
            java.util.Random rng = new java.util.Random();
            int ambientCount = 12;
            double ir = innerR * 0.9;
            for (int i = 0; i < ambientCount; i++) {
                double ax, ay, az;
                do {
                    ax = (rng.nextDouble() * 2 - 1) * ir;
                    ay = (rng.nextDouble() * 2 - 1) * ir;
                    az = (rng.nextDouble() * 2 - 1) * ir;
                } while (ax * ax + ay * ay + az * az > ir * ir);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        cx + ax, cy + ay, cz + az, 1, 0, 0, 0, 0.03);
            }
        }
    }

    private void applyDomainGoals() {
        var toRemove = boss.getGoalSelector().getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g != this
                        && !(g instanceof FloatGoal)
                        && !(g instanceof MeleeAttackGoal)
                        && !(g instanceof WaterAvoidingRandomStrollGoal)
                        && !(g instanceof LookAtPlayerGoal)
                        && !(g instanceof RandomLookAroundGoal))
                .toList();
        toRemove.forEach(boss.getGoalSelector()::removeGoal);
    }

    private void sendTaunt(ServerLevel level, String message) {
        var bossName = boss.getCustomName();
        MutableComponent text = Component.literal("[")
                .withStyle(ChatFormatting.DARK_PURPLE)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(TextUtil.parseColorCodes(message));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(domainCenter.x, domainCenter.y, domainCenter.z) <= (radius + 32) * (radius + 32)) {
                player.sendSystemMessage(text);
            }
        }
    }

    private static class DomainGoalEntry {
        private final Goal goal;
        private final int cooldownTicks;
        private int cooldownTimer;
        private boolean running;

        DomainGoalEntry(Goal goal, int cooldownTicks) {
            this.goal = goal;
            this.cooldownTicks = cooldownTicks;
            this.cooldownTimer = 0;
            this.running = false;
        }

        void tick(BossEntity boss) {
            if (boss.level().isClientSide) return;

            if (running) {
                if (goal.canContinueToUse()) {
                    goal.tick();
                } else {
                    goal.stop();
                    running = false;
                    cooldownTimer = cooldownTicks;
                }
            } else {
                if (cooldownTimer > 0) {
                    cooldownTimer--;
                } else if (goal.canUse()) {
                    goal.start();
                    running = true;
                }
            }
        }

        void forceStop() {
            if (running) {
                goal.stop();
                running = false;
            }
        }
    }
}
