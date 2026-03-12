package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.ModSounds;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Domain Expansion — the boss's ultimate ability.
 *
 * On activation a dark particle sphere seals nearby players inside.
 * The sphere is anchored to the boss's position at activation — it does
 * not move. The boss (and all captured players) cannot leave the sphere.
 * Players outside the sphere when it forms can't enter (pushed away).
 * If all trapped players die or leave, the domain collapses early.
 * On collapse the custom "domain_break" sound fires.
 *
 * JSON params:
 *   radius           (float,  15.0)  sphere radius in blocks
 *   duration         (int,   300)    max duration in ticks (~15 s)
 *   domainSpeed      (float,  0.36)  boss movement speed inside domain
 *   pushDamage       (float,  3.0)   damage applied when pushing outsiders away
 *   pullDamage       (float,  3.0)   damage applied when pulling insiders back
 *   darkness         (boolean, true) apply Darkness to trapped players
 *   blindness        (boolean, false) apply Blindness to trapped players
 *   taunt            (string, null)  message on activation
 *   attacks          (array,  [])    domain-specific ability list:
 *                                    [{type, cooldown, params}]
 *                                    If empty, boss keeps phase goals active.
 */
public class DomainGoal extends Goal {

    private static final UUID DOMAIN_SPEED_UUID = UUID.fromString("d0a10000-cafe-4b49-9a6a-e1f1c5d10001");

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

    // Anchor point — set at start(), never moves
    private Vec3d domainCenter;

    // Players trapped inside when domain spawned
    private final List<ServerPlayerEntity> capturedPlayers = new ArrayList<>();

    // Manually-driven domain goal entries
    private final List<DomainGoalEntry> domainGoals = new ArrayList<>();

    private boolean hasCustomAttacks;

    // Speed before domain
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
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
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

        // Anchor the sphere here — never moves again
        domainCenter = boss.getPos().add(0, 1.0, 0);

        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        // Save and override boss speed
        var speedAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            originalBaseSpeed = speedAttr.getBaseValue();
            speedAttr.removeModifier(DOMAIN_SPEED_UUID);
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    DOMAIN_SPEED_UUID, "domain_speed",
                    domainSpeed - originalBaseSpeed, EntityAttributeModifier.Operation.ADDITION));
        }

        // Collect players inside sphere at activation (use domainCenter for distance)
        Box scanBox = new Box(
                domainCenter.x - radius, domainCenter.y - radius, domainCenter.z - radius,
                domainCenter.x + radius, domainCenter.y + radius, domainCenter.z + radius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, scanBox,
                pp -> pp.isAlive() && !pp.isSpectator() && !pp.isCreative()
                        && pp.squaredDistanceTo(domainCenter.x, domainCenter.y, domainCenter.z) <= radius * radius)) {
            if (p instanceof ServerPlayerEntity spe) capturedPlayers.add(spe);
        }

        // Parse domain-specific attack goals
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
                    com.fiw.fiw_bosses.FiwBosses.LOGGER.warn("Domain: unknown attack type '{}'", type);
                }
            }
            // Schedule goal replacement for the next mobTick() — runs before
            // goalSelector.tick(), so the goal set is safe to modify there.
            boss.scheduleGoalAction(this::applyDomainGoals);
        }

        // Activation sounds
        world.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.5f, 0.5f);
        world.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.4f);

        // Activation burst — expanding ring of particles outward from center
        for (int burst = 0; burst < 5; burst++) {
            double br = radius * (burst + 1) / 5.0;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24.0;
                double bx = domainCenter.x + br * Math.cos(angle);
                double bz = domainCenter.z + br * Math.sin(angle);
                world.spawnParticles(ParticleTypes.PORTAL, bx, domainCenter.y, bz, 1, 0, 0, 0, 0.05);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, bx, domainCenter.y + br * 0.5, bz, 1, 0, 0, 0, 0.05);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                domainCenter.x, domainCenter.y, domainCenter.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.PORTAL,
                domainCenter.x, domainCenter.y, domainCenter.z,
                80, radius * 0.5, radius * 0.4, radius * 0.5, 0.3);

        if (taunt != null) sendTaunt(world, taunt);
    }

    @Override
    public boolean shouldContinue() {
        if (tick >= duration) return false;
        // Collapse early if no captured players remain
        if (!capturedPlayers.isEmpty()) {
            capturedPlayers.removeIf(p -> !p.isAlive() || p.isSpectator());
            if (capturedPlayers.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        rotation = (rotation + 2.5) % 360.0;

        // ── SPHERE VISUALS ────────────────────────────────────────────────────
        if (tick % 2 == 0) spawnSphere(world);

        // ── BOSS CONTAINMENT ─────────────────────────────────────────────────
        // Boss must stay inside the domain — push it back toward center if it drifts
        double bossDist = boss.getPos().distanceTo(new Vec3d(domainCenter.x, boss.getY(), domainCenter.z));
        if (bossDist > radius * 0.85) {
            Vec3d toCenter = new Vec3d(domainCenter.x - boss.getX(), 0, domainCenter.z - boss.getZ()).normalize();
            boss.addVelocity(toCenter.x * 0.6, 0, toCenter.z * 0.6);
            boss.velocityModified = true;
        }

        // ── PLAYER CONTAINMENT ────────────────────────────────────────────────
        double pullThreshold = (radius + 1.5);
        double pushThreshold = (radius - 0.5);

        Box nearbyBox = new Box(
                domainCenter.x - radius * 2, domainCenter.y - radius * 1.5, domainCenter.z - radius * 2,
                domainCenter.x + radius * 2, domainCenter.y + radius * 1.5, domainCenter.z + radius * 2);

        List<PlayerEntity> nearbyPlayers = world.getEntitiesByClass(PlayerEntity.class, nearbyBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (PlayerEntity player : nearbyPlayers) {
            double dist = Math.sqrt(player.squaredDistanceTo(domainCenter.x, domainCenter.y, domainCenter.z));
            boolean isCaptured = capturedPlayers.contains(player);

            if (isCaptured) {
                // Apply status effects to trapped players
                if (tick % 20 == 0) {
                    if (darkness) player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, false, false));
                    if (blindness) player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
                }

                // Pull back if they escaped
                if (dist > pullThreshold) {
                    Vec3d toCenter = new Vec3d(
                            domainCenter.x - player.getX(),
                            domainCenter.y - player.getY(),
                            domainCenter.z - player.getZ()).normalize();
                    player.addVelocity(toCenter.x * 1.4, toCenter.y * 0.4 + 0.3, toCenter.z * 1.4);
                    player.velocityModified = true;
                    player.damage(boss.getDamageSources().magic(), pullDamage);
                    // Visual warning
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            8, 0.3, 0.5, 0.3, 0.1);
                }
            } else {
                // Push away any outsider who wanders in
                if (dist < pushThreshold && dist > 0.1) {
                    Vec3d awayFromCenter = new Vec3d(
                            player.getX() - domainCenter.x,
                            0,
                            player.getZ() - domainCenter.z).normalize();
                    player.addVelocity(awayFromCenter.x * 1.6, 0.5, awayFromCenter.z * 1.6);
                    player.velocityModified = true;
                    if (dist < pushThreshold - 1.0 && tick % 5 == 0) {
                        player.damage(boss.getDamageSources().magic(), pushDamage);
                        world.spawnParticles(ParticleTypes.PORTAL,
                                player.getX(), player.getY() + 1, player.getZ(),
                                6, 0.3, 0.5, 0.3, 0.1);
                    }
                }
            }
        }

        // ── TICK DOMAIN ATTACKS ───────────────────────────────────────────────
        for (DomainGoalEntry entry : domainGoals) {
            entry.tick(boss);
        }

        // Ambient rumble every 3 seconds
        if (tick % 60 == 0) {
            world.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z,
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.3f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        Vec3d center = domainCenter != null ? domainCenter : boss.getPos().add(0, 1.0, 0);

        // Stop any running domain attack goals
        for (DomainGoalEntry entry : domainGoals) {
            entry.forceStop();
        }

        // Restore boss speed
        var speedAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(DOMAIN_SPEED_UUID);

        // Restore phase goals
        if (hasCustomAttacks && boss.getPhaseManager() != null) {
            boss.getPhaseManager().restoreCurrentPhase();
        }

        // Remove effects from captured players
        for (ServerPlayerEntity p : capturedPlayers) {
            if (!p.isAlive()) continue;
            if (darkness) p.removeStatusEffect(StatusEffects.DARKNESS);
            if (blindness) p.removeStatusEffect(StatusEffects.BLINDNESS);
        }
        capturedPlayers.clear();

        // Break sound
        world.playSound(null, center.x, center.y, center.z,
                ModSounds.DOMAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 1.0f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 1.2f, 1.5f);

        // Collapse burst — multiple rings imploding inward
        for (int ring = 0; ring < 6; ring++) {
            double br = radius * (6 - ring) / 6.0;
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2 * i / 32.0;
                double bx = center.x + br * Math.cos(angle);
                double bz = center.z + br * Math.sin(angle);
                world.spawnParticles(ParticleTypes.PORTAL, bx, center.y, bz, 1, 0, 0, 0, 0.05);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, bx, center.y + br * 0.3, bz, 1, 0, 0, 0, 0.05);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 4, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z,
                120, radius * 0.5, radius * 0.4, radius * 0.5, 0.4);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z,
                60, radius * 0.3, radius * 0.3, radius * 0.3, 0.3);
        world.spawnParticles(ParticleTypes.SOUL,
                center.x, center.y, center.z,
                40, radius * 0.4, radius * 0.3, radius * 0.4, 0.2);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Renders the domain sphere each visual tick.
     *
     * Layers:
     *  1. Outer shell  — 20 latitude rings (PORTAL / WARPED_SPORE / REVERSE_PORTAL alternating)
     *  2. Inner shell  — 12 latitude rings at 0.72× radius, counter-rotating (SCULK_CHARGE_POP)
     *  3. Equatorial   — dense highlight band at the equator (SOUL)
     *  4. Ground ring  — flat circle on the floor plane (WARPED_SPORE)
     *  5. Polar caps   — dense glow at top and bottom poles (PORTAL)
     *  6. Ambient fill — random floating particles inside the dome (REVERSE_PORTAL)
     */
    private void spawnSphere(ServerWorld world) {
        double cx = domainCenter.x;
        double cy = domainCenter.y;
        double cz = domainCenter.z;

        // ── 1. OUTER SHELL ────────────────────────────────────────────────────
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
                    world.spawnParticles(ParticleTypes.PORTAL, px, ringY, pz, 1, 0, 0, 0, 0);
                } else if (mod == 1) {
                    world.spawnParticles(ParticleTypes.WARPED_SPORE, px, ringY, pz, 1, 0, 0, 0, 0);
                } else if (mod == 2) {
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, px, ringY, pz, 1, 0, 0, 0, 0);
                } else {
                    world.spawnParticles(ParticleTypes.WARPED_SPORE, px, ringY, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        // ── 2. INNER SHELL (counter-rotating) ────────────────────────────────
        double innerR = radius * 0.72;
        int innerLatLines = 12;
        for (int lat = 0; lat < innerLatLines; lat++) {
            double phi = Math.PI * lat / (innerLatLines - 1);
            double ringRadius = innerR * Math.sin(phi);
            double ringY = cy + innerR * Math.cos(phi);
            int points = Math.max(4, (int)(ringRadius * 3.0));
            double angleStep = Math.PI * 2 / points;

            for (int p = 0; p < points; p++) {
                // Counter-rotate relative to outer shell
                double theta = angleStep * p - Math.toRadians(rotation * 0.7 + lat * 20.0);
                double px = cx + ringRadius * Math.cos(theta);
                double pz = cz + ringRadius * Math.sin(theta);
                world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, px, ringY, pz, 1, 0, 0, 0, 0);
            }
        }

        // ── 3. EQUATORIAL HIGHLIGHT ───────────────────────────────────────────
        if (tick % 4 == 0) {
            int eqPoints = 48;
            for (int i = 0; i < eqPoints; i++) {
                double theta = Math.PI * 2 * i / eqPoints + Math.toRadians(rotation * 1.5);
                double px = cx + radius * Math.cos(theta);
                double pz = cz + radius * Math.sin(theta);
                world.spawnParticles(ParticleTypes.SOUL, px, cy, pz, 1, 0, 0.1, 0, 0.01);
                // Double-layer for density
                world.spawnParticles(ParticleTypes.PORTAL, px, cy + 0.3, pz, 1, 0.05, 0.05, 0.05, 0);
            }
        }

        // ── 4. GROUND RING ────────────────────────────────────────────────────
        if (tick % 3 == 0) {
            double groundY = cy - radius + 1.5; // near the floor of the sphere
            int gPoints = 32;
            double gRadius = radius * 0.95;
            for (int i = 0; i < gPoints; i++) {
                double theta = Math.PI * 2 * i / gPoints + Math.toRadians(rotation * 2.0);
                double px = cx + gRadius * Math.cos(theta);
                double pz = cz + gRadius * Math.sin(theta);
                world.spawnParticles(ParticleTypes.WARPED_SPORE, px, groundY, pz, 1, 0, 0, 0, 0.02);
            }
            // Inner ground concentric ring
            double gRadius2 = radius * 0.55;
            for (int i = 0; i < 20; i++) {
                double theta = Math.PI * 2 * i / 20 - Math.toRadians(rotation * 1.5);
                double px = cx + gRadius2 * Math.cos(theta);
                double pz = cz + gRadius2 * Math.sin(theta);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, px, groundY, pz, 1, 0, 0, 0, 0.01);
            }
        }

        // ── 5. POLAR CAPS ─────────────────────────────────────────────────────
        if (tick % 3 == 1) {
            // Top pole burst
            double topY = cy + radius;
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10 + Math.toRadians(rotation * 3.0);
                double capR = radius * 0.2;
                world.spawnParticles(ParticleTypes.PORTAL,
                        cx + capR * Math.cos(angle), topY, cz + capR * Math.sin(angle),
                        1, 0.05, 0.05, 0.05, 0);
            }
            world.spawnParticles(ParticleTypes.SOUL, cx, topY, cz, 2, 0.1, 0, 0.1, 0.02);

            // Bottom pole burst
            double botY = cy - radius;
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10 - Math.toRadians(rotation * 3.0);
                double capR = radius * 0.2;
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        cx + capR * Math.cos(angle), botY, cz + capR * Math.sin(angle),
                        1, 0.05, 0.05, 0.05, 0);
            }
            world.spawnParticles(ParticleTypes.PORTAL, cx, botY, cz, 2, 0.1, 0, 0.1, 0.02);
        }

        // ── 6. AMBIENT INTERIOR PARTICLES ─────────────────────────────────────
        if (tick % 5 == 0) {
            java.util.Random rng = new java.util.Random();
            int ambientCount = 12;
            double ir = innerR * 0.9;
            for (int i = 0; i < ambientCount; i++) {
                // Random point in sphere via rejection sampling
                double ax, ay, az;
                do {
                    ax = (rng.nextDouble() * 2 - 1) * ir;
                    ay = (rng.nextDouble() * 2 - 1) * ir;
                    az = (rng.nextDouble() * 2 - 1) * ir;
                } while (ax * ax + ay * ay + az * az > ir * ir);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        cx + ax, cy + ay, cz + az, 1, 0, 0, 0, 0.03);
            }
        }
    }

    private void applyDomainGoals() {
        // Remove only phase-specific ability goals from the selector.
        // Preserve base movement goals and this DomainGoal itself so it keeps ticking.
        var toRemove = boss.getGoalSelector().getGoals().stream()
                .map(net.minecraft.entity.ai.goal.PrioritizedGoal::getGoal)
                .filter(g -> g != this
                        && !(g instanceof SwimGoal)
                        && !(g instanceof MeleeAttackGoal)
                        && !(g instanceof net.minecraft.entity.ai.goal.WanderAroundFarGoal)
                        && !(g instanceof net.minecraft.entity.ai.goal.LookAtEntityGoal)
                        && !(g instanceof net.minecraft.entity.ai.goal.LookAroundGoal))
                .toList();
        toRemove.forEach(boss.getGoalSelector()::remove);
        // Domain attack goals are ticked manually — no need to add them to the selector
    }

    private void sendTaunt(ServerWorld world, String message) {
        var bossName = boss.getCustomName();
        net.minecraft.text.Text text = net.minecraft.text.Text.literal("[")
                .formatted(net.minecraft.util.Formatting.DARK_PURPLE)
                .append(bossName != null ? bossName.copy() : net.minecraft.text.Text.literal("Boss"))
                .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.DARK_PURPLE))
                .append(com.fiw.fiw_bosses.util.TextUtil.parseColorCodes(message));
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(domainCenter.x, domainCenter.y, domainCenter.z) <= (radius + 32) * (radius + 32)) {
                player.sendMessage(text, false);
            }
        }
    }

    // ── Inner class: manually-driven goal ────────────────────────────────────

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
            if (boss.getWorld().isClient) return;

            if (running) {
                if (goal.shouldContinue()) {
                    goal.tick();
                } else {
                    goal.stop();
                    running = false;
                    cooldownTimer = cooldownTicks;
                }
            } else {
                if (cooldownTimer > 0) {
                    cooldownTimer--;
                } else if (goal.canStart()) {
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
