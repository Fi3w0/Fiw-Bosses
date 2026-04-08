package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.config.MinionEntry;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null && hasMinionsConfigured();
    }

    @Override
    public void start() {
        summonMinions();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private boolean hasMinionsConfigured() {
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        return phase != null && phase.minions != null && !phase.minions.isEmpty();
    }

    private void summonMinions() {
        if (boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        if (phase == null || phase.minions == null) return;

        trackedMinions.removeIf(uuid -> {
            Entity e = level.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        boolean didSummon = false;

        for (MinionEntry minionDef : phase.minions) {
            long aliveCount = trackedMinions.stream()
                    .map(level::getEntity)
                    .filter(Objects::nonNull)
                    .filter(Entity::isAlive)
                    .count();

            if (aliveCount >= minionDef.maxAlive) continue;

            ResourceLocation typeId = ResourceLocation.tryParse(minionDef.entityType);
            if (typeId == null) continue;

            Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
            if (entityTypeOpt.isEmpty()) continue;

            int toSpawn = Math.min(minionDef.count, minionDef.maxAlive - (int) aliveCount);

            for (int i = 0; i < toSpawn; i++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 1.5 + boss.getRandom().nextDouble() * minionDef.spawnRadius;
                double x = boss.getX() + Math.cos(angle) * dist;
                double z = boss.getZ() + Math.sin(angle) * dist;
                double y = boss.getY();

                Entity entity = entityTypeOpt.get().create(level);
                if (entity != null) {
                    entity.moveTo(x, y, z, boss.getRandom().nextFloat() * 360, 0);
                    if (entity instanceof Mob mob) {
                        mob.finalizeSpawn(level,
                                level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                                MobSpawnType.MOB_SUMMONED, null);
                        mob.setPersistenceRequired();
                        if (boss.getTarget() != null) {
                            mob.setTarget(boss.getTarget());
                        }
                    }
                    level.addFreshEntity(entity);
                    trackedMinions.add(entity.getUUID());
                    boss.registerMinion(entity.getUUID());
                    didSummon = true;

                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, y + 0.5, z, 8, 0.3, 0.5, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            x, y + 0.3, z, 5, 0.2, 0.4, 0.2, 0.02);
                }
            }
        }

        if (didSummon) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0f, 0.8f);

            String msg = tauntMessage != null ? tauntMessage : "&5Rise, my servants!";
            var bossName = boss.getCustomName();
            Component taunt = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(msg));
            for (var player : level.players()) {
                if (player.distanceToSqr(boss) <= 48 * 48) {
                    player.sendSystemMessage(taunt);
                }
            }
        }
    }
}
