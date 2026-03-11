package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
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
    public boolean shouldContinue() {
        return false;
    }

    private void performChainLightning() {
        if (boss.getWorld().isClient) return;

        ServerWorld world = (ServerWorld) boss.getWorld();
        LivingEntity primaryTarget = boss.getTarget();
        if (primaryTarget == null) return;

        // Gather nearby players
        Box area = Box.of(boss.getPos(), radius * 2, radius * 2, radius * 2);
        List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class, area,
                p -> p.isAlive() && p.squaredDistanceTo(boss) <= radius * radius);

        if (nearby.isEmpty()) return;

        // Primary target first, then sort remainder by distance
        nearby.sort((a, b) -> Double.compare(a.squaredDistanceTo(boss), b.squaredDistanceTo(boss)));
        if (primaryTarget instanceof PlayerEntity pt && nearby.contains(pt)) {
            nearby.remove(pt);
            nearby.add(0, pt);
        }

        // Build chain — each link picks the nearest unvisited player to the previous link
        List<LivingEntity> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        chain.add(nearby.get(0));
        visited.add(nearby.get(0).getUuid());

        for (int i = 0; i < bounces - 1 && chain.size() < nearby.size(); i++) {
            LivingEntity last = chain.get(chain.size() - 1);
            PlayerEntity next = null;
            double best = Double.MAX_VALUE;
            for (PlayerEntity p : nearby) {
                if (visited.contains(p.getUuid())) continue;
                double d = p.squaredDistanceTo(last);
                if (d < best) { best = d; next = p; }
            }
            if (next != null) {
                chain.add(next);
                visited.add(next.getUuid());
            }
        }

        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 1.2f, 1.4f);

        // Strike each target and draw a particle arc from the previous link
        LivingEntity prev = boss;
        for (LivingEntity entity : chain) {
            Vec3d from = new Vec3d(prev.getX(), prev.getY() + prev.getHeight() * 0.8, prev.getZ());
            Vec3d to = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.8, entity.getZ());

            // Jagged particle arc between links
            int steps = (int) (from.distanceTo(to) * 4);
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                double jx = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                double jy = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                double jz = (boss.getRandom().nextDouble() - 0.5) * 0.5;
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        from.x + (to.x - from.x) * t + jx,
                        from.y + (to.y - from.y) * t + jy,
                        from.z + (to.z - from.z) * t + jz,
                        1, 0, 0, 0, 0);
            }

            // Damage
            entity.damage(boss.getDamageSources().lightningBolt(), damage);

            // Cosmetic lightning bolt (no fire, no mob conversion)
            LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
            bolt.setCosmetic(true);
            bolt.setPosition(entity.getX(), entity.getY(), entity.getZ());
            world.spawnEntity(bolt);

            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
                    15, 0.4, 0.4, 0.4, 0.12);

            prev = entity;
        }

        if (taunt != null && boss.getRandom().nextFloat() < 0.4f) {
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
