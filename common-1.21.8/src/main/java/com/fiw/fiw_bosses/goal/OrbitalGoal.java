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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrbitalGoal extends Goal {

    private final BossEntity boss;
    private final int orbCount;
    private final float radius;
    private final float damage;
    private final int duration;
    private final float speed;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int orbitalTick;
    private float currentAngle;
    private final Set<UUID> hitThisTick = new HashSet<>();

    public OrbitalGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.orbCount = params.has("count") ? params.get("count").getAsInt() : 3;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 3.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 6.0f;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 100;
        this.speed = params.has("speed") ? params.get("speed").getAsFloat() : 8.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
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
        orbitalTick = 0;
        currentAngle = 0;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.2f, 1.6f);

            if (taunt != null) {
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
    }

    @Override
    public boolean canContinueToUse() {
        return orbitalTick < duration && boss.getTarget() != null;
    }

    @Override
    public void tick() {
        orbitalTick++;
        currentAngle = (currentAngle + speed) % 360;
        hitThisTick.clear();

        if (boss.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) boss.level();
        double cx = boss.getX();
        double cy = boss.getY() + 1.0;
        double cz = boss.getZ();

        for (int i = 0; i < orbCount; i++) {
            double orbAngle = Math.toRadians(currentAngle + (360.0 / orbCount) * i);
            double ox = cx + Math.cos(orbAngle) * radius;
            double oz = cz + Math.sin(orbAngle) * radius;

            level.sendParticles(ParticleTypes.END_ROD,
                    ox, cy, oz, 3, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    ox, cy, oz, 1, 0.05, 0.05, 0.05, 0.0);

            AABB orbBox = new AABB(ox - 1.0, cy - 1.0, oz - 1.0, ox + 1.0, cy + 1.5, oz + 1.0);
            List<Player> victims = level.getEntitiesOfClass(Player.class, orbBox,
                    p -> p.isAlive() && !hitThisTick.contains(p.getUUID()));

            for (Player victim : victims) {
                victim.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
                hitThisTick.add(victim.getUUID());

                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        4, 0.25, 0.25, 0.25, 0.06);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 0.8f, 1.4f);
            }
        }

        if (orbitalTick % 20 == 0) {
            level.playSound(null, cx, cy, cz,
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.5f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.END_ROD,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    25, radius * 0.5, 0.6, radius * 0.5, 0.25);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 1.5f);
        }
    }
}
