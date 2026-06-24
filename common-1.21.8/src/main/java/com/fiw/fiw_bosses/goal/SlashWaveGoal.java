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

public class SlashWaveGoal extends Goal {

    private final BossEntity boss;
    private final float damage;
    private final float speed;
    private final float range;
    private final float width;
    private final int chargeTime;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;

    private Vec3 origin;
    private Vec3 forward;
    private Vec3 right;
    private double traveled;
    private Vec3 slashPos;

    private final Set<UUID> alreadyHit = new HashSet<>();

    private static final int BLADE_HEIGHT = 3;

    public SlashWaveGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss       = boss;
        this.damage     = params.has("damage")     ? params.get("damage").getAsFloat()     : 14.0f;
        this.speed      = params.has("speed")      ? params.get("speed").getAsFloat()      : 1.8f;
        this.range      = params.has("range")      ? params.get("range").getAsFloat()      : 20.0f;
        this.width      = params.has("width")      ? params.get("width").getAsFloat()      : 1.4f;
        this.chargeTime = params.has("chargeTime") ? params.get("chargeTime").getAsInt()   : 10;
        this.taunt      = params.has("taunt")      ? params.get("taunt").getAsString()     : null;
        this.cooldown   = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) <= range + 5;
    }

    @Override
    public void start() {
        tick = 0;
        traveled = 0;
        alreadyHit.clear();

        LivingEntity target = boss.getTarget();
        if (target != null) boss.getLookControl().setLookAt(target, 360, 90);
        lockOrientation();

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.25f);

            if (taunt != null) {
                var bossName = boss.getCustomName();
                Component text = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                        .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                        .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(taunt));
                for (var p : level.players()) {
                    if (p.distanceToSqr(boss) <= 48 * 48) p.sendSystemMessage(text);
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < chargeTime || traveled < range;
    }

    @Override
    public void tick() {
        tick++;
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        boss.hurtMarked = true;

        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        if (tick <= chargeTime) {
            float t = (float) tick / chargeTime;
            LivingEntity target = boss.getTarget();
            if (target != null && tick < chargeTime) {
                boss.getLookControl().setLookAt(target, 360, 90);
                lockOrientation();
            }

            Vec3 handPos = origin.add(forward.scale(0.8)).add(0, 1.1, 0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    handPos.x, handPos.y, handPos.z,
                    (int)(1 + t * 6), 0.15, 0.2, 0.15, 0.08);
            level.sendParticles(ParticleTypes.CRIT,
                    handPos.x, handPos.y, handPos.z,
                    2, 0.1, 0.15, 0.1, 0.1);

            if (tick == chargeTime) {
                level.playSound(null, origin.x, origin.y, origin.z,
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5f, 1.4f);
                level.playSound(null, origin.x, origin.y, origin.z,
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.5f, 1.1f);
                slashPos = origin.add(forward.scale(0.5));
            }
            return;
        }

        Vec3 prevPos = slashPos;
        slashPos = slashPos.add(forward.scale(speed));
        traveled += speed;

        for (int layer = 0; layer < BLADE_HEIGHT; layer++) {
            double layerY = slashPos.y + 0.3 + layer * 0.65;

            for (double side = -width; side <= width; side += width) {
                double px = slashPos.x + right.x * side;
                double pz = slashPos.z + right.z * side;

                level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, layerY, pz, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.CRIT, px, layerY, pz, 3, 0.1, 0.1, 0.1, 0.18);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, px, layerY, pz, 2, 0.08, 0.08, 0.08, 0.1);
            }
        }

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                prevPos.x, prevPos.y + 1.0, prevPos.z,
                2, 0.1, 0.4, 0.1, 0.02);

        if (traveled >= range * 0.5 - speed && traveled <= range * 0.5 + speed) {
            level.sendParticles(ParticleTypes.FLASH,
                    slashPos.x, slashPos.y + 1.0, slashPos.z, 1, 0, 0, 0, 0);
        }

        AABB hitBox = new AABB(
                slashPos.x - width - 0.3, slashPos.y - 0.3, slashPos.z - width - 0.3,
                slashPos.x + width + 0.3, slashPos.y + BLADE_HEIGHT * 0.65 + 0.5, slashPos.z + width + 0.3);

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != boss && e.isAlive() && !boss.isMinion(e) && !alreadyHit.contains(e.getUUID()));

        for (LivingEntity victim : victims) {
            victim.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
            alreadyHit.add(victim.getUUID());

            Vec3 knock = victim.position().subtract(slashPos);
            double sideComponent = knock.dot(right);
            Vec3 knockDir = (sideComponent >= 0 ? right : right.reverse())
                    .add(forward.scale(0.4)).normalize();
            victim.push(knockDir.x * 0.9, 0.5, knockDir.z * 0.9);
            victim.hurtMarked = true;

            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                    14, 0.5, 0.4, 0.5, 0.28);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                    1, 0, 0, 0, 0);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 0.8f);
        }

        if (traveled >= range) {
            level.sendParticles(ParticleTypes.FLASH,
                    slashPos.x, slashPos.y + 1.0, slashPos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT,
                    slashPos.x, slashPos.y + 1.0, slashPos.z,
                    10, 0.5, 0.6, 0.5, 0.3);
            level.playSound(null, slashPos.x, slashPos.y, slashPos.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 1.8f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void lockOrientation() {
        Vec3 fwd = boss.getViewVector(1.0f);
        forward = new Vec3(fwd.x, 0, fwd.z).normalize();
        right   = new Vec3(-forward.z, 0, forward.x);
        origin  = boss.position();
        slashPos = origin;
    }
}
