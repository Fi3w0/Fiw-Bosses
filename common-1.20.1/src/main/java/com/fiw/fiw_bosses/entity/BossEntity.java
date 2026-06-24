package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.EquipmentConfig;
import com.fiw.fiw_bosses.config.EquipmentEntry;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerBossEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BossEntity extends Monster {

    private static final EntityDataAccessor<String> DATA_DISGUISE_ENTITY =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.STRING);

    // ── Boss state ────────────────────────────────────────────────────────────
    public enum BossState { INACTIVE, PRE_FIGHT, ACTIVE, PRE_DEATH }
    private BossState bossState = BossState.ACTIVE;

    private String bossId;
    private BossDefinition definition;
    private BossPhaseManager phaseManager;
    private final ServerBossEvent bossBar;
    private String movementMode = "side";
    private final SmartMovementController movementController = new SmartMovementController(this);

    // Dialogue
    private int dialogueTimer = 0;
    private int dialogueLine  = 0;

    // Pre-death guard (fire only once)
    private boolean preDeathTriggered = false;
    private boolean fightStarted = false;

    // ── Aggro switching ───────────────────────────────────────────────────────
    private int aggroSwitchTimer = 0;
    private static final int AGGRO_SWITCH_MIN = 100;
    private static final int AGGRO_SWITCH_MAX = 300;

    // ── Minion tracking ───────────────────────────────────────────────────────
    private final Set<UUID> minionUuids = new HashSet<>();

    // ── Damage tracking (for GuardianShieldGoal) ──────────────────────────────
    private long   lastDamageTick     = -1L;
    private Entity lastDamageAttacker = null;

    // ── Mark system (for DetectMarkGoal) ─────────────────────────────────────
    private UUID  markedTarget    = null;
    private float markDamageBonus = 0f;

    // ── Idle despawn / heal ───────────────────────────────────────────────────
    private int idleTimer      = 0;
    private int idleHealTimer  = 0;

    // ── Shield damage reduction ───────────────────────────────────────────────
    private float damageReduction = 0.0f;

    // ── Deferred goal-selector actions ───────────────────────────────────────
    private final Queue<Runnable> pendingGoalActions = new ArrayDeque<>();

    // ── Per-boss named ability cooldowns (survive phase/goal rebuilds) ────────
    private final java.util.Map<String, Integer> namedCooldowns = new java.util.HashMap<>();

    // ── Second Wind (auto-revive, armed by SecondWindGoal) ───────────────────
    private boolean secondWindArmed = false;
    private float   secondWindRevivePercent = 0.5f;

    // ── Adaptation (resist the damage type used most, set by AdaptationGoal) ──
    private final java.util.Map<String, Float> recentDamageByType = new java.util.HashMap<>();
    private String adaptedDamageType   = null;
    private float  adaptationResist     = 0.0f;
    private long   adaptationUntilTick  = -1L;

    // ── Cleanse debuff immunity (set by CleanseGoal) ─────────────────────────
    private long debuffImmuneUntilTick = -1L;

    public BossEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.bossBar = new ServerBossEvent(
                Component.literal("Boss"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.setPersistenceRequired();
        this.xpReward = 500;
        this.aggroSwitchTimer = AGGRO_SWITCH_MIN
                + (int) (Math.random() * (AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN));
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH,       200.0)
                .add(Attributes.MOVEMENT_SPEED,   0.3)
                .add(Attributes.ATTACK_DAMAGE,    10.0)
                .add(Attributes.ARMOR,            0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE,     64.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5);
    }

    // ── Definition / setup ───────────────────────────────────────────────────

    public void applyDefinition(BossDefinition def) {
        this.definition = def;
        this.bossId     = def.id;
        setMovementMode(def.movement);
        setDisguiseEntity(resolveDisguiseEntity(def.baseEntity, def.renderEntity));

        this.setCustomName(TextUtil.parseColorCodes(def.displayName));
        this.setCustomNameVisible(true);

        Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH)).setBaseValue(def.health);
        setHealth((float) def.health);
        Objects.requireNonNull(getAttribute(Attributes.ARMOR)).setBaseValue(def.armor);
        Objects.requireNonNull(getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(def.speed);
        Objects.requireNonNull(getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(def.knockbackResistance);
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(def.attackDamage);

        Component barName = TextUtil.parseColorCodes(def.displayName);
        bossBar.setName(barName);
        try { bossBar.setColor(BossEvent.BossBarColor.valueOf(def.bossBar.color.toUpperCase())); }
        catch (IllegalArgumentException ignored) {}
        try { bossBar.setOverlay(BossEvent.BossBarOverlay.valueOf(def.bossBar.overlay.toUpperCase())); }
        catch (IllegalArgumentException ignored) {}

        applyEquipment(def.equipment);

        this.phaseManager = new BossPhaseManager(this, def.phases);

        if (def.preFightDialogue != null && !def.preFightDialogue.isEmpty()) {
            this.bossState = BossState.INACTIVE;
            this.fightStarted = false;
            clearGoalsForInactive();
        } else {
            this.bossState = BossState.ACTIVE;
            this.fightStarted = true;
            this.phaseManager.transitionToPhase(0);
        }
    }

    public void applyEquipment(EquipmentConfig equipment) {
        if (equipment == null) return;
        setEquipmentSlot(EquipmentSlot.MAINHAND, equipment.mainHand);
        setEquipmentSlot(EquipmentSlot.OFFHAND,  equipment.offHand);
        setEquipmentSlot(EquipmentSlot.HEAD,     equipment.head);
        setEquipmentSlot(EquipmentSlot.CHEST,    equipment.chest);
        setEquipmentSlot(EquipmentSlot.LEGS,     equipment.legs);
        setEquipmentSlot(EquipmentSlot.FEET,     equipment.feet);
    }

    private void setEquipmentSlot(EquipmentSlot slot, EquipmentEntry entry) {
        ItemStack stack = ConfiguredItemStacks.equipment(
                entry, level().getServer(), slot.getName());
        if (!stack.isEmpty()) this.setItemSlot(slot, stack);
    }

    @Override
    protected void registerGoals() {
        // Goals are set dynamically by BossPhaseManager.
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DISGUISE_ENTITY, "");
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void customServerAiStep() {
        // Drain deferred goal actions before goalSelector.tick().
        Runnable action;
        while ((action = pendingGoalActions.poll()) != null) {
            action.run();
        }

        super.customServerAiStep();

        if (!namedCooldowns.isEmpty()) {
            namedCooldowns.replaceAll((k, v) -> v - 1);
            namedCooldowns.values().removeIf(v -> v <= 0);
        }

        // Fade out the adaptation damage history so it tracks *recent* damage only.
        if (!recentDamageByType.isEmpty() && tickCount % 20 == 0) {
            recentDamageByType.replaceAll((k, v) -> v * 0.85f);
            recentDamageByType.values().removeIf(v -> v < 0.5f);
        }

        tickBossBar();
        tickAggroSwitch();
        movementController.tick();
        tickIdleSystem();
        tickDialogueSystem();

        if (phaseManager != null) {
            phaseManager.tick();
        }
    }

    protected boolean showBossBar() { return true; }

    private void tickBossBar() {
        if (!showBossBar()) {
            bossBar.removeAllPlayers();
            return;
        }
        if (bossState == BossState.INACTIVE || bossState == BossState.PRE_FIGHT) {
            bossBar.removeAllPlayers();
            setTarget(null);
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) {
            for (ServerPlayer sp : sl.players()) {
                if (sp.isAlive() && !sp.isSpectator()
                        && sp.distanceToSqr(this) <= 64 * 64) {
                    bossBar.addPlayer(sp);
                }
            }
        }
    }

    // ── Activation (right-click) ─────────────────────────────────────────────

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && bossState == BossState.INACTIVE) {
            startPreFightSequence();
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private void startPreFightSequence() {
        bossState     = BossState.PRE_FIGHT;
        fightStarted  = true;
        dialogueTimer = 0;
        dialogueLine  = 0;
    }

    private void activateBoss() {
        bossState = BossState.ACTIVE;
        fightStarted = true;
        if (phaseManager != null) {
            int idx = phaseManager.getCurrentPhaseIndex();
            phaseManager.transitionToPhase(idx >= 0 ? idx : 0);
        }
    }

    // ── Pre-death sequence ───────────────────────────────────────────────────

    private void startPreDeathSequence() {
        preDeathTriggered = true;
        bossState         = BossState.PRE_DEATH;
        dialogueTimer     = 0;
        dialogueLine      = 0;
        setInvulnerable(true);
        setTarget(null);

        scheduleGoalAction(() -> {
            var goals = goalSelector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal).collect(Collectors.toList());
            goals.forEach(goalSelector::removeGoal);
            var targets = targetSelector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal).collect(Collectors.toList());
            targets.forEach(targetSelector::removeGoal);
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        });
    }

    // ── Dialogue ticker ───────────────────────────────────────────────────────

    private void tickDialogueSystem() {
        if (bossState == BossState.PRE_FIGHT) {
            tickLinedDialogue(
                    definition != null ? definition.preFightDialogue : List.of(),
                    definition != null ? definition.dialogueLineDelay : 60,
                    this::activateBoss
            );
        } else if (bossState == BossState.PRE_DEATH) {
            tickLinedDialogue(
                    definition != null ? definition.preDeathDialogue : List.of(),
                    definition != null ? definition.preDeathDialogueDelay : 40,
                    () -> {
                        // Must set state to ACTIVE before kill() — our hurt() override
                        // blocks all damage while in PRE_DEATH, which would prevent death.
                        bossState = BossState.ACTIVE;
                        setInvulnerable(false);
                        this.kill();
                    }
            );
        }
    }

    private void tickLinedDialogue(List<String> lines, int delay, Runnable onFinished) {
        if (lines.isEmpty()) { onFinished.run(); return; }
        dialogueTimer++;
        if (dialogueLine < lines.size() && dialogueTimer > dialogueLine * delay) {
            sendDialogueLine(lines.get(dialogueLine));
            dialogueLine++;
        }
        if (dialogueLine >= lines.size() && dialogueTimer > dialogueLine * delay) {
            onFinished.run();
        }
    }

    private void sendDialogueLine(String line) {
        Component text = TextUtil.parseColorCodes(line);
        for (Player player : level().players()) {
            if (player instanceof ServerPlayer sp
                    && sp.distanceToSqr(this) <= 80 * 80) {
                sp.sendSystemMessage(text);
            }
        }
    }

    // ── Damage ────────────────────────────────────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Fully immune while inactive / mid-dialogue
        if (bossState == BossState.INACTIVE
                || bossState == BossState.PRE_FIGHT
                || bossState == BossState.PRE_DEATH) {
            return false;
        }

        // Immune to own minions
        Entity attacker = source.getEntity();
        if (attacker != null && isMinion(attacker)) return false;

        // Adaptation: remember what hit us, and soften the type we've adapted to
        String damageType = classifyDamage(source);
        recentDamageByType.merge(damageType, amount, Float::sum);
        if (adaptedDamageType != null
                && adaptedDamageType.equals(damageType)
                && level().getGameTime() < adaptationUntilTick) {
            amount *= (1.0f - adaptationResist);
        }

        if (damageReduction > 0) amount *= (1.0f - damageReduction);

        // Second Wind: survive one otherwise-fatal blow, then disarm
        if (secondWindArmed && bossState == BossState.ACTIVE && getHealth() - amount <= 1.0f) {
            secondWindArmed = false;
            setHealth(Math.max(1.0f, getMaxHealth() * secondWindRevivePercent));
            onSecondWind();
            return false;
        }

        // Intercept lethal damage to start pre-death monologue
        if (!preDeathTriggered
                && definition != null
                && definition.preDeathDialogue != null
                && !definition.preDeathDialogue.isEmpty()
                && getHealth() - amount <= 1.0f) {
            setHealth(1.0f);
            startPreDeathSequence();
            return false;
        }

        if (!level().isClientSide) {
            lastDamageTick     = level().getGameTime();
            lastDamageAttacker = attacker;
        }

        boolean result = super.hurt(source, amount);

        if (result) idleTimer = 0;

        // Revenge aggro switch
        if (result && attacker instanceof Player playerAttacker) {
            LivingEntity current = getTarget();
            if (current != playerAttacker && getRandom().nextFloat() < 0.35f) {
                setTarget(playerAttacker);
                aggroSwitchTimer = AGGRO_SWITCH_MIN / 2;
            }
        }

        return result;
    }

    /** Applies bonus magic damage to the marked target on every successful melee hit. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result && markedTarget != null && markDamageBonus > 0
                && target.getUUID().equals(markedTarget)
                && target instanceof LivingEntity le) {
            le.hurt(damageSources().magic(), markDamageBonus);
        }
        return result;
    }

    // ── Death / removal ───────────────────────────────────────────────────────

    @Override
    public void checkDespawn() { /* bosses never auto-despawn */ }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!level().isClientSide && definition != null) {
            BossLootHandler.dropLoot(this, definition);
        }
        bossBar.removeAllPlayers();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeBossCustomData(tag);
    }

    /** Hook so {@link MinionEntity} can replace boss-specific persistence with its own. */
    protected void writeBossCustomData(CompoundTag tag) {
        if (bossId != null) tag.putString("BossId", bossId);
        if (phaseManager != null && phaseManager.getCurrentPhaseIndex() >= 0)
            tag.putInt("BossPhase", phaseManager.getCurrentPhaseIndex());
        tag.putString("BossState", bossState.name());
        tag.putBoolean("FightStarted", fightStarted);
        tag.putBoolean("PreDeathTriggered", preDeathTriggered);
        String disguise = getDisguiseEntity();
        if (!disguise.isEmpty()) tag.putString("DisguiseEntity", disguise);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readBossCustomData(tag);
    }

    /** Hook so {@link MinionEntity} can replace boss-specific persistence with its own. */
    protected void readBossCustomData(CompoundTag tag) {
        if (!tag.contains("BossId")) return;

        this.bossId = tag.getString("BossId");
        BossDefinition def = BossConfigLoader.getDefinition(bossId);
        if (def == null) {
            FiwBossesCore.LOGGER.warn("Boss definition '{}' not found, entity will be removed", bossId);
            this.discard();
            return;
        }

        float savedHealth = getHealth();

        applyDefinition(def);

        if (savedHealth > 0 && savedHealth <= getMaxHealth()) {
            setHealth(savedHealth);
        }

        // Restore saved phase silently (no transition messages or effects).
        int savedPhase = tag.contains("BossPhase") ? tag.getInt("BossPhase") : -1;

        if (tag.contains("BossState")) {
            BossState savedState;
            try { savedState = BossState.valueOf(tag.getString("BossState")); }
            catch (IllegalArgumentException e) { savedState = BossState.ACTIVE; }

            // PRE_FIGHT → INACTIVE on reload (don't resume mid-dialogue)
            // PRE_DEATH → ACTIVE on reload (boss survived the restart, let players finish it)
            if (savedState == BossState.PRE_FIGHT) savedState = BossState.INACTIVE;
            if (savedState == BossState.PRE_DEATH)  savedState = BossState.ACTIVE;
            this.bossState = savedState;
        }

        this.preDeathTriggered = tag.getBoolean("PreDeathTriggered");
        this.fightStarted = tag.getBoolean("FightStarted");
        if (!fightStarted && definition != null
                && definition.preFightDialogue != null
                && !definition.preFightDialogue.isEmpty()) {
            this.bossState = BossState.INACTIVE;
            clearGoalsForInactive();
        } else if (this.bossState == BossState.INACTIVE) {
            clearGoalsForInactive();
        } else if (phaseManager != null && phaseManager.getCurrentPhaseIndex() < 0) {
            phaseManager.restoreToPhase(savedPhase >= 0 ? savedPhase : 0);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveDisguiseEntity(String baseEntity, String renderEntity) {
        String value = renderEntity != null && !renderEntity.isBlank() ? renderEntity : baseEntity;
        if (value == null || value.isBlank() || "custom".equalsIgnoreCase(value)) return "";
        return value;
    }

    /** Removes all goals/targets except FloatGoal so INACTIVE boss just stands there. */
    private void clearGoalsForInactive() {
        scheduleGoalAction(() -> {
            var goals = goalSelector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal).collect(Collectors.toList());
            goals.forEach(goalSelector::removeGoal);
            var targets = targetSelector.getAvailableGoals().stream()
                    .map(WrappedGoal::getGoal).collect(Collectors.toList());
            targets.forEach(targetSelector::removeGoal);
            goalSelector.addGoal(0, new FloatGoal(this));
        });
    }

    private void tickAggroSwitch() {
        if (aggroSwitchTimer > 0) { aggroSwitchTimer--; return; }
        aggroSwitchTimer = AGGRO_SWITCH_MIN + getRandom().nextInt(AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN);

        LivingEntity current = getTarget();
        List<Player> nearbyPlayers = level().getEntitiesOfClass(
                Player.class, getBoundingBox().inflate(48),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (nearbyPlayers.size() <= 1) return;
        if (getRandom().nextFloat() < 0.4f) {
            if (current instanceof Player) nearbyPlayers.remove(current);
            if (!nearbyPlayers.isEmpty())
                setTarget(nearbyPlayers.get(getRandom().nextInt(nearbyPlayers.size())));
        }
    }

    private void tickIdleSystem() {
        if (definition == null || definition.idleTimeout <= 0) return;
        if (bossState != BossState.ACTIVE) return;

        boolean playerNearby = !level().getEntitiesOfClass(
                Player.class, getBoundingBox().inflate(64),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).isEmpty();

        if (playerNearby) { idleTimer = 0; idleHealTimer = 0; return; }

        idleTimer++;
        if (idleTimer < definition.idleTimeout) return;

        if ("despawn".equals(definition.idleAction)) {
            this.discard();
        } else if ("heal".equals(definition.idleAction)) {
            idleHealTimer++;
            if (idleHealTimer >= definition.idleHealInterval) {
                idleHealTimer = 0;
                if (getHealth() < getMaxHealth())
                    setHealth((float) Math.min(getHealth() + definition.idleHealAmount, getMaxHealth()));
            }
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    @Override
    public boolean ignoreExplosion() { return true; }

    // ── Minion management ────────────────────────────────────────────────────

    public void registerMinion(UUID minionUuid) { minionUuids.add(minionUuid); }
    public boolean isMinion(Entity entity) { return minionUuids.contains(entity.getUUID()); }
    public Set<UUID> getMinionUuids() { return minionUuids; }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getBossId() { return bossId; }
    public BossDefinition getDefinition() { return definition; }
    public BossPhaseManager getPhaseManager() { return phaseManager; }
    public String getMovementMode() { return movementMode; }
    public void setMovementMode(String movementMode) {
        this.movementMode = movementMode != null ? movementMode : "side";
        movementController.reset();
    }
    public String getDisguiseEntity() { return entityData.get(DATA_DISGUISE_ENTITY); }
    public void setDisguiseEntity(String entityId) {
        entityData.set(DATA_DISGUISE_ENTITY, entityId != null && !"custom".equalsIgnoreCase(entityId) ? entityId : "");
    }
    public net.minecraft.world.entity.ai.goal.GoalSelector getGoalSelector() { return this.goalSelector; }
    public net.minecraft.world.entity.ai.goal.GoalSelector getTargetSelector() { return this.targetSelector; }
    public BossState getBossState() { return bossState; }
    public boolean isActive() { return bossState == BossState.ACTIVE; }

    // Damage-tracking accessors (used by GuardianShieldGoal)
    public long   getLastDamageTick()     { return lastDamageTick; }
    public Entity getLastDamageAttacker() { return lastDamageAttacker; }

    // Mark-target accessors (used by DetectMarkGoal)
    public void setMarkTarget(UUID uuid, float bonus) { markedTarget = uuid; markDamageBonus = bonus; }
    public void clearMarkTarget() { markedTarget = null; markDamageBonus = 0f; }
    public UUID getMarkedTarget() { return markedTarget; }

    public void scheduleGoalAction(Runnable action) { pendingGoalActions.add(action); }

    /** Named ability cooldowns that persist across phase/goal rebuilds. */
    public boolean isOnCooldown(String key) { return namedCooldowns.getOrDefault(key, 0) > 0; }
    public void setCooldown(String key, int ticks) { if (ticks > 0) namedCooldowns.put(key, ticks); }

    public void setDamageReduction(float reduction) { this.damageReduction = reduction; }
    public float getDamageReduction() { return damageReduction; }

    // ── Second Wind (used by SecondWindGoal) ─────────────────────────────────
    public void armSecondWind(float revivePercent) {
        this.secondWindArmed = true;
        this.secondWindRevivePercent = revivePercent;
    }
    public boolean isSecondWindArmed() { return secondWindArmed; }

    private void onSecondWind() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 90, 0.6, 1.0, 0.6, 0.35);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.6f, 0.8f);
        }
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true));
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, true));
    }

    // ── Adaptation (used by AdaptationGoal) ──────────────────────────────────
    /** The damage type that has dealt the most (recent) damage, or null if none yet. */
    public String getDominantDamageType() {
        String best = null;
        float bestVal = 0f;
        for (java.util.Map.Entry<String, Float> e : recentDamageByType.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    public void setAdaptation(String damageType, float resistPercent, int durationTicks) {
        this.adaptedDamageType  = damageType;
        this.adaptationResist    = resistPercent;
        this.adaptationUntilTick = level().getGameTime() + durationTicks;
    }

    public String getAdaptedDamageType() {
        return level().getGameTime() < adaptationUntilTick ? adaptedDamageType : null;
    }

    private static String classifyDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE))      return "fire";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "explosion";
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return "projectile";
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return "magic";
        return "melee";
    }

    // ── Cleanse debuff immunity (used by CleanseGoal) ────────────────────────
    public void setDebuffImmuneTicks(int ticks) {
        if (ticks > 0) this.debuffImmuneUntilTick = level().getGameTime() + ticks;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (level().getGameTime() < debuffImmuneUntilTick
                && !effectInstance.getEffect().isBeneficial()) {
            return false;
        }
        return super.canBeAffected(effectInstance);
    }
}
