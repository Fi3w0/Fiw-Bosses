package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;

public class ArrowRainGoal extends Goal {

    private static final int STATE_WARN = 0;
    private static final int STATE_RAIN = 1;

    private final BossEntity boss;
    private final double radius;
    private final int    arrowCount;
    private final double height;
    private final float  damage;
    private final int    warnTicks;
    private final int    rainTicks;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     arrowsSpawned;

    private Vec3   rainCenter;

    private static final DustParticleOptions DUST_ORANGE =
            new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.0f), 1.2f);
    private static final DustParticleOptions DUST_RED =
            new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.0f), 1.0f);

    public ArrowRainGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.radius      = params.has("radius")     ? params.get("radius").getAsDouble()    : 8.0;
        this.arrowCount  = params.has("arrowCount") ? params.get("arrowCount").getAsInt()   : 20;
        this.height      = params.has("height")     ? params.get("height").getAsDouble()    : 20.0;
        this.damage      = params.has("damage")     ? params.get("damage").getAsFloat()     : 8.0f;
        this.warnTicks   = params.has("warnTicks")  ? params.get("warnTicks").getAsInt()    : 40;
        this.rainTicks   = params.has("rainTicks")  ? params.get("rainTicks").getAsInt()    : 30;
        this.cooldown    = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_WARN;
        active       = true;
        arrowsSpawned = 0;

        LivingEntity target = boss.getTarget();
        rainCenter = (target != null && target.isAlive())
                ? target.position()
                : boss.position().add(boss.getLookAngle().scale(radius));
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (state == STATE_WARN) {
            tickWarn(level);
        } else {
            tickRain(level);
        }
    }

    private void tickWarn(ServerLevel level) {
        double pulse = 0.8 + 0.2 * Math.sin(tick * 0.4);
        int ringPoints = 32;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.toRadians(360.0 / ringPoints * i + tick * 5.0);
            double px = rainCenter.x + Math.cos(angle) * radius * pulse;
            double pz = rainCenter.z + Math.sin(angle) * radius * pulse;
            level.sendParticles(DUST_ORANGE,
                    px, rainCenter.y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (tick % 4 == 0) {
            int fillCount = 8;
            for (int i = 0; i < fillCount; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                double r     = level.getRandom().nextDouble() * radius;
                double px    = rainCenter.x + Math.cos(angle) * r;
                double pz    = rainCenter.z + Math.sin(angle) * r;
                level.sendParticles(DUST_RED,
                        px, rainCenter.y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        if (tick == 1) {
            level.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.5f, 0.4f);
        }
        if (tick == warnTicks - 5) {
            level.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 2.0f, 0.6f);
        }

        if (tick >= warnTicks) {
            state = STATE_RAIN;
            level.playSound(null, rainCenter.x, rainCenter.y, rainCenter.z,
                    SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 2.5f, 0.8f);
        }
    }

    private void tickRain(ServerLevel level) {
        int rainElapsed = tick - warnTicks;

        if (arrowsSpawned < arrowCount) {
            int arrowsThisTick = (int) Math.ceil(
                    (double)(arrowCount - arrowsSpawned) / Math.max(1, rainTicks - rainElapsed));
            arrowsThisTick = Math.min(arrowsThisTick, arrowCount - arrowsSpawned);

            for (int a = 0; a < arrowsThisTick; a++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                double r     = level.getRandom().nextDouble() * radius;
                double ax    = rainCenter.x + Math.cos(angle) * r;
                double az    = rainCenter.z + Math.sin(angle) * r;
                double ay    = rainCenter.y + height;

                Arrow arrow = new Arrow(level, ax, ay, az, new ItemStack(Items.ARROW), null);
                arrow.setOwner(boss);
                arrow.setBaseDamage(damage);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setDeltaMovement(0, -2.5, 0);
                level.addFreshEntity(arrow);
                arrowsSpawned++;
            }
        }

        if (tick % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                double r     = level.getRandom().nextDouble() * radius;
                double px    = rainCenter.x + Math.cos(angle) * r;
                double pz    = rainCenter.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.CRIT,
                        px, rainCenter.y + height * 0.3, pz, 1, 0.1, 0.3, 0.1, 0.5);
            }
        }

        if (rainElapsed >= rainTicks) {
            active = false;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
