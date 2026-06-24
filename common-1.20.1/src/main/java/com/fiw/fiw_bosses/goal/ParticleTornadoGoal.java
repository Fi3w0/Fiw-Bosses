package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class ParticleTornadoGoal extends Goal {

    private final BossEntity boss;
    private final float maxRadius;
    private final float height;
    private final int duration;
    private final float rotationSpeed;
    private final int disks;
    private final float damage;
    private final boolean fire;
    private final int fireSeconds;
    private final SimpleParticleType particle;
    private final SimpleParticleType accentParticle;
    private final float twistPerDisk;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private int tick;
    private double rotation;
    private Vec3 origin;

    public ParticleTornadoGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss          = boss;
        // "size" is a friendly alias for maxRadius.
        this.maxRadius     = params.has("maxRadius")     ? params.get("maxRadius").getAsFloat()
                           : params.has("size")          ? params.get("size").getAsFloat()          : 4.0f;
        this.height        = params.has("height")        ? params.get("height").getAsFloat()        : 6.0f;
        this.duration      = params.has("duration")      ? params.get("duration").getAsInt()        : 100;
        this.rotationSpeed = params.has("rotationSpeed") ? params.get("rotationSpeed").getAsFloat()
                           : params.has("spinSpeed")     ? params.get("spinSpeed").getAsFloat()      : 12.0f;
        this.disks         = params.has("disks")         ? params.get("disks").getAsInt()           : 14;
        this.damage        = params.has("damage")        ? params.get("damage").getAsFloat()        : 0.0f;
        this.fire          = params.has("fire")          && params.get("fire").getAsBoolean();
        this.fireSeconds   = params.has("fireSeconds")   ? params.get("fireSeconds").getAsInt()      : 4;
        this.particle      = particleOr(params, "particle", ParticleTypes.FLAME);
        this.accentParticle= particleOr(params, "accentParticle", ParticleTypes.SMOKE);
        // degrees of spiral twist per disk — gives the funnel a visible spin.
        this.twistPerDisk  = params.has("twist")         ? params.get("twist").getAsFloat()          : 22.0f;
        this.taunt         = params.has("taunt")         ? params.get("taunt").getAsString()         : null;
        this.cooldown      = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        rotation = 0;
        origin = boss.position();

        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();

            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.5f, 0.4f);

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
    }

    @Override
    public boolean canContinueToUse() {
        return tick < duration;
    }

    @Override
    public void tick() {
        tick++;
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();

        rotation = (rotation + rotationSpeed) % 360.0;
        double rotRad = Math.toRadians(rotation);

        for (int d = 0; d < disks; d++) {
            double t = (double) d / Math.max(1, disks - 1);
            double diskY      = origin.y + t * height;
            double diskRadius = maxRadius * t;          // funnel: narrow at base, wide at top

            int points = Math.max(6, (int) (diskRadius * 6));
            double twist = Math.toRadians(d * twistPerDisk);   // per-disk spiral => visible spin
            for (int p = 0; p < points; p++) {
                double angle = rotRad + twist + Math.toRadians((360.0 / points) * p);
                double px = origin.x + Math.cos(angle) * diskRadius;
                double pz = origin.z + Math.sin(angle) * diskRadius;
                level.sendParticles(particle, px, diskY, pz, 1, 0, 0.03, 0, 0.01);
                if ((d + p) % 4 == 0) {
                    level.sendParticles(accentParticle, px, diskY, pz, 1, 0, 0.02, 0, 0.0);
                }
            }
        }

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                origin.x, origin.y + 0.1, origin.z,
                3, maxRadius * 0.4, 0.1, maxRadius * 0.4, 0.02);

        if ((damage > 0 || fire) && tick % 5 == 0) {
            AABB funnel = new AABB(origin.x - maxRadius, origin.y, origin.z - maxRadius,
                                   origin.x + maxRadius, origin.y + height, origin.z + maxRadius);
            List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, funnel,
                    e -> e != boss && e.isAlive() && !boss.isMinion(e)
                         && isInsideTornado(e));
            for (LivingEntity entity : inside) {
                if (damage > 0) entity.hurt(boss.damageSources().mobAttack(boss), damage);
                if (fire) entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireSeconds * 20));
                Vec3 toCenter = origin.subtract(entity.position()).normalize();
                entity.push(toCenter.x * 0.3, 0.15, toCenter.z * 0.3);
                entity.hurtMarked = true;
            }
        }

        if (tick % 20 == 0) {
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.8f,
                    0.6f + boss.getRandom().nextFloat() * 0.4f);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        if (!boss.level().isClientSide) {
            ServerLevel level = (ServerLevel) boss.level();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    origin.x, origin.y + height * 0.5, origin.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL,
                    origin.x, origin.y + 1, origin.z,
                    20, maxRadius * 0.5, height * 0.3, maxRadius * 0.5, 0.1);
        }
    }

    private boolean isInsideTornado(LivingEntity entity) {
        double relY = entity.getY() - origin.y;
        if (relY < 0 || relY > height) return false;
        double t = relY / height;
        double allowedRadius = maxRadius * t + 1.0;
        double dx = entity.getX() - origin.x;
        double dz = entity.getZ() - origin.z;
        return dx * dx + dz * dz <= allowedRadius * allowedRadius;
    }

    /** Resolves a SimpleParticleType from a `namespace:path` param, falling back to a default. */
    static SimpleParticleType particleOr(JsonObject params, String key, SimpleParticleType def) {
        if (!params.has(key)) return def;
        ResourceLocation id = ResourceLocation.tryParse(params.get(key).getAsString());
        if (id == null) return def;
        var opt = BuiltInRegistries.PARTICLE_TYPE.getOptional(id);
        return (opt.isPresent() && opt.get() instanceof SimpleParticleType spt) ? spt : def;
    }
}
