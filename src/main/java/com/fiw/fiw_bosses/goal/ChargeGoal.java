package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChargeGoal extends Goal {

    private final BossEntity boss;
    private final float speed;
    private final float damage;
    private final float distance;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private Vec3d chargeDir;
    private int chargeTick;
    private int maxChargeTicks;
    private final Set<UUID> alreadyHit = new HashSet<>();
    private static final int WINDUP_TICKS = 10;

    public ChargeGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.speed = params.has("speed") ? params.get("speed").getAsFloat() : 1.5f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 15.0f;
        this.distance = params.has("distance") ? params.get("distance").getAsFloat() : 10.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive()
                && boss.distanceTo(target) >= 5.0f
                && boss.distanceTo(target) <= distance + 5;
    }

    @Override
    public void start() {
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        chargeDir = target.getPos().subtract(boss.getPos()).normalize();
        chargeTick = -WINDUP_TICKS; // negative = windup phase
        maxChargeTicks = (int) (distance / (speed * 0.5f));
        maxChargeTicks = Math.max(5, Math.min(30, maxChargeTicks));
        alreadyHit.clear();

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            // Windup sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.5f, 0.8f);

            // Taunt
            if (taunt != null) {
                Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                        .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                        .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                        .append(TextUtil.parseColorCodes(taunt));
                for (var player : world.getPlayers()) {
                    if (player.squaredDistanceTo(boss) <= 48 * 48) {
                        player.sendMessage(tauntText, false);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return chargeTick < maxChargeTicks;
    }

    @Override
    public void tick() {
        chargeTick++;

        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();

            if (chargeTick <= 0) {
                // Windup phase — boss stands still, particles gather
                boss.setVelocity(0, boss.getVelocity().y, 0);
                boss.velocityModified = true;

                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                        boss.getX(), boss.getY() + 2.0, boss.getZ(),
                        2, 0.3, 0.3, 0.3, 0.0);
                world.spawnParticles(ParticleTypes.CLOUD,
                        boss.getX() + chargeDir.x * 0.5, boss.getY() + 0.1, boss.getZ() + chargeDir.z * 0.5,
                        3, 0.2, 0.0, 0.2, 0.01);
                return;
            }

            // Charge phase — move fast
            boss.setVelocity(chargeDir.x * speed * 0.5, boss.getVelocity().y, chargeDir.z * speed * 0.5);
            boss.velocityModified = true;

            // Hit entities in path (only once per entity)
            Box hitbox = boss.getBoundingBox().expand(0.8);
            List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class, hitbox,
                    e -> e != boss && e.isAlive() && !(e instanceof BossEntity)
                            && !boss.isMinion(e) && !alreadyHit.contains(e.getUuid()));

            for (LivingEntity entity : hit) {
                entity.damage(boss.getDamageSources().mobAttack(boss), damage);
                Vec3d knockDir = entity.getPos().subtract(boss.getPos()).normalize();
                entity.addVelocity(knockDir.x * 1.5, 0.6, knockDir.z * 1.5);
                entity.velocityModified = true;
                alreadyHit.add(entity.getUuid());

                // Impact sound + particles
                world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0f, 0.8f);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(),
                        5, 0.3, 0.3, 0.3, 0.1);
            }

            // Trail particles
            world.spawnParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.3, boss.getZ(),
                    3, 0.2, 0.1, 0.2, 0.02);
            world.spawnParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + 0.05, boss.getZ(),
                    2, 0.3, 0.0, 0.3, 0.01);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        boss.setVelocity(0, boss.getVelocity().y, 0);
        boss.velocityModified = true;

        // Impact stomp at end of charge
        if (!boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
    }
}
