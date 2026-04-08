package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;

/**
 * Boss throws a potion that creates a persistent effect field on the ground.
 *
 * JSON params:
 *   effect        (String, "minecraft:slowness")  status effect identifier
 *   amplifier     (int,     1)                    effect amplifier (0 = level I)
 *   effectDuration(int,   100)                    ticks of effect applied each interval
 *   applyInterval (int,    20)                    ticks between applying the effect
 *   fieldDuration (int,   200)                    total ticks the field persists
 *   fieldRadius   (double, 5.0)                   radius of the field
 *   damage        (float,  0.0)                   direct damage on landing (0 = none)
 *   throwSpeed    (double, 0.6)                   initial projectile speed
 */
public class PotionFieldGoal extends Goal {

    private static final int STATE_WINDUP  = 0;
    private static final int STATE_FLY     = 1;
    private static final int STATE_FIELD   = 2;

    private static final int WINDUP_TICKS = 20;

    private final BossEntity boss;
    private final StatusEffect effect;
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

    private Vec3d   projectilePos;
    private Vec3d   projectileVel;
    private Vec3d   fieldCenter;

    private static final DustParticleEffect DUST_PURPLE =
            new DustParticleEffect(new Vector3f(0.6f, 0.0f, 1.0f), 1.2f);
    private static final DustParticleEffect DUST_VIOLET =
            new DustParticleEffect(new Vector3f(0.4f, 0.0f, 0.8f), 0.9f);

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

        // Resolve status effect
        StatusEffect resolved = null;
        if (params.has("effect")) {
            Identifier id = Identifier.tryParse(params.get("effect").getAsString());
            if (id != null) resolved = Registries.STATUS_EFFECT.get(id);
        }
        this.effect = (resolved != null) ? resolved : StatusEffects.SLOWNESS;

        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return active;
    }

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
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        switch (state) {
            case STATE_WINDUP -> tickWindup(world);
            case STATE_FLY    -> tickFly(world);
            case STATE_FIELD  -> tickField(world);
        }
    }

    private void tickWindup(ServerWorld world) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 30, 30);
        }

        // Swirling purple particles around boss hand area
        double swirl = Math.toRadians(tick * 20.0);
        for (int i = 0; i < 3; i++) {
            double a = swirl + Math.toRadians(i * 120.0);
            double px = boss.getX() + Math.cos(a) * 0.8;
            double py = boss.getY() + 1.2;
            double pz = boss.getZ() + Math.sin(a) * 0.8;
            world.spawnParticles(DUST_PURPLE, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.WITCH, px, py, pz, 1, 0.05, 0.05, 0.05, 0.05);
        }

        if (tick >= WINDUP_TICKS) {
            // Launch projectile toward target
            Vec3d start = boss.getPos().add(0, 1.2, 0);
            Vec3d dir;
            if (target != null && target.isAlive()) {
                dir = target.getPos().add(0, 0.5, 0).subtract(start).normalize();
            } else {
                dir = boss.getRotationVector();
            }
            projectilePos = start;
            projectileVel = dir.multiply(throwSpeed);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.HOSTILE, 1.2f, 0.8f);
            state = STATE_FLY;
        }
    }

    private void tickFly(ServerWorld world) {
        // Simple arc: apply gravity each tick
        projectileVel = new Vec3d(projectileVel.x, projectileVel.y - 0.04, projectileVel.z);
        projectilePos = projectilePos.add(projectileVel);

        // Potion particle trail
        world.spawnParticles(DUST_PURPLE,
                projectilePos.x, projectilePos.y, projectilePos.z,
                2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.WITCH,
                projectilePos.x, projectilePos.y, projectilePos.z,
                2, 0.1, 0.1, 0.1, 0.05);

        // Check if it hit the ground (Y <= ground level) or went below -64
        boolean hitGround = !world.isAir(net.minecraft.util.math.BlockPos.ofFloored(
                projectilePos.x, projectilePos.y - 0.5, projectilePos.z));
        boolean tooLow = projectilePos.y < world.getBottomY();

        if (hitGround || tooLow) {
            land(world, projectilePos);
        }
    }

    private void land(ServerWorld world, Vec3d pos) {
        fieldCenter = pos;

        // Landing burst
        world.spawnParticles(DUST_PURPLE,
                pos.x, pos.y + 0.1, pos.z,
                40, fieldRadius * 0.5, 0.2, fieldRadius * 0.5, 0.0);
        world.spawnParticles(ParticleTypes.WITCH,
                pos.x, pos.y + 0.1, pos.z,
                30, fieldRadius * 0.4, 0.3, fieldRadius * 0.4, 0.15);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.5, pos.z,
                5, 0.5, 0.3, 0.5, 0.1);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.HOSTILE, 1.5f, 0.7f);

        // Apply immediate damage if configured
        if (damage > 0) {
            double rSq = fieldRadius * fieldRadius;
            Box box = new Box(pos.x - fieldRadius, pos.y - 1, pos.z - fieldRadius,
                              pos.x + fieldRadius, pos.y + 3, pos.z + fieldRadius);
            List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, box,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && p.squaredDistanceTo(pos.x, pos.y, pos.z) <= rSq);
            for (PlayerEntity p : players) {
                p.damage(boss.getDamageSources().magic(), damage);
            }
        }

        state = STATE_FIELD;
    }

    private void tickField(ServerWorld world) {
        fieldAge++;
        applyTimer++;

        // Ambient field particles — ring on ground + rising wisps
        int ringPoints = 20;
        double angle = Math.toRadians(fieldAge * 3.0);
        for (int i = 0; i < ringPoints; i++) {
            double a = angle + Math.toRadians(i * (360.0 / ringPoints));
            double px = fieldCenter.x + Math.cos(a) * fieldRadius;
            double pz = fieldCenter.z + Math.sin(a) * fieldRadius;
            world.spawnParticles(DUST_VIOLET,
                    px, fieldCenter.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Rising wisps inside field
        if (fieldAge % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double a2  = world.getRandom().nextDouble() * Math.PI * 2;
                double r2  = world.getRandom().nextDouble() * fieldRadius;
                double px  = fieldCenter.x + Math.cos(a2) * r2;
                double pz  = fieldCenter.z + Math.sin(a2) * r2;
                world.spawnParticles(ParticleTypes.WITCH,
                        px, fieldCenter.y + 0.1, pz, 1, 0.1, 0.4, 0.1, 0.05);
                world.spawnParticles(ParticleTypes.ENCHANT,
                        px, fieldCenter.y + 0.1, pz, 1, 0.1, 0.5, 0.1, 0.08);
            }
        }

        // Apply effect on interval
        if (applyTimer >= applyInterval) {
            applyTimer = 0;
            double rSq = fieldRadius * fieldRadius;
            Box box = new Box(
                    fieldCenter.x - fieldRadius, fieldCenter.y - 1, fieldCenter.z - fieldRadius,
                    fieldCenter.x + fieldRadius, fieldCenter.y + 3, fieldCenter.z + fieldRadius);
            List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, box,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && p.squaredDistanceTo(fieldCenter.x, fieldCenter.y, fieldCenter.z) <= rSq);
            for (PlayerEntity p : players) {
                p.addStatusEffect(new StatusEffectInstance(effect, effectDuration, amplifier, false, true));
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
