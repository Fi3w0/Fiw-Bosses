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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChainLightningGoal extends Goal {

    private final BossEntity boss;
    private final int bounces;
    private final float damage;
    private final float radius;
    private final int cooldown;
    private final String taunt;
    private int cooldownTimer;

    public ChainLightningGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.bounces = params.has("bounces") ? params.get("bounces").getAsInt() : 3;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 8.0f;
        this.radius = params.has("radius") ? params.get("radius").getAsFloat() : 12.0f;
        this.cooldown = cooldownTicks;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldownTimer = 0;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        performChainLightning();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private void performChainLightning() {
        if (boss.level().isClientSide) return;

        ServerLevel level = (ServerLevel) boss.level();
        LivingEntity primaryTarget = boss.getTarget();
        if (primaryTarget == null) return;

        AABB area = AABB.ofSize(boss.position(), radius * 2, radius * 2, radius * 2);
        List<Player> nearby = level.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && p.distanceToSqr(boss) <= radius * radius);

        if (nearby.isEmpty()) return;

        nearby.sort((a, b) -> Double.compare(a.distanceToSqr(boss), b.distanceToSqr(boss)));
        if (primaryTarget instanceof Player pt && nearby.contains(pt)) {
            nearby.remove(pt);
            nearby.add(0, pt);
        }

        List<LivingEntity> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        chain.add(nearby.get(0));
        visited.add(nearby.get(0).getUUID());

        for (int i = 0; i < bounces - 1 && chain.size() < nearby.size(); i++) {
            LivingEntity last = chain.get(chain.size() - 1);
            Player next = null;
            double best = Double.MAX_VALUE;
            for (Player p : nearby) {
                if (visited.contains(p.getUUID())) continue;
                double d = p.distanceToSqr(last);
                if (d < best) { best = d; next = p; }
            }
            if (next != null) {
                chain.add(next);
                visited.add(next.getUUID());
            }
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 1.4f);

        LivingEntity prev = boss;
        for (LivingEntity entity : chain) {
            Vec3 from = new Vec3(prev.getX(), prev.getY() + prev.getBbHeight() * 0.8, prev.getZ());
            Vec3 to = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ());

            int steps = (int) (from.distanceTo(to) * 4);
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                double jx = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                double jy = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                double jz = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        from.x + (to.x - from.x) * t + jx,
                        from.y + (to.y - from.y) * t + jy,
                        from.z + (to.z - from.z) * t + jz,
                        1, 0, 0, 0, 0);
            }

            entity.hurt(boss.damageSources().lightningBolt(), damage);

            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.setVisualOnly(true);
            bolt.setPos(entity.getX(), entity.getY(), entity.getZ());
            level.addFreshEntity(bolt);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                    15, 0.4, 0.4, 0.4, 0.12);

            prev = entity;
        }

        if (taunt != null && boss.getRandom().nextFloat() < 0.4f) {
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
