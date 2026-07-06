package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.config.AbilityEntry;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BossPhaseManager {

    private static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "phase_speed");
    private static final ResourceLocation DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "phase_damage");

    private final BossEntity boss;
    private final List<PhaseDefinition> phases;
    private int currentPhaseIndex = -1;

    public BossPhaseManager(BossEntity boss, List<PhaseDefinition> phases) {
        this.boss = boss;
        this.phases = phases != null ? phases : new ArrayList<>();
    }

    public void tick() {
        if (phases.isEmpty()) return;
        if (!boss.isActive() || !boss.isAlive()) return;

        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        int targetIndex = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (hpPercent <= phases.get(i).hpThresholdPercent) {
                targetIndex = i;
            }
        }

        if (targetIndex > currentPhaseIndex || currentPhaseIndex < 0) {
            transitionToPhase(targetIndex);
        }
    }

    /** Silently restores a phase on world reload — no transition messages or effects. */
    public void restoreToPhase(int newIndex) {
        if (newIndex < 0 || newIndex >= phases.size()) return;
        currentPhaseIndex = newIndex;
        rebuildGoals(phases.get(newIndex));
        applyStatModifiers(phases.get(newIndex));
        if (phases.get(newIndex).equipment != null) {
            boss.applyEquipment(phases.get(newIndex).equipment);
        }
    }

    public void transitionToPhase(int newIndex) {
        if (newIndex < 0 || newIndex >= phases.size()) return;

        PhaseDefinition phase = phases.get(newIndex);
        boolean isInitial = currentPhaseIndex == -1;
        currentPhaseIndex = newIndex;

        if (!isInitial && !boss.level().isClientSide) {
            playTransitionEffects(phase);
        }

        rebuildGoals(phase);
        applyStatModifiers(phase);

        if (phase.equipment != null) {
            boss.applyEquipment(phase.equipment);
        }

        FiwBossesCore.LOGGER.debug("Boss {} transitioned to phase {}", boss.getBossId(), newIndex);
    }

    private void rebuildGoals(PhaseDefinition phase) {
        var goals = boss.getGoalSelector().getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .collect(Collectors.toList());
        goals.forEach(boss.getGoalSelector()::removeGoal);

        var targets = boss.getTargetSelector().getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .collect(Collectors.toList());
        targets.forEach(boss.getTargetSelector()::removeGoal);

        // === BASE MOVEMENT ===
        if (boss.shouldFloat()) boss.getGoalSelector().addGoal(0, new FloatGoal(boss));
        // Ability goals (priority 1-5) run BEFORE MeleeAttackGoal (priority 6)
        // so a casting boss won't have its animation interrupted by basic melee.
        boss.getGoalSelector().addGoal(6, new MeleeAttackGoal(boss, 1.2, false));
        boss.getGoalSelector().addGoal(8, new WaterAvoidingRandomStrollGoal(boss, 1.0));
        boss.getGoalSelector().addGoal(9, new LookAtPlayerGoal(boss, Player.class, 48.0f));
        boss.getGoalSelector().addGoal(10, new RandomLookAroundGoal(boss));

        // === TARGETING ===
        boss.getTargetSelector().addGoal(1, new AllyAwareHurtByTargetGoal(boss));
        boss.getTargetSelector().addGoal(2, new NearestAttackableTargetGoal<>(boss, Player.class, true));

        // === ABILITY GOALS ===
        int priority = 1;
        for (AbilityEntry ability : phase.abilities) {
            try {
                Goal goal = BossGoalFactory.create(ability.type, boss, ability.cooldownTicks, ability.params);
                boss.getGoalSelector().addGoal(priority, goal);
                priority++;
                if (priority > 5) priority = 5;
            } catch (Exception e) {
                FiwBossesCore.LOGGER.error("Failed to create ability '{}' for boss '{}': {}",
                        ability.type, boss.getBossId(), e.getMessage());
            }
        }
    }

    /** HurtByTargetGoal that refuses to retaliate against allies unless configured to. */
    private static class AllyAwareHurtByTargetGoal extends HurtByTargetGoal {
        private final BossEntity boss;

        AllyAwareHurtByTargetGoal(BossEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            if (!super.canUse()) return false;
            LivingEntity attacker = boss.getLastHurtByMob();
            return attacker == null || boss.canTargetFactionAllies() || !boss.isAlly(attacker);
        }
    }

    private void applyStatModifiers(PhaseDefinition phase) {
        var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        var damageAttr = boss.getAttribute(Attributes.ATTACK_DAMAGE);

        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);
            if (phase.speedMultiplier != 1.0f) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        SPEED_MODIFIER_ID,
                        phase.speedMultiplier - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }

        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
            if (phase.damageMultiplier != 1.0f) {
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_ID,
                        phase.damageMultiplier - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private void playTransitionEffects(PhaseDefinition phase) {
        ServerLevel level = (ServerLevel) boss.level();

        if (phase.transitionMessage != null && !phase.transitionMessage.isEmpty()) {
            Component message = TextUtil.parseColorCodes(phase.transitionMessage);
            for (var player : level.players()) {
                if (player.distanceToSqr(boss) <= 64 * 64) {
                    player.sendSystemMessage(message);
                }
            }
        }

        if (phase.transitionSound != null && !phase.transitionSound.isEmpty()) {
            ResourceLocation soundId = ResourceLocation.tryParse(phase.transitionSound);
            if (soundId != null) {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
                if (sound != null) {
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            sound, SoundSource.HOSTILE, 2.0f, 1.0f);
                }
            }
        }

        if (phase.transitionParticle != null && !phase.transitionParticle.isEmpty()) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
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
