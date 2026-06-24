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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Sucks nearby players toward the well center and lifts them with Levitation for the
 * channel; when it ends the Levitation expires and they drop, taking optional impact
 * damage. Control/displacement tool distinct from {@code pull}.
 */
public class GravityWellGoal extends Goal {

    private final BossEntity boss;
    private final double radius;
    private final double pullStrength;
    private final int levitationAmplifier;
    private final int duration;
    private final float impactDamage;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private boolean active;

    public GravityWellGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsDouble() : 8.0;
        this.pullStrength = params.has("pullStrength") ? params.get("pullStrength").getAsDouble() : 0.5;
        this.levitationAmplifier = params.has("levitationAmplifier") ? params.get("levitationAmplifier").getAsInt() : 2;
        this.duration = params.has("duration") ? params.get("duration").getAsInt() : 50;
        this.impactDamage = params.has("impactDamage") ? params.get("impactDamage").getAsFloat() : 0.0f;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= radius + 6.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        tick = 0;
        active = true;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4f, 0.6f);
        sendTaunt(level);
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        Vec3 center = boss.position().add(0, 1.0, 0);

        // Swirling funnel particles rising toward the boss.
        for (int i = 0; i < 10; i++) {
            double r = radius * (0.25 + boss.getRandom().nextDouble() * 0.75);
            double angle = Math.toRadians(tick * 26 + i * 36);
            double px = center.x + Math.cos(angle) * r;
            double py = boss.getY() + boss.getRandom().nextDouble() * 3.0;
            double pz = center.z + Math.sin(angle) * r;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, 0.1, 0, 0.02);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 1, 0, 0.05, 0, 0.01);
            }
        }

        AABB area = AABB.ofSize(center, radius * 2, radius * 2 + 4, radius * 2);
        List<Player> victims = level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        && p.distanceToSqr(boss) <= radius * radius);

        for (Player player : victims) {
            // Lift with Levitation (re-applied so it ends shortly after the channel -> drop).
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 12, levitationAmplifier, false, false));
            // Pull horizontally toward the center.
            Vec3 toward = center.subtract(player.position());
            Vec3 horizontal = new Vec3(toward.x, 0, toward.z);
            if (horizontal.lengthSqr() > 1.0) {
                Vec3 push = horizontal.normalize().scale(pullStrength * 0.15);
                player.push(push.x, 0, push.z);
                player.hurtMarked = true;
            }
        }

        if (tick % 12 == 0) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 0.7f, 0.6f);
        }

        if (tick >= duration) {
            active = false;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();
        Vec3 center = boss.position().add(0, 1.0, 0);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.2f, 0.5f);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 0.5, 0.4, 0.5, 0.0);

        if (impactDamage <= 0) return;
        AABB area = AABB.ofSize(center, radius * 2, radius * 2 + 4, radius * 2);
        List<Player> victims = level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        && p.distanceToSqr(boss) <= radius * radius);
        for (Player player : victims) {
            player.hurt(boss.damageSources().magic(), impactDamage);
        }
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
