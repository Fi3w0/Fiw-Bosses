package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class FireArrowGoal extends Goal {

    private static final int STATE_CHARGE = 0;
    private static final int STATE_FLY    = 1;

    private final BossEntity boss;
    private final int chargeTime;
    private final float damage;
    private final float explosionRadius;
    private final double speed;
    private final double range;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private int state;
    private boolean active;

    private LivingEntity lockedTarget;
    private Vec3 arrowPos;
    private Vec3 arrowDir;
    private double traveled;

    public FireArrowGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss            = boss;
        this.chargeTime      = params.has("chargeTime")      ? params.get("chargeTime").getAsInt()      : 25;
        this.damage          = params.has("damage")          ? params.get("damage").getAsFloat()          : 20.0f;
        this.explosionRadius = params.has("explosionRadius") ? params.get("explosionRadius").getAsFloat() : 4.0f;
        this.speed           = params.has("speed")           ? params.get("speed").getAsDouble()          : 2.5;
        this.range           = params.has("range")           ? params.get("range").getAsDouble()          : 25.0;
        this.cooldown        = cooldownTicks;
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
        state        = STATE_CHARGE;
        active       = true;
        lockedTarget = boss.getTarget();
        traveled     = 0;
        arrowPos     = null;
        arrowDir     = null;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (state == STATE_CHARGE) {
            if (lockedTarget != null) boss.getLookControl().setLookAt(lockedTarget, 360, 90);

            double chargeProgress = (double) tick / chargeTime;
            double swirlR = 2.5 * (1.0 - chargeProgress) + 0.5;
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(i * 120.0 + tick * 18.0);
                double px = boss.getX() + Math.cos(angle) * swirlR;
                double pz = boss.getZ() + Math.sin(angle) * swirlR;
                level.sendParticles(ParticleTypes.FLAME,
                        px, boss.getY() + 1.0, pz, 1, 0.05, 0.05, 0.05, 0.02);
            }
            level.sendParticles(ParticleTypes.LAVA,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0.1, 0.1, 0.1, 0.0);

            if (tick == chargeTime / 2) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1.0f, 2.0f);
            }

            if (tick >= chargeTime) {
                Vec3 origin = boss.position().add(0, 1.4, 0);
                Vec3 targetPos = lockedTarget != null
                        ? lockedTarget.position().add(0, 1.0, 0)
                        : boss.position().add(boss.getLookAngle().scale(range));
                arrowDir = targetPos.subtract(origin).normalize();
                arrowPos = origin;
                traveled = 0;

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.2f, 1.0f);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 1.1f);

                state = STATE_FLY;
            }

        } else if (state == STATE_FLY) {
            int substeps = (int) Math.ceil(speed);
            double stepSize = speed / substeps;

            for (int s = 0; s < substeps; s++) {
                arrowPos = arrowPos.add(arrowDir.scale(stepSize));
                traveled += stepSize;

                level.sendParticles(ParticleTypes.FLAME,
                        arrowPos.x, arrowPos.y, arrowPos.z, 3, 0.1, 0.1, 0.1, 0.05);
                level.sendParticles(ParticleTypes.LAVA,
                        arrowPos.x, arrowPos.y, arrowPos.z, 1, 0.05, 0.05, 0.05, 0.0);

                AABB hitBox = new AABB(
                        arrowPos.x - 1.0, arrowPos.y - 1.0, arrowPos.z - 1.0,
                        arrowPos.x + 1.0, arrowPos.y + 1.0, arrowPos.z + 1.0);

                List<Player> hit = level.getEntitiesOfClass(Player.class, hitBox, p -> p.isAlive());

                if (!hit.isEmpty()) {
                    explode(level, arrowPos);
                    active = false;
                    return;
                }

                if (traveled >= range) {
                    explode(level, arrowPos);
                    active = false;
                    return;
                }
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
    }

    private void explode(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);

        double spread = explosionRadius * 0.4;
        level.sendParticles(ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 60, spread, spread, spread, 0.2);

        double lavaSpread = explosionRadius * 0.3;
        level.sendParticles(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, 20, lavaSpread, lavaSpread, lavaSpread, 0.0);

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 1.2f);

        AABB blastBox = new AABB(
                pos.x - explosionRadius, pos.y - explosionRadius, pos.z - explosionRadius,
                pos.x + explosionRadius, pos.y + explosionRadius, pos.z + explosionRadius);

        List<Player> players = level.getEntitiesOfClass(Player.class, blastBox, p -> p.isAlive());

        for (Player player : players) {
            double dist = player.position().distanceTo(pos);
            if (dist > explosionRadius) continue;

            float falloff = (float) (1.0 - dist / explosionRadius);
            player.hurt(boss.damageSources().magic(), damage * falloff);

            Vec3 knock = player.position().subtract(pos).normalize().scale(1.5 * falloff);
            player.push(knock.x, knock.y, knock.z);
            player.hurtMarked = true;
        }
    }
}
