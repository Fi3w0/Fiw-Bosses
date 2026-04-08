package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArcSlashGoal extends Goal {

    private final BossEntity boss;
    private final float arc;
    private final float radius;
    private final float damage;
    private final int duration;
    private final int points;
    private final float yOffset;
    private final float height;
    private final float roll;
    private final float hitRadius;
    private final int cooldown;
    private final String taunt;

    private int cooldownTimer;
    private int slashTick;

    private Vec3 slashOrigin;
    private Vec3 slashForward;
    private Vec3 slashRight;

    private static final int WINDUP_TICKS = 8;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public ArcSlashGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.arc       = params.has("arc")       ? params.get("arc").getAsFloat()       : 180.0f;
        this.radius    = params.has("radius")    ? params.get("radius").getAsFloat()    : 4.5f;
        this.damage    = params.has("damage")    ? params.get("damage").getAsFloat()    : 12.0f;
        this.duration  = params.has("duration")  ? params.get("duration").getAsInt()    : 8;
        this.points    = params.has("points")    ? params.get("points").getAsInt()      : 28;
        this.yOffset   = params.has("yOffset")   ? params.get("yOffset").getAsFloat()   : 1.1f;
        this.height    = params.has("height")    ? params.get("height").getAsFloat()    : 1.0f;
        this.roll      = params.has("roll")      ? params.get("roll").getAsFloat()      : 0.0f;
        this.hitRadius = params.has("hitRadius") ? params.get("hitRadius").getAsFloat() : 1.2f;
        this.cooldown  = cooldownTicks;
        this.taunt     = params.has("taunt")     ? params.get("taunt").getAsString()    : null;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= radius + 3;
    }

    @Override
    public void start() {
        slashTick = -WINDUP_TICKS;
        alreadyHit.clear();

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);

        snapOrientation();

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 0.35f);

            if (taunt != null) sendTaunt(level, taunt);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return slashTick < duration;
    }

    @Override
    public void tick() {
        slashTick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hasImpulse = true;

        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (slashTick <= 0) {
            LivingEntity target = boss.getTarget();
            if (target != null) {
                boss.getLookControl().setLookAt(target, 360, 90);
                snapOrientation();
            }

            float windupT = (float)(WINDUP_TICKS + slashTick) / WINDUP_TICKS;

            if (windupT > 0.4f) {
                for (int i = 0; i <= points; i += 2) {
                    double t = (double) i / points;
                    Vec3 preview = arcPoint(-arc / 2.0 + t * arc, t);
                    level.sendParticles(ParticleTypes.SMOKE,
                            preview.x, preview.y, preview.z, 1, 0, 0, 0, 0);
                }
            }

            Vec3 startPos = arcPoint(-arc / 2.0, 0.0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    startPos.x, startPos.y, startPos.z,
                    (int)(1 + windupT * 5), 0.22, 0.22, 0.22, 0.06);
            level.sendParticles(ParticleTypes.CRIT,
                    startPos.x, startPos.y, startPos.z,
                    2, 0.12, 0.12, 0.12, 0.08);
            return;
        }

        if (slashTick == 1) {
            level.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 1.2f);
            level.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 0.9f);
        }

        double prevT = (double)(slashTick - 1) / duration;
        double currT = (double) slashTick / duration;
        int iStart = (int)(prevT * points);
        int iEnd   = Math.min(points, (int)(currT * points) + 1);

        for (int pi = iStart; pi <= iEnd; pi++) {
            double t = (double) pi / points;
            double thetaDeg = -arc / 2.0 + t * arc;
            Vec3 pos = arcPoint(thetaDeg, t);

            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 4, 0.07, 0.07, 0.07, 0.20);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 2, 0.07, 0.07, 0.07, 0.14);

            if (pi % 2 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.004);
            }

            if (pi == points / 2) {
                level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }

            AABB hitBox = new AABB(
                    pos.x - hitRadius, pos.y - hitRadius - 0.5, pos.z - hitRadius,
                    pos.x + hitRadius, pos.y + hitRadius + 0.5, pos.z + hitRadius);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, hitBox,
                    e -> e != boss && e.isAlive() && !boss.isMinion(e)
                            && !alreadyHit.contains(e.getUUID()));

            for (LivingEntity victim : victims) {
                victim.hurt(boss.damageSources().mobAttack(boss), damage);
                alreadyHit.add(victim.getUUID());

                Vec3 knock = victim.position().subtract(boss.position()).normalize();
                victim.push(knock.x * 0.8, 0.4, knock.z * 0.8);
                victim.hasImpulse = true;

                level.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                        16, 0.45, 0.45, 0.45, 0.28);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                        8, 0.3, 0.3, 0.3, 0.12);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                        1, 0, 0, 0, 0);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.3f, 0.75f);
            }
        }

        if (slashTick == duration) {
            Vec3 endPos = arcPoint(arc / 2.0, 1.0);
            level.sendParticles(ParticleTypes.FLASH, endPos.x, endPos.y, endPos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT,
                    endPos.x, endPos.y, endPos.z, 8, 0.3, 0.3, 0.3, 0.3);
            level.playSound(null, slashOrigin.x, slashOrigin.y, slashOrigin.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private Vec3 arcPoint(double thetaDeg, double t) {
        double theta = Math.toRadians(thetaDeg);

        double hx = slashOrigin.x
                + radius * (Math.cos(theta) * slashForward.x + Math.sin(theta) * slashRight.x);
        double hz = slashOrigin.z
                + radius * (Math.cos(theta) * slashForward.z + Math.sin(theta) * slashRight.z);

        double vertArc = Math.sin(Math.PI * t);
        double lateralFrac = t * 2.0 - 1.0;
        double rollOffset = lateralFrac * Math.sin(Math.toRadians(roll)) * height;
        double hy = slashOrigin.y + yOffset + height * vertArc + rollOffset;

        return new Vec3(hx, hy, hz);
    }

    private void snapOrientation() {
        Vec3 fwd = boss.getViewVector(1.0f);
        slashForward = new Vec3(fwd.x, 0, fwd.z).normalize();
        slashRight   = new Vec3(-slashForward.z, 0, slashForward.x);
        slashOrigin  = boss.position();
    }

    private void sendTaunt(ServerLevel level, String message) {
        var bossName = boss.getCustomName();
        Component text = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(message));
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= 48 * 48) {
                player.sendSystemMessage(text);
            }
        }
    }
}
