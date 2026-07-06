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

public class RiftCleaveGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final double range;
    private final double width;
    private final double knockback;
    private final int windupTicks;
    private final int lingerTicks;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;
    private boolean cleaved;
    private Vec3 start;
    private Vec3 end;
    private Vec3 direction;
    private final Set<UUID> hitEntities = new HashSet<>();

    public RiftCleaveGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 16.0f;
        this.range = params.has("range") ? params.get("range").getAsDouble() : 18.0;
        this.width = params.has("width") ? params.get("width").getAsDouble() : 1.4;
        this.knockback = params.has("knockback") ? params.get("knockback").getAsDouble() : 1.2;
        this.windupTicks = params.has("windupTicks") ? params.get("windupTicks").getAsInt() : 28;
        this.lingerTicks = params.has("lingerTicks") ? params.get("lingerTicks").getAsInt() : 12;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= range + 6.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        cleaved = false;
        hitEntities.clear();

        LivingEntity target = boss.getTarget();
        start = boss.position().add(0, 0.1, 0);
        Vec3 rawDir = target != null && target.isAlive()
                ? target.position().subtract(boss.position())
                : boss.getLookAngle();
        direction = new Vec3(rawDir.x, 0, rawDir.z);
        if (direction.lengthSqr() < 1.0e-6) direction = boss.getLookAngle();
        direction = new Vec3(direction.x, 0, direction.z).normalize();
        end = start.add(direction.scale(range));

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4f, 0.55f);
            sendTaunt(level);
        }
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        if (tick <= windupTicks) {
            spawnWarning(level);
            if (tick % 8 == 0) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.7f, 0.7f);
            }
            return;
        }

        if (!cleaved) {
            cleaved = true;
            cleave(level);
        } else {
            spawnRift(level, 0.35);
        }

        if (tick >= windupTicks + lingerTicks) {
            active = false;
        }
    }

    @Override
    public void stop() {
        active = false;
        cooldownTimer = cooldown;
        hitEntities.clear();
    }

    private void spawnWarning(ServerLevel level) {
        double progress = (double) tick / Math.max(1, windupTicks);
        int steps = Math.max(6, (int) (range * 1.4));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = start.add(direction.scale(range * t));
            double pulse = Math.sin((progress * Math.PI) + i * 0.4) * 0.15;
            level.sendParticles(ParticleTypes.SOUL,
                    p.x, p.y + 0.05 + pulse, p.z, 1, width * 0.2, 0.02, width * 0.2, 0.01);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                        p.x, p.y + 0.08, p.z, 1, 0.05, 0.02, 0.05, 0.0);
            }
        }
    }

    private void spawnRift(ServerLevel level, double spread) {
        int steps = Math.max(8, (int) (range * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = start.add(direction.scale(range * t));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    p.x, p.y + 0.1, p.z, 1, spread, 0.02, spread, 0.02);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        p.x, p.y + 0.2, p.z, 1, spread * 0.4, 0.05, spread * 0.4, 0.03);
            }
        }
    }

    private void cleave(ServerLevel level) {
        spawnRift(level, 0.45);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.2f, 0.75f);

        AABB area = new AABB(
                Math.min(start.x, end.x) - width - 1.0, start.y - 1.0, Math.min(start.z, end.z) - width - 1.0,
                Math.max(start.x, end.x) + width + 1.0, start.y + 3.0, Math.max(start.z, end.z) + width + 1.0);

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> boss.canAbilityHit(e) && isOnRift(e.position().add(0, e.getBbHeight() * 0.5, 0)));

        for (LivingEntity victim : victims) {
            if (!hitEntities.add(victim.getUUID())) continue;
            victim.hurtServer(level, boss.damageSources().magic(), damage);
            Vec3 away = new Vec3(victim.getX() - boss.getX(), 0, victim.getZ() - boss.getZ());
            if (away.lengthSqr() < 1.0e-6) away = direction;
            away = away.normalize();
            victim.push(away.x * knockback, 0.35, away.z * knockback);
            victim.hurtMarked = true;
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private boolean isOnRift(Vec3 point) {
        Vec3 toPoint = point.subtract(start);
        double proj = toPoint.dot(direction);
        if (proj < 0 || proj > range) return false;
        Vec3 closest = start.add(direction.scale(proj));
        return closest.distanceToSqr(point) <= width * width;
    }

    private void sendTaunt(ServerLevel level) {
        if (taunt == null) return;
        var bossName = boss.getCustomName();
        Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(taunt));
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= 48 * 48) {
                player.sendSystemMessage(tauntText);
            }
        }
    }
}
