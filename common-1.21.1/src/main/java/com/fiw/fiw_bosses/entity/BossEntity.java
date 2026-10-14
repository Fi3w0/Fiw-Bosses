package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.EquipmentConfig;
import com.fiw.fiw_bosses.config.EquipmentEntry;
import com.fiw.fiw_bosses.config.FluidSettings;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.config.SettingsLoader;
import com.fiw.fiw_bosses.fight.BossFightHandler;
import com.fiw.fiw_bosses.fight.FightLedger;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerBossEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BossEntity extends Monster {

    private static final EntityDataAccessor<String> DATA_DISGUISE_ENTITY =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.STRING);
    // Configured max HP when it exceeds the MAX_HEALTH attribute cap (0 = use the attribute).
    // Synced so the client (Jade, WTHIT, The One Probe, ...) reads the real max health too.
    private static final EntityDataAccessor<Float> DATA_CONFIGURED_MAX_HEALTH =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);

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
    // Bar membership / idle player checks / combat sampling run on these intervals, not every tick.
    private static final int BOSS_BAR_REFRESH_TICKS   = 10;
    private static final int IDLE_CHECK_TICKS         = 20;
    public  static final int COMBAT_TRACK_TICKS       = 5;

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
    // Starts true so a boss can't idle out before the first player check runs.
    private boolean idlePlayerNearby = true;

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


    // ── Raid tracking (see BossFightHandler) ────────────────────────────────
    private FightLedger ledger = new FightLedger();
    private final Map<UUID, Float> trackedHp = new java.util.HashMap<>();
    private final Map<UUID, Long> lastTargeted = new java.util.HashMap<>();
    private boolean rewardsSuppressed = false;
    private boolean rewardsHandled = false;
    // Player whose hit is being applied right now, so die() can credit the killing blow.
    private ServerPlayer pendingHitter = null;
    private float healthBeforeHit = 0f;
    private UUID lastPlayerHitter = null;

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

        applyConfiguredHealth(def.health);
        Objects.requireNonNull(getAttribute(Attributes.ARMOR)).setBaseValue(def.armor);
        Objects.requireNonNull(getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(def.speed);
        Objects.requireNonNull(getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(def.knockbackResistance);
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(def.attackDamage);

        if (def.fluid != null && def.fluid.canSwim) {
            setPathfindingMalus(PathType.WATER, 0.0f);
        }

        Component barName = TextUtil.parseColorCodes(def.displayName);
        bossBar.setName(barName);
        bossBarTitle = barName;
        shownHealth = -1L;
        try { bossBar.setColor(BossEvent.BossBarColor.valueOf(def.bossBar.color.toUpperCase())); }
        catch (IllegalArgumentException e) { FiwBossesCore.LOGGER.warn("Invalid boss bar color '{}' for boss '{}', using default", def.bossBar.color, def.id); }
        try { bossBar.setOverlay(BossEvent.BossBarOverlay.valueOf(def.bossBar.overlay.toUpperCase())); }
        catch (IllegalArgumentException e) { FiwBossesCore.LOGGER.warn("Invalid boss bar overlay '{}' for boss '{}', using default", def.bossBar.overlay, def.id); }

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

    /**
     * Uncapped health: the MAX_HEALTH attribute is clamped to its range (1024 in vanilla).
     * When the configured health is above that cap, the attribute stays at the cap and
     * {@link #getMaxHealth()} reports the configured value instead, so real HP, damage,
     * heals and every health display (boss bar, Jade, ...) use the true numbers.
     */
    private void applyConfiguredHealth(float configured) {
        var maxHealth = Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH));
        maxHealth.setBaseValue(configured);
        // getValue() is clamped to the attribute's range, so this is the effective cap
        // (1024 in vanilla, higher if another mod raised it).
        float cap = (float) maxHealth.getValue();
        if (configured > cap && cap > 0) {
            maxHealth.setBaseValue(cap);
            entityData.set(DATA_CONFIGURED_MAX_HEALTH, configured);
        } else {
            entityData.set(DATA_CONFIGURED_MAX_HEALTH, 0.0f);
        }
        setHealth(getMaxHealth());
    }

    // LivingEntity.getMaxHealth() is final but reads MAX_HEALTH through this method.
    @Override
    public double getAttributeValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        if (attribute.value() == Attributes.MAX_HEALTH.value()) {
            float configured = entityData.get(DATA_CONFIGURED_MAX_HEALTH);
            if (configured > 0.0f) return configured;
        }
        return super.getAttributeValue(attribute);
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
                entry, level().getServer(), registryAccess(), slot.getSerializedName());
        if (!stack.isEmpty()) this.setItemSlot(slot, stack);
    }

    @Override
    protected void registerGoals() {
        // Goals are set dynamically by BossPhaseManager.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DISGUISE_ENTITY, "");
        builder.define(DATA_CONFIGURED_MAX_HEALTH, 0.0f);
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
        tickFluidHandling();
        tickIdleSystem();
        tickDialogueSystem();
        if (tracksFight() && tickCount % COMBAT_TRACK_TICKS == 0) BossFightHandler.trackCombat(this);

        if (phaseManager != null) {
            phaseManager.tick();
        }
    }

    protected boolean showBossBar() { return true; }

    // Base bar title and the last HP shown, so the title is only resent when the number changes.
    private Component bossBarTitle = null;
    private long shownHealth = -1L;

    /** Appends the boss's HP to the bar title when enabled for this boss. */
    private void updateBossBarHealthText() {
        if (definition == null || bossBarTitle == null) return;
        Boolean perBoss = definition.bossBar != null ? definition.bossBar.showHealth : null;
        boolean show = perBoss != null ? perBoss : SettingsLoader.get().showBossBarHealth;
        if (!show) {
            if (shownHealth != -1L) { bossBar.setName(bossBarTitle); shownHealth = -1L; }
            return;
        }
        long current = (long) Math.ceil(getHealth());
        if (current == shownHealth) return;
        shownHealth = current;
        long max = Math.round(getMaxHealth());
        bossBar.setName(Component.empty().append(bossBarTitle).append(TextUtil.parseColorCodes(
                "  &f" + String.format(java.util.Locale.ROOT, "%,d", current)
                        + "&7/&f" + String.format(java.util.Locale.ROOT, "%,d", max) + " &c\u2764")));
    }

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
        updateBossBarHealthText();
        if (tickCount % BOSS_BAR_REFRESH_TICKS == 0) refreshBossBarPlayers();
    }

    /**
     * Adds players who came into range and removes those who left (or died, logged out, changed
     * dimension). addPlayer/removePlayer only send a packet when membership changes, so players
     * who stay in range cost nothing — unlike clearing and re-adding everyone each tick.
     */
    private void refreshBossBarPlayers() {
        if (!(level() instanceof ServerLevel sl)) return;
        List<ServerPlayer> inRange = new java.util.ArrayList<>();
        for (ServerPlayer sp : sl.players()) {
            if (sp.isAlive() && !sp.isSpectator()
                    && sp.distanceToSqr(this) <= 64 * 64) {
                inRange.add(sp);
            }
        }
        for (ServerPlayer sp : List.copyOf(bossBar.getPlayers())) {
            if (!inRange.contains(sp)) bossBar.removePlayer(sp);
        }
        for (ServerPlayer sp : inRange) bossBar.addPlayer(sp);
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
            if (shouldFloat()) goalSelector.addGoal(0, new FloatGoal(this));
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
        // /kill and /boss kill (generic_kill): an admin kill must always land — skip immunity
        // states, protection, Second Wind and the pre-death hold.
        if (source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);

        // Fully immune while inactive / mid-dialogue
        if (bossState == BossState.INACTIVE
                || bossState == BossState.PRE_FIGHT
                || bossState == BossState.PRE_DEATH) {
            return false;
        }

        // Faction / own-group friendly-fire gate
        Entity attacker = source.getEntity();
        if (attacker != null && blocksFriendlyDamageFrom(attacker)) return false;

        // Config-driven damage protection (item id > damage type id > category).
        // Runs before adaptation so fully-blocked damage is never recorded there.
        float protMult = protectionMultiplier(source);
        if (protMult <= 0.0f) return false;
        if (protMult != 1.0f) amount *= protMult;

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
            creditIntercepted(source);
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
            creditIntercepted(source);
            setHealth(1.0f);
            startPreDeathSequence();
            return false;
        }

        if (!level().isClientSide) {
            lastDamageTick     = level().getGameTime();
            lastDamageAttacker = attacker;
        }

        pendingHitter = tracksFight() ? BossFightHandler.playerFrom(source) : null;
        healthBeforeHit = getHealth();
        boolean result = super.hurt(source, amount);
        if (pendingHitter != null) {
            // Still pending means the hit didn't kill (die() consumes it on a kill).
            if (result) creditDamage(pendingHitter, healthBeforeHit - getHealth());
            pendingHitter = null;
        }

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

    /** One-shot self fall-damage waiver, consumed by the next landing (e.g. after a jump ability). */
    private boolean ignoreNextFallDamage = false;
    public void ignoreNextFallDamage() { this.ignoreNextFallDamage = true; }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        if (ignoreNextFallDamage) {
            ignoreNextFallDamage = false;
            return false;
        }
        return super.causeFallDamage(fallDistance, damageMultiplier, source);
    }

    // ── Death / removal ───────────────────────────────────────────────────────

    @Override
    public void checkDespawn() { /* bosses never auto-despawn */ }

    @Override
    public void die(DamageSource damageSource) {
        if (pendingHitter != null) {
            creditDamage(pendingHitter, healthBeforeHit);
            pendingHitter = null;
        }
        super.die(damageSource);
        if (!level().isClientSide && definition != null) {
            if (tracksFight()) BossFightHandler.onBossDeath(this, damageSource);
            else BossLootHandler.dropLoot(this, definition);
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
        if (!ledger.isEmpty()) tag.putString("FightLedger", ledger.encode());
        if (phaseManager != null && phaseManager.getPhaseFloor() >= 0)
            tag.putInt("PhaseFloor", phaseManager.getPhaseFloor());
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

        // Vanilla's load clamped Health to the attribute cap; read the raw saved value.
        float savedHealth = tag.contains("Health") ? tag.getFloat("Health") : -1.0f;

        applyDefinition(def);

        // 0 keeps a boss that died right before the save dead (its death animation then
        // finishes and removes it) instead of reviving it at full health.
        if (savedHealth >= 0.0f) setHealth(savedHealth);

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
        } else if (phaseManager != null) {
            // applyDefinition() already entered phase 0 for bosses without pre-fight dialogue,
            // so restore the saved phase whenever it differs (e.g. an admin-forced phase).
            int current = phaseManager.getCurrentPhaseIndex();
            if (savedPhase >= 0 && savedPhase != current) phaseManager.restoreToPhase(savedPhase);
            else if (current < 0) phaseManager.restoreToPhase(0);
        }
        if (tag.contains("FightLedger")) this.ledger = FightLedger.decode(tag.getString("FightLedger"));
        if (phaseManager != null) phaseManager.setPhaseFloor(tag.contains("PhaseFloor") ? tag.getInt("PhaseFloor") : -1);
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
            if (shouldFloat()) goalSelector.addGoal(0, new FloatGoal(this));
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

        if (tickCount % IDLE_CHECK_TICKS == 0) {
            AABB area = getBoundingBox().inflate(64);
            idlePlayerNearby = false;
            for (Player p : level().players()) {
                if (p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && area.intersects(p.getBoundingBox())) {
                    idlePlayerNearby = true;
                    break;
                }
            }
        }

        if (idlePlayerNearby) { idleTimer = 0; idleHealTimer = 0; return; }

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
                // Healed back to full = the attempt is over; earlier damage earns nothing.
                if (getHealth() >= getMaxHealth()) ledger.clear();
            }
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    // ── Minion management ────────────────────────────────────────────────────

    public void registerMinion(UUID minionUuid) { minionUuids.add(minionUuid); }
    public boolean isMinion(Entity entity) { return minionUuids.contains(entity.getUUID()); }
    public Set<UUID> getMinionUuids() { return minionUuids; }

    // ── Factions / allies ────────────────────────────────────────────────────

    public String getFaction() {
        String f = definition != null ? definition.faction : null;
        return f != null && !f.isEmpty() ? f : null;
    }

    public boolean canDamageFactionAllies() { return definition != null && definition.damageFactionAllies; }
    public boolean canTargetFactionAllies() { return definition != null && definition.targetFactionAllies; }
    public boolean canDamageOwnGroup()      { return definition != null && definition.damageOwnGroup; }

    /** Own-group = this boss's registered minions. MinionEntity adds owner + siblings. */
    protected boolean isOwnGroup(Entity other) {
        return isMinion(other);
    }

    /** Same non-empty faction on both sides. */
    protected boolean isSameFaction(Entity other) {
        String faction = getFaction();
        return faction != null && other instanceof BossEntity be && faction.equals(be.getFaction());
    }

    /** Allies: own minion group (either direction) or same faction. */
    public boolean isAlly(Entity other) {
        return isOwnGroup(other)
                || (other instanceof BossEntity be && be.isOwnGroup(this))
                || isSameFaction(other);
    }

    /** Central gate used by ability/AoE goals: may this entity's abilities hit e? */
    public boolean canAbilityHit(Entity e) {
        if (e == this || !e.isAlive()) return false;
        boolean ownGroup = isOwnGroup(e) || (e instanceof BossEntity be && be.isOwnGroup(this));
        if (ownGroup) return canDamageOwnGroup();
        if (isSameFaction(e)) return canDamageFactionAllies();
        return true;
    }

    /** Victim-side friendly-fire gate: true when damage from attacker must be cancelled. */
    private boolean blocksFriendlyDamageFrom(Entity attacker) {
        BossEntity attackerBoss = attacker instanceof BossEntity be ? be : null;
        boolean ownGroup = isOwnGroup(attacker)
                || (attackerBoss != null && attackerBoss.isOwnGroup(this));
        if (ownGroup) {
            return attackerBoss == null || !attackerBoss.canDamageOwnGroup();
        }
        return attackerBoss != null && isSameFaction(attackerBoss)
                && !attackerBoss.canDamageFactionAllies();
    }

    // ── Raid tracking (used by BossFightHandler) ────────────────────────────

    /** Bosses track fights and grant raid rewards; minions don't. */
    protected boolean tracksFight() { return true; }
    public FightLedger getLedger() { return ledger; }
    public Map<UUID, Float> getTrackedHp() { return trackedHp; }
    public Map<UUID, Long> getLastTargeted() { return lastTargeted; }
    /** Admin kills (/boss kill) grant no raid rewards, commands or summary. */
    public void suppressRewards() { rewardsSuppressed = true; }
    public boolean areRewardsSuppressed() { return rewardsSuppressed; }
    /** True exactly once per entity, so rewards can never pay out twice. */
    public boolean claimRewards() {
        if (rewardsHandled) return false;
        rewardsHandled = true;
        return true;
    }
    /** The player whose hit started the pre-death monologue (the real finisher). */
    public UUID getPreDeathFinisher() { return preDeathTriggered ? lastPlayerHitter : null; }

    /** Credits HP removed by a player. */
    private void creditDamage(ServerPlayer player, float realAmount) {
        if (realAmount <= 0) return;
        lastPlayerHitter = player.getUUID();
        ledger.recordDealt(player.getUUID(), player.getName().getString(),
                realAmount, level().getGameTime());
    }

    /** A lethal hit swallowed by Second Wind / pre-death still counts as the remaining HP. */
    private void creditIntercepted(DamageSource source) {
        ServerPlayer player = tracksFight() ? BossFightHandler.playerFrom(source) : null;
        if (player != null) creditDamage(player, getHealth());
    }

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

    // ── Damage protection (config-driven per-type multipliers) ──────────────

    /** Protection multiplier for this damage; 1.0 = unchanged, <= 0 = fully immune. */
    protected float protectionMultiplier(DamageSource source) {
        if (currentPhaseProtection() == null
                && (definition == null || definition.protection == null)) {
            return 1.0f;
        }
        ItemStack weapon = resolveWeaponItem(source);
        if (weapon != null && !weapon.isEmpty()) {
            Float v = protectionValue(BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString());
            if (v != null) return v;
        }
        String typeId = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString()).orElse(null);
        if (typeId != null) {
            Float v = protectionValue(typeId);
            if (v != null) return v;
        }
        String category = protectionCategory(source);
        if (category != null) {
            Float v = protectionValue(category);
            if (v != null) return v;
        }
        return 1.0f;
    }

    /** Phase-map entry wins over the boss-level entry for the same key. */
    private Float protectionValue(String key) {
        Map<String, Float> phaseMap = currentPhaseProtection();
        if (phaseMap != null) {
            Float v = phaseMap.get(key);
            if (v != null) return v;
        }
        Map<String, Float> bossMap = definition != null ? definition.protection : null;
        return bossMap != null ? bossMap.get(key) : null;
    }

    private Map<String, Float> currentPhaseProtection() {
        PhaseDefinition phase = phaseManager != null ? phaseManager.getCurrentPhase() : null;
        return phase != null ? phase.protection : null;
    }

    /** The weapon behind this damage: tracked weapon if any, else the attacker's main hand. */
    private static ItemStack resolveWeaponItem(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && !weapon.isEmpty()) return weapon;
        if (source.getEntity() instanceof LivingEntity attacker) return attacker.getMainHandItem();
        return null;
    }

    /** Category key for protection lookups; "melee" only for direct living hits. */
    private static String protectionCategory(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE))            return "fire";
        if (source.is(DamageTypeTags.IS_EXPLOSION))       return "explosion";
        if (source.is(DamageTypeTags.IS_PROJECTILE))      return "projectile";
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return "magic";
        if (source.is(DamageTypeTags.IS_LIGHTNING))       return "lightning";
        if (source.is(DamageTypeTags.IS_FREEZING))        return "freezing";
        if (source.is(DamageTypeTags.IS_DROWNING))        return "drowning";
        if (source.is(DamageTypeTags.IS_FALL))            return "fall";
        return source.getDirectEntity() instanceof LivingEntity ? "melee" : null;
    }

    // ── Fluid handling (config-driven water/lava behavior) ──────────────────

    private FluidSettings fluidSettings() {
        return definition != null ? definition.fluid : null;
    }

    /** Whether the swim-up FloatGoal should be installed for this entity. */
    public boolean shouldFloat() {
        FluidSettings fluid = fluidSettings();
        return fluid == null || fluid.floats;
    }

    @Override
    public boolean fireImmune() {
        FluidSettings fluid = fluidSettings();
        return (fluid != null && fluid.fireImmune) || super.fireImmune();
    }

    @Override
    public boolean isPushedByFluid() {
        FluidSettings fluid = fluidSettings();
        if (fluid != null && !fluid.pushedByFluids) return false;
        return super.isPushedByFluid();
    }

    private void tickFluidHandling() {
        FluidSettings fluid = fluidSettings();
        if (fluid == null) return;
        if (fluid.drownImmune && getAirSupply() < getMaxAirSupply()) {
            setAirSupply(getMaxAirSupply());
        }
        if (fluid.fireImmune && getRemainingFireTicks() > 0) {
            clearFire();
        }
        if (fluid.swimSpeed != 1.0f && (isInWater() || isInLava())) {
            Vec3 dm = getDeltaMovement();
            if (fluid.swimSpeed < 1.0f) {
                setDeltaMovement(dm.x * fluid.swimSpeed, dm.y, dm.z * fluid.swimSpeed);
            } else {
                // Boost horizontal speed toward a cap so it can't grow unbounded.
                double h = Math.sqrt(dm.x * dm.x + dm.z * dm.z);
                double cap = getAttributeValue(Attributes.MOVEMENT_SPEED) * fluid.swimSpeed;
                if (h > 1.0e-4 && h < cap) {
                    double scale = Math.min(fluid.swimSpeed, cap / h);
                    setDeltaMovement(dm.x * scale, dm.y, dm.z * scale);
                }
            }
        }
    }

    // ── Cleanse debuff immunity (used by CleanseGoal) ────────────────────────
    public void setDebuffImmuneTicks(int ticks) {
        if (ticks > 0) this.debuffImmuneUntilTick = level().getGameTime() + ticks;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (level().getGameTime() < debuffImmuneUntilTick
                && !effectInstance.getEffect().value().isBeneficial()) {
            return false;
        }
        return super.canBeAffected(effectInstance);
    }
}
