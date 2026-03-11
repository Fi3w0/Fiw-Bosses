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
import java.util.List;

public class MeleeSlashAttackGoal extends Goal {

    private final BossEntity boss;
    private final float range;
    private final float arc;
    private final float damage;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;
    private int attackTick;

    public MeleeSlashAttackGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.range = params.has("range") ? params.get("range").getAsFloat() : 4.0f;
        this.arc = params.has("arc") ? params.get("arc").getAsFloat() : 90.0f;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 10.0f;
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
                && boss.squaredDistanceTo(target) <= range * range;
    }

    @Override
    public void start() {
        attackTick = 0;
        // Look at target during windup
        LivingEntity target = boss.getTarget();
        if (target != null) {
            boss.getLookControl().lookAt(target, 360, 90);
        }
    }

    @Override
    public boolean shouldContinue() {
        return attackTick < 12;
    }

    @Override
    public void tick() {
        attackTick++;

        // Windup particles (ticks 1-4)
        if (attackTick <= 4 && !boss.getWorld().isClient) {
            ServerWorld world = (ServerWorld) boss.getWorld();
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    boss.getX(), boss.getY() + 1.2, boss.getZ(),
                    2, 0.3, 0.2, 0.3, 0.1);
        }

        // Slash on tick 5
        if (attackTick == 5) {
            performSlash();
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }

    private void performSlash() {
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        Vec3d bossPos = boss.getPos();
        Vec3d lookDir = boss.getRotationVec(1.0f).normalize();
        float halfArc = arc / 2.0f;

        // Sound — heavy sword sweep
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.7f);

        Box searchBox = boss.getBoundingBox().expand(range);
        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e != boss && e.isAlive() && !(e instanceof BossEntity) && !boss.isMinion(e));

        int hitCount = 0;
        for (LivingEntity entity : entities) {
            Vec3d toEntity = entity.getPos().subtract(bossPos).normalize();
            double dot = lookDir.dotProduct(toEntity);
            double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

            if (angle <= halfArc && boss.squaredDistanceTo(entity) <= range * range) {
                entity.damage(boss.getDamageSources().mobAttack(boss), damage);
                Vec3d knockback = toEntity.multiply(0.7);
                entity.addVelocity(knockback.x, 0.25, knockback.z);
                entity.velocityModified = true;
                hitCount++;

                // Blood/hit particles on the entity
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(),
                        3, 0.2, 0.2, 0.2, 0.1);
            }
        }

        // Sweep particles in an arc
        for (int i = 0; i < 15; i++) {
            double sweepAngle = Math.toRadians(boss.getYaw() - halfArc + (arc / 15.0) * i);
            double dist = range * (0.5 + (i % 3) * 0.15);
            double px = bossPos.x + Math.sin(-sweepAngle) * dist;
            double pz = bossPos.z + Math.cos(sweepAngle) * dist;
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, bossPos.y + 1.0, pz, 1, 0, 0, 0, 0);
        }
        // Crit sparkle
        world.spawnParticles(ParticleTypes.CRIT,
                bossPos.x + lookDir.x * range * 0.5, bossPos.y + 1.0, bossPos.z + lookDir.z * range * 0.5,
                8, range * 0.3, 0.3, range * 0.3, 0.2);

        // Boss taunt on hit
        if (hitCount > 0 && taunt != null && boss.getRandom().nextFloat() < 0.3f) {
            sendBossTaunt(world, taunt);
        }
    }

    private void sendBossTaunt(ServerWorld world, String message) {
        Text tauntText = Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(message));
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(boss) <= 48 * 48) {
                player.sendMessage(tauntText, false);
            }
        }
    }
}
