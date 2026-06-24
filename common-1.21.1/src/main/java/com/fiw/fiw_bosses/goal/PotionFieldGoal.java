package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.util.Colors;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class PotionFieldGoal extends Goal {

    private static final int STATE_WINDUP  = 0;
    private static final int STATE_FLY     = 1;
    private static final int STATE_FIELD   = 2;
    private static final int WINDUP_TICKS = 20;

    private final BossEntity boss;
    private final Holder<MobEffect> effect;
    private final int    amplifier;
    private final int    effectDuration;
    private final int    applyInterval;
    private final int    fieldDuration;
    private final double fieldRadius;
    private final float  damage;
    private final double throwSpeed;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;
    private int     fieldAge;
    private int     applyTimer;

    private Vec3 projectilePos;
    private Vec3 projectileVel;
    private Vec3 fieldCenter;

    private static final DustParticleOptions DUST_PURPLE =
            new DustParticleOptions(Colors.rgb(0.6f, 0.0f, 1.0f), 1.2f);
    private static final DustParticleOptions DUST_VIOLET =
            new DustParticleOptions(Colors.rgb(0.4f, 0.0f, 0.8f), 0.9f);

    public PotionFieldGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss          = boss;
        this.amplifier     = params.has("amplifier")      ? params.get("amplifier").getAsInt()         : 1;
        this.effectDuration= params.has("effectDuration") ? params.get("effectDuration").getAsInt()    : 100;
        this.applyInterval = params.has("applyInterval")  ? params.get("applyInterval").getAsInt()     : 20;
        this.fieldDuration = params.has("fieldDuration")  ? params.get("fieldDuration").getAsInt()     : 200;
        this.fieldRadius   = params.has("fieldRadius")    ? params.get("fieldRadius").getAsDouble()    : 5.0;
        this.damage        = params.has("damage")         ? params.get("damage").getAsFloat()          : 0.0f;
        this.throwSpeed    = params.has("throwSpeed")     ? params.get("throwSpeed").getAsDouble()     : 0.6;
        this.cooldown      = cooldownTicks;

        Holder<MobEffect> resolved = MobEffects.MOVEMENT_SLOWDOWN;
        if (params.has("effect")) {
            ResourceLocation id = ResourceLocation.tryParse(params.get("effect").getAsString());
            if (id != null) {
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
                if (holder.isPresent()) resolved = holder.get();
            }
        }
        this.effect = resolved;

        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return active; }

    @Override
    public void start() {
        tick       = 0;
        state      = STATE_WINDUP;
        active     = true;
        fieldAge   = 0;
        applyTimer = 0;
        projectilePos = null;
        projectileVel = null;
        fieldCenter   = null;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        switch (state) {
            case STATE_WINDUP -> tickWindup(level);
            case STATE_FLY    -> tickFly(level);
            case STATE_FIELD  -> tickField(level);
        }
    }

    private void tickWindup(ServerLevel level) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().setLookAt(target, 30, 30);
        }

        double swirl = Math.toRadians(tick * 20.0);
        for (int i = 0; i < 3; i++) {
            double a = swirl + Math.toRadians(i * 120.0);
            double px = boss.getX() + Math.cos(a) * 0.8;
            double py = boss.getY() + 1.2;
            double pz = boss.getZ() + Math.sin(a) * 0.8;
            level.sendParticles(DUST_PURPLE, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(ParticleTypes.WITCH, px, py, pz, 1, 0.05, 0.05, 0.05, 0.05);
        }

        if (tick >= WINDUP_TICKS) {
            Vec3 start = boss.position().add(0, 1.2, 0);
            Vec3 dir;
            if (target != null && target.isAlive()) {
                dir = target.position().add(0, 0.5, 0).subtract(start).normalize();
            } else {
                dir = boss.getLookAngle();
            }
            projectilePos = start;
            projectileVel = dir.scale(throwSpeed);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 1.2f, 0.8f);
            state = STATE_FLY;
        }
    }

    private void tickFly(ServerLevel level) {
        projectileVel = new Vec3(projectileVel.x, projectileVel.y - 0.04, projectileVel.z);
        projectilePos = projectilePos.add(projectileVel);

        level.sendParticles(DUST_PURPLE,
                projectilePos.x, projectilePos.y, projectilePos.z,
                2, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.WITCH,
                projectilePos.x, projectilePos.y, projectilePos.z,
                2, 0.1, 0.1, 0.1, 0.05);

        BlockPos belowPos = BlockPos.containing(projectilePos.x, projectilePos.y - 0.5, projectilePos.z);
        boolean hitGround = !level.isEmptyBlock(belowPos);
        boolean tooLow = projectilePos.y < level.getMinBuildHeight();

        if (hitGround || tooLow) {
            land(level, projectilePos);
        }
    }

    private void land(ServerLevel level, Vec3 pos) {
        fieldCenter = pos;

        level.sendParticles(DUST_PURPLE,
                pos.x, pos.y + 0.1, pos.z,
                40, fieldRadius * 0.5, 0.2, fieldRadius * 0.5, 0.0);
        level.sendParticles(ParticleTypes.WITCH,
                pos.x, pos.y + 0.1, pos.z,
                30, fieldRadius * 0.4, 0.3, fieldRadius * 0.4, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.5, pos.z,
                5, 0.5, 0.3, 0.5, 0.1);

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);

        if (damage > 0) {
            double rSq = fieldRadius * fieldRadius;
            AABB box = new AABB(pos.x - fieldRadius, pos.y - 1, pos.z - fieldRadius,
                                pos.x + fieldRadius, pos.y + 3, pos.z + fieldRadius);
            List<Player> players = level.getEntitiesOfClass(Player.class, box,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && p.distanceToSqr(pos.x, pos.y, pos.z) <= rSq);
            for (Player p : players) {
                p.hurt(boss.damageSources().magic(), damage);
            }
        }

        state = STATE_FIELD;
    }

    private void tickField(ServerLevel level) {
        fieldAge++;
        applyTimer++;

        int ringPoints = 20;
        double angle = Math.toRadians(fieldAge * 3.0);
        for (int i = 0; i < ringPoints; i++) {
            double a = angle + Math.toRadians(i * (360.0 / ringPoints));
            double px = fieldCenter.x + Math.cos(a) * fieldRadius;
            double pz = fieldCenter.z + Math.sin(a) * fieldRadius;
            level.sendParticles(DUST_VIOLET,
                    px, fieldCenter.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (fieldAge % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double a2  = level.getRandom().nextDouble() * Math.PI * 2;
                double r2  = level.getRandom().nextDouble() * fieldRadius;
                double px  = fieldCenter.x + Math.cos(a2) * r2;
                double pz  = fieldCenter.z + Math.sin(a2) * r2;
                level.sendParticles(ParticleTypes.WITCH,
                        px, fieldCenter.y + 0.1, pz, 1, 0.1, 0.4, 0.1, 0.05);
                level.sendParticles(ParticleTypes.ENCHANT,
                        px, fieldCenter.y + 0.1, pz, 1, 0.1, 0.5, 0.1, 0.08);
            }
        }

        if (applyTimer >= applyInterval) {
            applyTimer = 0;
            double rSq = fieldRadius * fieldRadius;
            AABB box = new AABB(
                    fieldCenter.x - fieldRadius, fieldCenter.y - 1, fieldCenter.z - fieldRadius,
                    fieldCenter.x + fieldRadius, fieldCenter.y + 3, fieldCenter.z + fieldRadius);
            List<Player> players = level.getEntitiesOfClass(Player.class, box,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && p.distanceToSqr(fieldCenter.x, fieldCenter.y, fieldCenter.z) <= rSq);
            for (Player p : players) {
                p.addEffect(new MobEffectInstance(effect, effectDuration, amplifier, false, true));
            }
        }

        if (fieldAge >= fieldDuration) {
            active = false;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
    }
}
