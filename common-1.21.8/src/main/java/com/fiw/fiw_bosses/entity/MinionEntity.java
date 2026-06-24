package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.config.*;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.UUID;

/**
 * A custom minion entity — uses the BossEntity infrastructure (player model, skin,
 * abilities, equipment) but has no boss bar, no phases, no dialogue.
 * Extends BossEntity so all 42 ability goals work unchanged.
 */
public class MinionEntity extends BossEntity {

    private String minionId;
    private MinionDefinition minionDef;
    private UUID ownerBossUuid;
    private String movementMode = "normal";

    public MinionEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 10;
    }

    // ── No boss bar ──────────────────────────────────────────────────────────

    @Override
    protected boolean showBossBar() { return false; }

    // ── Setup ────────────────────────────────────────────────────────────────

    public void applyMinionDefinition(MinionDefinition def, BossEntity owner) {
        this.minionDef = def;
        this.minionId  = def.id;
        this.movementMode = def.movement != null ? def.movement : "normal";
        if (owner != null) {
            this.ownerBossUuid = owner.getUUID();
        }

        // Build a synthetic BossDefinition so the parent's applyDefinition()
        // sets up stats, equipment, name, and abilities via the phase manager.
        BossDefinition syntheticDef = new BossDefinition();
        syntheticDef.id          = "minion_" + def.id;
        syntheticDef.displayName = def.displayName != null ? def.displayName : def.id;
        syntheticDef.health      = def.health;
        syntheticDef.armor       = def.armor;
        syntheticDef.speed       = def.speed;
        syntheticDef.knockbackResistance = def.knockbackResistance;
        syntheticDef.attackDamage = def.attackDamage;
        syntheticDef.equipment   = def.equipment;
        syntheticDef.skin        = def.skin;

        // Single phase with all the minion's abilities
        PhaseDefinition phase = new PhaseDefinition();
        phase.hpThresholdPercent = 1.0f;
        if (def.abilities != null) {
            phase.abilities.addAll(def.abilities);
        }
        syntheticDef.phases.add(phase);

        // No dialogues, no idle system
        syntheticDef.preFightDialogue.clear();
        syntheticDef.preDeathDialogue.clear();
        syntheticDef.idleTimeout = -1;

        applyDefinition(syntheticDef);
        setupMovementGoals();
    }

    private void setupMovementGoals() {
        scheduleGoalAction(() -> {
            switch (movementMode) {
                case "static" -> {
                    // Remove wander goals — minion stays in place, only uses abilities
                    goalSelector.getAvailableGoals().stream()
                            .filter(wg -> wg.getGoal() instanceof WaterAvoidingRandomStrollGoal)
                            .map(WrappedGoal::getGoal)
                            .toList()
                            .forEach(goalSelector::removeGoal);
                }
                case "follow_boss" -> {
                    // Add a follow-owner goal (higher priority than wander)
                    if (ownerBossUuid != null) {
                        goalSelector.addGoal(3, new MinionFollowOwnerGoal(this));
                    }
                }
                // "normal" — default chase-target AI from BossPhaseManager, no changes needed
            }
        });
    }

    // ── Death / loot ─────────────────────────────────────────────────────────

    @Override
    public void die(DamageSource damageSource) {
        // Drop minion-specific loot instead of boss loot
        if (!level().isClientSide() && minionDef != null && minionDef.loot != null) {
            BossLootHandler.dropLootEntries(this, minionDef.loot);
        }
        // Skip BossEntity.die() boss loot — call Monster.die() directly
        // We need to remove bossbar and call LivingEntity death logic
        superDie(damageSource);
    }

    private void superDie(DamageSource damageSource) {
        // Call the grandparent (Monster) die, skipping BossEntity's boss-loot drop
        super.die(damageSource);
    }

    // ── Damage: immune to owner boss ─────────────────────────────────────────

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        // Immune to the owner boss
        if (attacker != null && ownerBossUuid != null && attacker.getUUID().equals(ownerBossUuid)) {
            return false;
        }
        // Immune to sibling minions of the same boss
        if (attacker instanceof MinionEntity other && ownerBossUuid != null
                && ownerBossUuid.equals(other.ownerBossUuid)) {
            return false;
        }
        return super.hurtServer(world, source, amount);
    }

    // ── Never auto-despawn, but despawn when owner dies ──────────────────────

    @Override
    public void checkDespawn() { /* minions don't auto-despawn */ }

    // ── NBT persistence ──────────────────────────────────────────────────────

    @Override
    protected void writeBossCustomData(ValueOutput output) {
        // Replaces BossEntity's boss-field persistence: minions store their own data,
        // so BossId/phase/state are never written.
        if (minionId != null) output.putString("MinionId", minionId);
        if (ownerBossUuid != null) output.store("OwnerBoss", UUIDUtil.CODEC, ownerBossUuid);
        output.putString("MovementMode", movementMode);
    }

    @Override
    protected void readBossCustomData(ValueInput input) {
        Optional<String> minionIdOpt = input.getString("MinionId");
        if (minionIdOpt.isEmpty()) {
            this.discard();
            return;
        }

        this.minionId = minionIdOpt.get();
        input.read("OwnerBoss", UUIDUtil.CODEC).ifPresent(u -> this.ownerBossUuid = u);
        this.movementMode = input.getStringOr("MovementMode", "normal");

        MinionDefinition def = MinionConfigLoader.getDefinition(minionId);
        if (def == null) {
            FiwBossesCore.LOGGER.warn("Minion definition '{}' not found, entity will be removed", minionId);
            this.discard();
            return;
        }

        float savedHealth = getHealth();
        applyMinionDefinition(def, null);
        if (savedHealth > 0 && savedHealth <= getMaxHealth()) {
            setHealth(savedHealth);
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getMinionId() { return minionId; }
    public MinionDefinition getMinionDefinition() { return minionDef; }
    public UUID getOwnerBossUuid() { return ownerBossUuid; }
    public String getMovementMode() { return movementMode; }

    @Override
    public String getBossId() {
        // Used by skin system to look up the skin
        return minionId != null ? "minion_" + minionId : null;
    }

    // ── Follow-owner AI goal ─────────────────────────────────────────────────

    private static class MinionFollowOwnerGoal extends Goal {
        private final MinionEntity minion;
        private LivingEntity owner;
        private int timeToRecalcPath;

        MinionFollowOwnerGoal(MinionEntity minion) {
            this.minion = minion;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (minion.ownerBossUuid == null) return false;
            if (minion.getTarget() != null && minion.getTarget().isAlive()) return false;

            Entity e = minion.level() instanceof net.minecraft.server.level.ServerLevel sl
                    ? sl.getEntity(minion.ownerBossUuid) : null;
            if (e instanceof LivingEntity le && le.isAlive()) {
                this.owner = le;
                return minion.distanceToSqr(owner) > 9.0; // > 3 blocks away
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (owner == null || !owner.isAlive()) return false;
            return minion.distanceToSqr(owner) > 4.0; // > 2 blocks
        }

        @Override
        public void start() { timeToRecalcPath = 0; }

        @Override
        public void tick() {
            if (--timeToRecalcPath <= 0) {
                timeToRecalcPath = 10;
                minion.getNavigation().moveTo(owner, 1.0);
            }
        }

        @Override
        public void stop() {
            minion.getNavigation().stop();
            owner = null;
        }
    }
}
