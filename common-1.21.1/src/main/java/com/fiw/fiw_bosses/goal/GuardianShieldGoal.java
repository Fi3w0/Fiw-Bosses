package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;

public class GuardianShieldGoal extends Goal {

    private static final DustParticleOptions DUST_CYAN =
            new DustParticleOptions(Colors.rgb(0.0f, 0.85f, 0.85f), 1.1f);

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
    private long lastReactedDamageTick = -1L;

    public GuardianShieldGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss             = boss;
        this.counterDamage    = params.has("counterDamage")    ? params.get("counterDamage").getAsFloat()     : 12.0f;
        this.counterKnockback = params.has("counterKnockback") ? params.get("counterKnockback").getAsDouble() : 2.0;
        this.counterWindow    = params.has("counterWindow")    ? params.get("counterWindow").getAsInt()       : 10;
        this.shieldRadius     = params.has("shieldRadius")     ? params.get("shieldRadius").getAsDouble()     : 1.5;
        this.duration         = params.has("duration")         ? params.get("duration").getAsInt()            : 300;
        this.cooldown         = cooldownTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() { return active && age < duration; }

    @Override
    public void start() {
        age    = 0;
        active = true;
        lastReactedDamageTick = boss.getLastDamageTick();
    }

    @Override
    public void tick() {
        age++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        spawnShieldParticles(level);
        checkAndCounterAttack(level);
    }

    private void spawnShieldParticles(ServerLevel level) {
        int    pts    = 16;
        double offset = Math.toRadians(age * 4.0);
        double cy     = boss.getY() + 1.0;

        for (int i = 0; i < pts; i++) {
            double a  = offset + Math.toRadians(i * (360.0 / pts));
            double px = boss.getX() + Math.cos(a) * shieldRadius;
            double pz = boss.getZ() + Math.sin(a) * shieldRadius;
            level.sendParticles(DUST_CYAN, px, cy, pz, 1, 0, 0, 0, 0);
        }

        if (age % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double a  = level.getRandom().nextDouble() * Math.PI * 2;
                double r  = level.getRandom().nextDouble() * shieldRadius;
                double px = boss.getX() + Math.cos(a) * r;
                double pz = boss.getZ() + Math.sin(a) * r;
                level.sendParticles(ParticleTypes.ENCHANT,
                        px, boss.getY() + 0.5 + level.getRandom().nextDouble() * 1.5, pz,
                        1, 0, 0.1, 0, 0.05);
            }
        }
    }

    private void checkAndCounterAttack(ServerLevel level) {
        long hitTick = boss.getLastDamageTick();
        if (hitTick < 0) return;
        if (hitTick == lastReactedDamageTick) return;
        if (level.getGameTime() - hitTick > counterWindow) return;

        Entity attacker = boss.getLastDamageAttacker();
        if (!(attacker instanceof Player player)) return;
        if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;

        lastReactedDamageTick = hitTick;
        executeCounterAttack(level, player);
    }

    private void executeCounterAttack(ServerLevel level, Player target) {
        target.hurt(boss.damageSources().magic(), counterDamage);

        Vec3 dir = target.position().subtract(boss.position()).normalize();
        target.push(dir.x * counterKnockback, 0.4, dir.z * counterKnockback);
        target.hurtMarked = true;

        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 0.5, target.getZ(),
                12, 0.4, 0.3, 0.4, 0.2);
        level.sendParticles(DUST_CYAN,
                target.getX(), target.getY() + 1.0, target.getZ(),
                8, 0.3, 0.3, 0.3, 0);

        Vec3 from = boss.position().add(0, 1.0, 0);
        Vec3 to   = target.position().add(0, 1.0, 0);
        int   steps = Math.max(1, (int) from.distanceTo(to));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            level.sendParticles(ParticleTypes.ENCHANT,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    1, 0, 0, 0, 0);
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.5f, 1.2f);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
