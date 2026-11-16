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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class FreezeGoal extends Goal {

    private final BossEntity boss;
    private final int duration;
    private final int intensity;
    private final float radius;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private List<LivingEntity> frozenTargets;

    public FreezeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss      = boss;
        this.duration  = params.has("duration")  ? params.get("duration").getAsInt()   : 60;
        this.intensity = params.has("intensity") ? params.get("intensity").getAsInt()  : 140;
        this.radius    = params.has("radius")    ? params.get("radius").getAsFloat()   : 8.0f;
        this.taunt     = params.has("taunt")     ? params.get("taunt").getAsString()   : null;
        this.cooldown  = cooldownTicks;
        this.frozenTargets = new ArrayList<>();
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) <= radius * radius;
    }

    @Override
    public boolean canContinueToUse() {
        return tick < duration;
    }

    @Override
    public void start() {
        tick = 0;
        frozenTargets = new ArrayList<>();
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        AABB area = boss.getBoundingBox().inflate(radius);
        frozenTargets = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> boss.canAbilityHit(e)
                     && boss.distanceToSqr(e) <= radius * radius);

        if (frozenTargets.isEmpty()) return;

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.POWDER_SNOW_PLACE, SoundSource.HOSTILE, 1.5f, 0.5f);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 1.2f, 1.0f);

        for (LivingEntity entity : frozenTargets) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    16, 0.4, 0.6, 0.4, 0.04);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    10, 0.3, 0.3, 0.3, 0.08);
        }

        if (taunt != null) {
            var bossName = boss.getCustomName();
            Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(taunt));
            for (var player : level.players()) {
                if (player.distanceToSqr(boss) <= 48 * 48)
                    player.sendSystemMessage(tauntText);
            }
        }
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        frozenTargets.removeIf(e -> !e.isAlive());
        for (LivingEntity entity : frozenTargets) {
            entity.setTicksFrozen(intensity);
        }

        if (tick % 4 == 0) {
            for (LivingEntity entity : frozenTargets) {
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        3, 0.3, 0.4, 0.3, 0.02);
            }
        }

        if (tick == duration - 20 && duration > 20) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 1.5f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        frozenTargets.clear();
    }
}
