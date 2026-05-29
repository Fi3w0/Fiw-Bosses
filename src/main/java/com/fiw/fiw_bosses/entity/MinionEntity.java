package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.*;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A custom minion entity — uses the BossEntity infrastructure (player model, skin,
 * abilities, equipment) but has no boss bar, no phases, no dialogue.
 * Extends BossEntity so all ability goals work unchanged.
 */
public class MinionEntity extends BossEntity {

    private String minionId;
    private MinionDefinition minionDef;
    private UUID ownerBossUuid;
    private String movementMode = "normal";

    public MinionEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 10;
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
            this.ownerBossUuid = owner.getUuid();
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
                    getGoalSelector().getGoals().stream()
                            .filter(wg -> wg.getGoal() instanceof WanderAroundFarGoal)
                            .map(PrioritizedGoal::getGoal)
                            .toList()
                            .forEach(getGoalSelector()::remove);
                }
                case "follow_boss" -> {
                    // Add a follow-owner goal (higher priority than wander)
                    if (ownerBossUuid != null) {
                        getGoalSelector().add(3, new MinionFollowOwnerGoal(this));
                    }
                }
                // "normal" — default chase-target AI from BossPhaseManager, no changes needed
            }
        });
    }

    // ── Death / loot ─────────────────────────────────────────────────────────

    @Override
    public void onDeath(DamageSource damageSource) {
        // Drop minion-specific loot instead of boss loot
        if (!getEntityWorld().isClient() && minionDef != null && minionDef.loot != null) {
            BossLootHandler.dropLootEntries(this, minionDef.loot);
        }
        // Call BossEntity.onDeath() — the synthetic def has empty loot, so
        // BossLootHandler.dropLoot() is a no-op. Boss bar clear is harmless.
        super.onDeath(damageSource);
    }

    // ── Damage: immune to owner boss ─────────────────────────────────────────

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        Entity attacker = source.getAttacker();
        // Immune to the owner boss
        if (attacker != null && ownerBossUuid != null && attacker.getUuid().equals(ownerBossUuid)) {
            return false;
        }
        // Immune to sibling minions of the same boss
        if (attacker instanceof MinionEntity other && ownerBossUuid != null
                && ownerBossUuid.equals(other.ownerBossUuid)) {
            return false;
        }
        return super.damage(world, source, amount);
    }

    // ── Never auto-despawn, but despawn when owner dies ──────────────────────

    @Override
    public void checkDespawn() { /* minions don't auto-despawn */ }

    // ── NBT persistence ──────────────────────────────────────────────────────

    /**
     * Override the BossEntity hook so MinionEntity writes minion-specific tags
     * INSTEAD OF the boss tags. Vanilla HostileEntity data still goes out via
     * the {@link BossEntity#writeCustomData} super-call chain.
     */
    @Override
    protected void writeBossCustomData(WriteView view) {
        if (minionId != null) view.putString("MinionId", minionId);
        if (ownerBossUuid != null) view.put("OwnerBoss", Uuids.CODEC, ownerBossUuid);
        view.putString("MovementMode", movementMode);
    }

    @Override
    protected void readBossCustomData(ReadView view) {
        var minionIdOpt = view.getOptionalString("MinionId");
        if (minionIdOpt.isEmpty()) {
            this.discard();
            return;
        }

        this.minionId = minionIdOpt.get();
        view.read("OwnerBoss", Uuids.CODEC).ifPresent(uuid -> this.ownerBossUuid = uuid);
        view.getOptionalString("MovementMode").ifPresent(m -> this.movementMode = m);

        MinionDefinition def = MinionConfigLoader.getDefinition(minionId);
        if (def == null) {
            FiwBosses.LOGGER.warn("Minion definition '{}' not found, entity will be removed", minionId);
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
            this.setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (minion.ownerBossUuid == null) return false;
            if (minion.getTarget() != null && minion.getTarget().isAlive()) return false;

            Entity e = minion.getEntityWorld() instanceof ServerWorld sw
                    ? sw.getEntity(minion.ownerBossUuid) : null;
            if (e instanceof LivingEntity le && le.isAlive()) {
                this.owner = le;
                return minion.squaredDistanceTo(owner) > 9.0; // > 3 blocks away
            }
            return false;
        }

        @Override
        public boolean shouldContinue() {
            if (owner == null || !owner.isAlive()) return false;
            return minion.squaredDistanceTo(owner) > 4.0; // > 2 blocks
        }

        @Override
        public void start() { timeToRecalcPath = 0; }

        @Override
        public void tick() {
            if (--timeToRecalcPath <= 0) {
                timeToRecalcPath = 10;
                minion.getNavigation().startMovingTo(owner, 1.0);
            }
        }

        @Override
        public void stop() {
            minion.getNavigation().stop();
            owner = null;
        }
    }
}
