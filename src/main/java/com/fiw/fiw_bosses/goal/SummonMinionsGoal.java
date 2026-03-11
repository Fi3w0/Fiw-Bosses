package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.config.MinionEntry;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class SummonMinionsGoal extends Goal {

    private final BossEntity boss;
    private final int cooldown;
    private int cooldownTimer;
    private final List<UUID> trackedMinions = new ArrayList<>();
    private final String tauntMessage;

    public SummonMinionsGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.cooldown = cooldownTicks;
        this.cooldownTimer = cooldownTicks / 2;
        this.tauntMessage = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null && hasMinionsConfigured();
    }

    @Override
    public void start() {
        summonMinions();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    private boolean hasMinionsConfigured() {
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        return phase != null && phase.minions != null && !phase.minions.isEmpty();
    }

    private void summonMinions() {
        if (boss.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) boss.getWorld();
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        if (phase == null || phase.minions == null) return;

        // Clean up dead minions
        trackedMinions.removeIf(uuid -> {
            Entity e = world.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        boolean didSummon = false;

        for (MinionEntry minionDef : phase.minions) {
            long aliveCount = trackedMinions.stream()
                    .map(world::getEntity)
                    .filter(Objects::nonNull)
                    .filter(Entity::isAlive)
                    .count();

            if (aliveCount >= minionDef.maxAlive) continue;

            Identifier typeId = Identifier.tryParse(minionDef.entityType);
            if (typeId == null) continue;

            Optional<EntityType<?>> entityTypeOpt = Registries.ENTITY_TYPE.getOrEmpty(typeId);
            if (entityTypeOpt.isEmpty()) continue;

            int toSpawn = Math.min(minionDef.count, minionDef.maxAlive - (int) aliveCount);

            for (int i = 0; i < toSpawn; i++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 1.5 + boss.getRandom().nextDouble() * minionDef.spawnRadius;
                double x = boss.getX() + Math.cos(angle) * dist;
                double z = boss.getZ() + Math.sin(angle) * dist;
                double y = boss.getY();

                Entity entity = entityTypeOpt.get().create(world);
                if (entity != null) {
                    entity.refreshPositionAndAngles(x, y, z, boss.getRandom().nextFloat() * 360, 0);
                    if (entity instanceof MobEntity mob) {
                        mob.initialize(world, world.getLocalDifficulty(new BlockPos((int) x, (int) y, (int) z)),
                                SpawnReason.MOB_SUMMONED, null, null);
                        mob.setPersistent();
                        // Set the minion to target the boss's target (a player)
                        if (boss.getTarget() != null) {
                            mob.setTarget(boss.getTarget());
                        }
                    }
                    world.spawnEntity(entity);
                    trackedMinions.add(entity.getUuid());
                    // Register minion with boss so boss won't be targeted by it
                    boss.registerMinion(entity.getUuid());
                    didSummon = true;

                    // Summon particles — dark smoke + soul fire
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, y + 0.5, z, 8, 0.3, 0.5, 0.3, 0.05);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            x, y + 0.3, z, 5, 0.2, 0.4, 0.2, 0.02);
                }
            }
        }

        if (didSummon) {
            // Sound
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 0.8f);

            // Taunt message to nearby players
            String msg = tauntMessage != null ? tauntMessage : "&5Rise, my servants!";
            Text taunt = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(boss.getCustomName() != null ? boss.getCustomName().copy() : Text.literal("Boss"))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(msg));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48) {
                    player.sendMessage(taunt, false);
                }
            }
        }
    }
}
