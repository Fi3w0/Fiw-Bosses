package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.AbilityEntry;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BossPhaseManager {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("b3f40c00-cafe-4b49-9a6a-e1f1c5d1e7a1");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("b3f40c00-cafe-4b49-9a6a-e1f1c5d1e7a2");

    private final BossEntity boss;
    private final List<PhaseDefinition> phases;
    private int currentPhaseIndex = -1;

    public BossPhaseManager(BossEntity boss, List<PhaseDefinition> phases) {
        this.boss = boss;
        this.phases = phases != null ? phases : new ArrayList<>();
    }

    public void tick() {
        if (phases.isEmpty()) return;
        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        int targetIndex = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (hpPercent <= phases.get(i).hpThresholdPercent) {
                targetIndex = i;
            }
        }

        if (targetIndex != currentPhaseIndex) {
            transitionToPhase(targetIndex);
        }
    }

    public void transitionToPhase(int newIndex) {
        if (newIndex < 0 || newIndex >= phases.size()) return;

        PhaseDefinition phase = phases.get(newIndex);
        boolean isInitial = currentPhaseIndex == -1;
        currentPhaseIndex = newIndex;

        if (!isInitial && !boss.getWorld().isClient) {
            playTransitionEffects(phase);
        }

        rebuildGoals(phase);
        applyStatModifiers(phase);

        if (phase.equipment != null) {
            boss.applyEquipment(phase.equipment);
        }

        FiwBosses.LOGGER.debug("Boss {} transitioned to phase {}", boss.getBossId(), newIndex);
    }

    private void rebuildGoals(PhaseDefinition phase) {
        // Clear all existing goals
        var goals = boss.getGoalSelector().getGoals().stream()
                .map(PrioritizedGoal::getGoal)
                .collect(Collectors.toList());
        goals.forEach(boss.getGoalSelector()::remove);

        var targets = boss.getTargetSelector().getGoals().stream()
                .map(PrioritizedGoal::getGoal)
                .collect(Collectors.toList());
        targets.forEach(boss.getTargetSelector()::remove);

        // === BASE MOVEMENT ===
        boss.getGoalSelector().add(0, new SwimGoal(boss));
        // Ability goals (priority 1-5) run BEFORE MeleeAttackGoal (priority 6)
        // so a casting boss won't have its animation interrupted by basic melee.
        boss.getGoalSelector().add(6, new MeleeAttackGoal(boss, 1.2, false));
        boss.getGoalSelector().add(8, new WanderAroundFarGoal(boss, 1.0));
        boss.getGoalSelector().add(9, new LookAtEntityGoal(boss, PlayerEntity.class, 48.0f));
        boss.getGoalSelector().add(10, new LookAroundGoal(boss));

        // === TARGETING ===
        boss.getTargetSelector().add(1, new RevengeGoal(boss));
        boss.getTargetSelector().add(2, new ActiveTargetGoal<>(boss, PlayerEntity.class, true));

        // === ABILITY GOALS (priority 1-5, higher than melee so abilities are never interrupted) ===
        int priority = 1;
        for (AbilityEntry ability : phase.abilities) {
            try {
                Goal goal = BossGoalFactory.create(ability.type, boss, ability.cooldownTicks, ability.params);
                boss.getGoalSelector().add(priority, goal);
                priority++;
                if (priority > 5) priority = 5;
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to create ability '{}' for boss '{}': {}",
                        ability.type, boss.getBossId(), e.getMessage());
            }
        }
    }

    private void applyStatModifiers(PhaseDefinition phase) {
        var speedAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        var damageAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            if (phase.speedMultiplier != 1.0f) {
                speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                        SPEED_MODIFIER_UUID, "boss_phase_speed",
                        phase.speedMultiplier - 1.0, EntityAttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
            if (phase.damageMultiplier != 1.0f) {
                damageAttr.addTemporaryModifier(new EntityAttributeModifier(
                        DAMAGE_MODIFIER_UUID, "boss_phase_damage",
                        phase.damageMultiplier - 1.0, EntityAttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
    }

    private void playTransitionEffects(PhaseDefinition phase) {
        ServerWorld world = (ServerWorld) boss.getWorld();

        if (phase.transitionMessage != null && !phase.transitionMessage.isEmpty()) {
            Text message = TextUtil.parseColorCodes(phase.transitionMessage);
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 64 * 64) {
                    player.sendMessage(message, false);
                }
            }
        }

        if (phase.transitionSound != null && !phase.transitionSound.isEmpty()) {
            Identifier soundId = Identifier.tryParse(phase.transitionSound);
            if (soundId != null) {
                SoundEvent sound = Registries.SOUND_EVENT.get(soundId);
                if (sound != null) {
                    world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            sound, SoundCategory.HOSTILE, 2.0f, 1.0f);
                }
            }
        }

        if (phase.transitionParticle != null && !phase.transitionParticle.isEmpty()) {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    3, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /** Re-applies current phase goals and stats without playing transition effects. */
    public void restoreCurrentPhase() {
        if (currentPhaseIndex < 0 || currentPhaseIndex >= phases.size()) return;
        PhaseDefinition phase = phases.get(currentPhaseIndex);
        rebuildGoals(phase);
        applyStatModifiers(phase);
    }

    public int getCurrentPhaseIndex() { return currentPhaseIndex; }

    public PhaseDefinition getCurrentPhase() {
        if (currentPhaseIndex >= 0 && currentPhaseIndex < phases.size()) {
            return phases.get(currentPhaseIndex);
        }
        return null;
    }
}
