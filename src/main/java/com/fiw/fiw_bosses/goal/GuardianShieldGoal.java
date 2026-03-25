package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * Passive reactive shield.  Cyan particles orbit the boss at all times.
 * When the boss is hit, it counterattacks the attacker with damage + knockback.
 * Does nothing if the boss is not attacked.
 *
 * JSON params:
 *   counterDamage  (float,  12.0)  damage dealt to attacker
 *   counterKnockback (double, 2.0) knockback force away from boss
 *   counterWindow  (int,    10)    ticks after a hit during which counterattack fires
 *   shieldRadius   (double,  1.5)  visual orbit radius
 *   duration       (int,   300)    total active ticks before goal stops
 */
public class GuardianShieldGoal extends Goal {

    private static final DustParticleEffect DUST_CYAN =
            new DustParticleEffect(new Vector3f(0.0f, 0.85f, 0.85f), 1.1f);

    private final BossEntity boss;
    private final float  counterDamage;
    private final double counterKnockback;
    private final int    counterWindow;
    private final double shieldRadius;
    private final int    duration;
    private final int    cooldown;

    private int  cooldownTimer;
    private int  age;
    private boolean active;
    /** Tracks the last damage-tick we already reacted to (avoids double-fires). */
    private long lastReactedDamageTick = -1L;

    public GuardianShieldGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss             = boss;
        this.counterDamage    = params.has("counterDamage")    ? params.get("counterDamage").getAsFloat()     : 12.0f;
        this.counterKnockback = params.has("counterKnockback") ? params.get("counterKnockback").getAsDouble() : 2.0;
        this.counterWindow    = params.has("counterWindow")    ? params.get("counterWindow").getAsInt()       : 10;
        this.shieldRadius     = params.has("shieldRadius")     ? params.get("shieldRadius").getAsDouble()     : 1.5;
        this.duration         = params.has("duration")         ? params.get("duration").getAsInt()            : 300;
        this.cooldown         = cooldownTicks;
        // Passive — runs alongside all other goals
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null;
    }

    @Override
    public boolean shouldContinue() { return active && age < duration; }

    @Override
    public void start() {
        age    = 0;
        active = true;
        lastReactedDamageTick = boss.getLastDamageTick();
    }

    @Override
    public void tick() {
        age++;
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        spawnShieldParticles(world);
        checkAndCounterAttack(world);
    }

    private void spawnShieldParticles(ServerWorld world) {
        int    pts    = 16;
        double offset = Math.toRadians(age * 4.0);
        double cy     = boss.getY() + 1.0;

        for (int i = 0; i < pts; i++) {
            double a  = offset + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * shieldRadius;
            double pz = boss.getZ() + Math.sin(a) * shieldRadius;
            world.spawnParticles(DUST_CYAN, px, cy, pz, 1, 0, 0, 0, 0);
        }

        // Scattered ENCHANT inside the sphere
        if (age % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double a  = world.getRandom().nextDouble() * Math.PI * 2;
                double r  = world.getRandom().nextDouble() * shieldRadius;
                double px = boss.getX() + Math.cos(a) * r;
                double pz = boss.getZ() + Math.sin(a) * r;
                world.spawnParticles(ParticleTypes.ENCHANT,
                        px, boss.getY() + 0.5 + world.getRandom().nextDouble() * 1.5, pz,
                        1, 0, 0.1, 0, 0.05);
            }
        }
    }

    private void checkAndCounterAttack(ServerWorld world) {
        long hitTick = boss.getLastDamageTick();
        if (hitTick < 0) return;
        if (hitTick == lastReactedDamageTick) return;
        if (world.getTime() - hitTick > counterWindow) return;

        Entity attacker = boss.getLastDamageAttacker();
        if (!(attacker instanceof PlayerEntity player)) return;
        if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;

        lastReactedDamageTick = hitTick;
        executeCounterAttack(world, player);
    }

    private void executeCounterAttack(ServerWorld world, PlayerEntity target) {
        target.damage(boss.getDamageSources().magic(), counterDamage);

        // Knockback away from boss
        Vec3d dir = target.getPos().subtract(boss.getPos()).normalize();
        target.addVelocity(dir.x * counterKnockback, 0.4, dir.z * counterKnockback);
        target.velocityModified = true;

        // Visual
        world.spawnParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 0.5, target.getZ(),
                12, 0.4, 0.3, 0.4, 0.2);
        world.spawnParticles(DUST_CYAN,
                target.getX(), target.getY() + 1.0, target.getZ(),
                8, 0.3, 0.3, 0.3, 0);

        // Line of ENCHANT from boss to target
        Vec3d from = boss.getPos().add(0, 1.0, 0);
        Vec3d to   = target.getPos().add(0, 1.0, 0);
        int   steps = Math.max(1, (int) from.distanceTo(to));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            world.spawnParticles(ParticleTypes.ENCHANT,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    1, 0, 0, 0, 0);
        }

        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.5f, 1.2f);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
