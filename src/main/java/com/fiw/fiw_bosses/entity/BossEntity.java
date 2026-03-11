package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.*;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;

public class BossEntity extends HostileEntity {

    private String bossId;
    private BossDefinition definition;
    private BossPhaseManager phaseManager;
    private final ServerBossBar bossBar;

    // Aggro switching
    private int aggroSwitchTimer = 0;
    private static final int AGGRO_SWITCH_MIN = 100;
    private static final int AGGRO_SWITCH_MAX = 300;

    // Minion tracking
    private final Set<UUID> minionUuids = new HashSet<>();

    // Strafing
    private int strafeTimer = 0;
    private int strafeDir = 1;

    public BossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = new ServerBossBar(
                Text.literal("Boss"),
                BossBar.Color.RED,
                BossBar.Style.PROGRESS
        );
        this.setPersistent();
        this.experiencePoints = 500;
        this.aggroSwitchTimer = AGGRO_SWITCH_MIN + (int) (Math.random() * (AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN));
    }

    public static DefaultAttributeContainer.Builder createBossAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_ARMOR, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5);
    }

    public void applyDefinition(BossDefinition def) {
        this.definition = def;
        this.bossId = def.id;

        this.setCustomName(TextUtil.parseColorCodes(def.displayName));
        this.setCustomNameVisible(true);

        Objects.requireNonNull(getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)).setBaseValue(def.health);
        setHealth(def.health);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.GENERIC_ARMOR)).setBaseValue(def.armor);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)).setBaseValue(def.speed);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)).setBaseValue(def.knockbackResistance);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)).setBaseValue(def.attackDamage);

        Text barName = TextUtil.parseColorCodes(def.displayName);
        bossBar.setName(barName);
        try {
            bossBar.setColor(BossBar.Color.valueOf(def.bossBar.color.toUpperCase()));
        } catch (IllegalArgumentException ignored) {}
        try {
            bossBar.setStyle(BossBar.Style.valueOf(def.bossBar.overlay.toUpperCase()));
        } catch (IllegalArgumentException ignored) {}

        applyEquipment(def.equipment);

        this.phaseManager = new BossPhaseManager(this, def.phases);
        this.phaseManager.transitionToPhase(0);
    }

    public void applyEquipment(EquipmentConfig equipment) {
        if (equipment == null) return;
        setEquipmentSlot(EquipmentSlot.MAINHAND, equipment.mainHand);
        setEquipmentSlot(EquipmentSlot.OFFHAND, equipment.offHand);
        setEquipmentSlot(EquipmentSlot.HEAD, equipment.head);
        setEquipmentSlot(EquipmentSlot.CHEST, equipment.chest);
        setEquipmentSlot(EquipmentSlot.LEGS, equipment.legs);
        setEquipmentSlot(EquipmentSlot.FEET, equipment.feet);
    }

    private void setEquipmentSlot(EquipmentSlot slot, EquipmentEntry entry) {
        if (entry == null || entry.item == null) return;
        Identifier itemId = Identifier.tryParse(entry.item);
        if (itemId == null) return;
        var item = Registries.ITEM.get(itemId);
        if (item == null) {
            FiwBosses.LOGGER.warn("Unknown item ID in equipment slot {}: {}", slot, entry.item);
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (entry.nbt != null && !entry.nbt.isEmpty()) {
            try {
                NbtCompound nbt = StringNbtReader.parse(entry.nbt);
                stack.setNbt(nbt);
            } catch (Exception e) {
                FiwBosses.LOGGER.warn("Failed to parse NBT for equipment slot {}: {}", slot, e.getMessage());
            }
        }
        this.equipStack(slot, stack);
    }

    @Override
    protected void initGoals() {
        // Goals are set dynamically by BossPhaseManager
    }

    @Override
    protected void mobTick() {
        super.mobTick();

        bossBar.setPercent(getHealth() / getMaxHealth());

        if (!getWorld().isClient) {
            for (var player : getWorld().getPlayers()) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    if (serverPlayer.squaredDistanceTo(this) <= 64 * 64) {
                        bossBar.addPlayer(serverPlayer);
                    } else {
                        bossBar.removePlayer(serverPlayer);
                    }
                }
            }

            tickAggroSwitch();
            tickStrafing();
        }

        if (phaseManager != null) {
            phaseManager.tick();
        }
    }

    private void tickAggroSwitch() {
        if (aggroSwitchTimer > 0) {
            aggroSwitchTimer--;
            return;
        }

        aggroSwitchTimer = AGGRO_SWITCH_MIN + getRandom().nextInt(AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN);

        LivingEntity current = getTarget();
        List<PlayerEntity> nearbyPlayers = getWorld().getEntitiesByClass(
                PlayerEntity.class,
                getBoundingBox().expand(48),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
        );

        if (nearbyPlayers.size() <= 1) return;

        // 40% chance to switch to a random different player
        if (getRandom().nextFloat() < 0.4f) {
            if (current instanceof PlayerEntity) {
                nearbyPlayers.remove(current);
            }
            if (!nearbyPlayers.isEmpty()) {
                PlayerEntity newTarget = nearbyPlayers.get(getRandom().nextInt(nearbyPlayers.size()));
                setTarget(newTarget);
            }
        }
    }

    private void tickStrafing() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        // Skip strafing while an ability goal is holding MOVE — it would fight the freeze/movement
        boolean abilityHoldingMove = goalSelector.getGoals().stream().anyMatch(pg ->
                pg.isRunning()
                && pg.getGoal().getControls().contains(Goal.Control.MOVE)
                && !(pg.getGoal() instanceof net.minecraft.entity.ai.goal.MeleeAttackGoal)
                && !(pg.getGoal() instanceof net.minecraft.entity.ai.goal.WanderAroundFarGoal));
        if (abilityHoldingMove) {
            strafeTimer = 0;
            return;
        }

        double dist = distanceTo(target);

        if (dist < 7.0 && dist > 2.0) {
            strafeTimer++;

            if (strafeTimer % 30 == 0 && getRandom().nextFloat() < 0.5f) {
                strafeDir *= -1;
            }

            if (strafeTimer % 2 == 0) {
                getMoveControl().strafeTo(-0.3f, strafeDir * 0.6f);
                getLookControl().lookAt(target, 30.0f, 30.0f);
            }
        } else {
            strafeTimer = 0;
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Immune to damage from own minions
        Entity attacker = source.getAttacker();
        if (attacker != null && isMinion(attacker)) {
            return false;
        }

        if (damageReduction > 0) {
            amount *= (1.0f - damageReduction);
        }

        boolean result = super.damage(source, amount);

        // Revenge aggro switch — if another player hits us, chance to switch
        if (result && attacker instanceof PlayerEntity playerAttacker) {
            LivingEntity current = getTarget();
            if (current != playerAttacker && getRandom().nextFloat() < 0.35f) {
                setTarget(playerAttacker);
                aggroSwitchTimer = AGGRO_SWITCH_MIN / 2;
            }
        }

        return result;
    }

    @Override
    public void checkDespawn() {
        // Bosses never despawn
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient && definition != null) {
            BossLootHandler.dropLoot(this, definition);
        }
        bossBar.clearPlayers();
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        bossBar.clearPlayers();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (bossId != null) {
            nbt.putString("BossId", bossId);
        }
        if (phaseManager != null && phaseManager.getCurrentPhaseIndex() >= 0) {
            nbt.putInt("BossPhase", phaseManager.getCurrentPhaseIndex());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BossId")) {
            this.bossId = nbt.getString("BossId");
            BossDefinition def = BossConfigLoader.getDefinition(bossId);
            if (def != null) {
                applyDefinition(def);
                // Restore the phase the boss was in before the world was saved
                if (nbt.contains("BossPhase") && phaseManager != null) {
                    int savedPhase = nbt.getInt("BossPhase");
                    if (savedPhase > 0) {
                        phaseManager.transitionToPhase(savedPhase);
                    }
                }
            } else {
                FiwBosses.LOGGER.warn("Boss definition '{}' not found, entity will be removed", bossId);
                this.discard();
            }
        }
    }

    @Override
    public boolean isImmuneToExplosion() {
        return true;
    }

    // ---- Minion management ----
    public void registerMinion(UUID minionUuid) {
        minionUuids.add(minionUuid);
    }

    public boolean isMinion(Entity entity) {
        return minionUuids.contains(entity.getUuid());
    }

    public Set<UUID> getMinionUuids() {
        return minionUuids;
    }

    // ---- Accessors ----
    public String getBossId() { return bossId; }
    public BossDefinition getDefinition() { return definition; }
    public BossPhaseManager getPhaseManager() { return phaseManager; }
    public GoalSelector getGoalSelector() { return this.goalSelector; }
    public GoalSelector getTargetSelector() { return this.targetSelector; }

    // ---- Shield damage reduction ----
    private float damageReduction = 0.0f;
    public void setDamageReduction(float reduction) { this.damageReduction = reduction; }
    public float getDamageReduction() { return damageReduction; }
}
