package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.*;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import com.fiw.fiw_bosses.util.LegacyNbtToComponents;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class BossEntity extends HostileEntity {

    // ── Boss state ────────────────────────────────────────────────────────────
    public enum BossState { INACTIVE, PRE_FIGHT, ACTIVE, PRE_DEATH }
    private BossState bossState = BossState.ACTIVE;

    private String bossId;
    private BossDefinition definition;
    private BossPhaseManager phaseManager;
    private final ServerBossBar bossBar;

    // Dialogue
    private int dialogueTimer = 0;
    private int dialogueLine  = 0;

    // Pre-death guard (fire only once)
    private boolean preDeathTriggered = false;

    // ── Aggro switching ───────────────────────────────────────────────────────
    private int aggroSwitchTimer = 0;
    private static final int AGGRO_SWITCH_MIN = 100;
    private static final int AGGRO_SWITCH_MAX = 300;

    // ── Minion tracking ───────────────────────────────────────────────────────
    private final Set<UUID> minionUuids = new HashSet<>();

    // ── Strafing ──────────────────────────────────────────────────────────────
    private int strafeTimer = 0;
    private int strafeDir   = 1;

    // ── Damage tracking (for GuardianShieldGoal) ──────────────────────────────
    private long   lastDamageTick     = -1L;
    private Entity lastDamageAttacker = null;

    // ── Mark system (for DetectMarkGoal) ─────────────────────────────────────
    private UUID  markedTarget    = null;
    private float markDamageBonus = 0f;

    public BossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = new ServerBossBar(
                Text.literal("Boss"),
                BossBar.Color.RED,
                BossBar.Style.PROGRESS
        );
        this.setPersistent();
        this.experiencePoints = 500;
        this.aggroSwitchTimer = AGGRO_SWITCH_MIN
                + (int) (Math.random() * (AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN));
    }

    public static DefaultAttributeContainer.Builder createBossAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH,           200.0)
                .add(EntityAttributes.MOVEMENT_SPEED,       0.3)
                .add(EntityAttributes.ATTACK_DAMAGE,        10.0)
                .add(EntityAttributes.ARMOR,                0.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(EntityAttributes.FOLLOW_RANGE,         64.0)
                .add(EntityAttributes.ATTACK_KNOCKBACK,     1.5);
    }

    // ── Definition / setup ───────────────────────────────────────────────────

    public void applyDefinition(BossDefinition def) {
        this.definition = def;
        this.bossId     = def.id;

        this.setCustomName(TextUtil.parseColorCodes(def.displayName));
        this.setCustomNameVisible(true);

        Objects.requireNonNull(getAttributeInstance(EntityAttributes.MAX_HEALTH))
               .setBaseValue(def.health);
        setHealth(def.health);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.ARMOR))
               .setBaseValue(def.armor);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.MOVEMENT_SPEED))
               .setBaseValue(def.speed);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE))
               .setBaseValue(def.knockbackResistance);
        Objects.requireNonNull(getAttributeInstance(EntityAttributes.ATTACK_DAMAGE))
               .setBaseValue(def.attackDamage);

        Text barName = TextUtil.parseColorCodes(def.displayName);
        bossBar.setName(barName);
        try { bossBar.setColor(BossBar.Color.valueOf(def.bossBar.color.toUpperCase())); }
        catch (IllegalArgumentException ignored) {}
        try { bossBar.setStyle(BossBar.Style.valueOf(def.bossBar.overlay.toUpperCase())); }
        catch (IllegalArgumentException ignored) {}

        applyEquipment(def.equipment);

        this.phaseManager = new BossPhaseManager(this, def.phases);

        // Determine initial fight state from config.
        // If preFightDialogue is configured the boss starts INACTIVE (right-click to activate).
        if (def.preFightDialogue != null && !def.preFightDialogue.isEmpty()) {
            this.bossState = BossState.INACTIVE;
            // Build goals for phase 0 so the manager is initialised, then immediately
            // clear them — the boss must not fight while INACTIVE.
            this.phaseManager.transitionToPhase(0);
            clearGoalsForInactive();
        } else {
            this.bossState = BossState.ACTIVE;
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
        if (entry == null) return;

        ItemStack stack;

        // Fiw Tools integration: toolId takes precedence over item/nbt.
        if (entry.toolId != null && !entry.toolId.isEmpty()) {
            var server = (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) ? sw.getServer() : null;
            stack = com.fiw.fiw_bosses.integration.FiwToolsBridge.getItemStack(entry.toolId, server, 1);
            if (stack == null || stack.isEmpty()) {
                if (com.fiw.fiw_bosses.integration.FiwToolsBridge.isPresent()) {
                    FiwBosses.LOGGER.warn("Unknown Fiw Tools item id in equipment slot {}: {}", slot, entry.toolId);
                }
                return;
            }
        } else {
            if (entry.item == null) return;
            Identifier itemId = Identifier.tryParse(entry.item);
            if (itemId == null) return;
            var item = Registries.ITEM.get(itemId);
            if (item == null) {
                FiwBosses.LOGGER.warn("Unknown item ID in equipment slot {}: {}", slot, entry.item);
                return;
            }
            stack = new ItemStack(item);
            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try {
                    NbtCompound parsed = StringNbtReader.readCompound(entry.nbt);
                    LegacyNbtToComponents.apply(stack, parsed, this.getRegistryManager());
                } catch (Exception e) {
                    FiwBosses.LOGGER.warn("Failed to parse NBT for equipment slot {}: {}", slot, e.getMessage());
                }
            }
        }

        this.equipStack(slot, stack);
    }

    @Override
    protected void initGoals() {
        // Goals are set dynamically by BossPhaseManager.
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void mobTick(ServerWorld world) {
        // Drain deferred goal actions before goalSelector.tick().
        Runnable action;
        while ((action = pendingGoalActions.poll()) != null) {
            action.run();
        }

        super.mobTick(world);

        tickBossBar();
        tickAggroSwitch();
        tickStrafing();
        tickIdleSystem();
        tickDialogueSystem();

        if (phaseManager != null) {
            phaseManager.tick();
        }
    }

    protected boolean showBossBar() { return true; }

    private void tickBossBar() {
        if (!showBossBar()) {
            bossBar.clearPlayers();
            return;
        }
        // Boss bar is only visible while the boss is actively fighting or dying.
        if (bossState == BossState.INACTIVE || bossState == BossState.PRE_FIGHT) {
            bossBar.clearPlayers();
            setTarget(null);
            return;
        }
        bossBar.setPercent(getHealth() / getMaxHealth());
        // Rebuild the player set every tick: clear first to automatically evict
        // dead / spectator / out-of-range players (including respawned entities).
        bossBar.clearPlayers();
        if (getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            for (ServerPlayerEntity sp : sw.getPlayers()) {
                if (sp.isAlive() && !sp.isSpectator()
                        && sp.squaredDistanceTo(this) <= 64 * 64) {
                    bossBar.addPlayer(sp);
                }
            }
        }
    }

    // ── Activation (right-click) ─────────────────────────────────────────────

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getEntityWorld().isClient() && bossState == BossState.INACTIVE) {
            startPreFightSequence();
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    private void startPreFightSequence() {
        bossState     = BossState.PRE_FIGHT;
        dialogueTimer = 0;
        dialogueLine  = 0;
        // Goals are already cleared from applyDefinition / NBT load.
    }

    private void activateBoss() {
        bossState = BossState.ACTIVE;
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

        // Stop all ability goals — boss just stands there talking.
        scheduleGoalAction(() -> {
            var goals = goalSelector.getGoals().stream()
                    .map(PrioritizedGoal::getGoal).collect(Collectors.toList());
            goals.forEach(goalSelector::remove);
            var targets = targetSelector.getGoals().stream()
                    .map(PrioritizedGoal::getGoal).collect(Collectors.toList());
            targets.forEach(targetSelector::remove);
            goalSelector.add(0, new SwimGoal(this));
            goalSelector.add(9, new LookAroundGoal(this));
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
                        // Must set state to ACTIVE before kill() — our damage() override
                        // blocks all damage while in PRE_DEATH, which would prevent death.
                        bossState = BossState.ACTIVE;
                        setInvulnerable(false);
                        this.kill((ServerWorld) getEntityWorld());
                    }
            );
        }
    }

    /**
     * Sends lines[dialogueLine] once per delay ticks, then calls onFinished
     * after all lines + one final delay.
     */
    private void tickLinedDialogue(List<String> lines, int delay, Runnable onFinished) {
        if (lines.isEmpty()) { onFinished.run(); return; }
        dialogueTimer++;
        // Send next line when enough ticks have elapsed since last send
        if (dialogueLine < lines.size() && dialogueTimer > dialogueLine * delay) {
            sendDialogueLine(lines.get(dialogueLine));
            dialogueLine++;
        }
        // After last line wait one more delay, then finish
        if (dialogueLine >= lines.size() && dialogueTimer > dialogueLine * delay) {
            onFinished.run();
        }
    }

    private void sendDialogueLine(String line) {
        Text text = TextUtil.parseColorCodes(line);
        for (var player : getEntityWorld().getPlayers()) {
            if (player instanceof ServerPlayerEntity sp
                    && sp.squaredDistanceTo(this) <= 80 * 80) {
                sp.sendMessage(text, false);
            }
        }
    }

    // ── Damage ────────────────────────────────────────────────────────────────

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Fully immune while inactive / mid-dialogue
        if (bossState == BossState.INACTIVE
                || bossState == BossState.PRE_FIGHT
                || bossState == BossState.PRE_DEATH) {
            return false;
        }

        // Immune to own minions
        Entity attacker = source.getAttacker();
        if (attacker != null && isMinion(attacker)) return false;

        if (damageReduction > 0) amount *= (1.0f - damageReduction);

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

        // Track last hit for GuardianShieldGoal
        lastDamageTick     = world.getTime();
        lastDamageAttacker = attacker;

        boolean result = super.damage(world, source, amount);

        if (result) idleTimer = 0;

        // Revenge aggro switch
        if (result && attacker instanceof PlayerEntity playerAttacker) {
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
    public boolean tryAttack(ServerWorld world, Entity target) {
        boolean result = super.tryAttack(world, target);
        if (result && markedTarget != null && markDamageBonus > 0
                && target.getUuid().equals(markedTarget)
                && target instanceof LivingEntity le) {
            le.damage(world, getDamageSources().magic(), markDamageBonus);
        }
        return result;
    }

    // ── Death / removal ───────────────────────────────────────────────────────

    @Override
    public void checkDespawn() { /* bosses never auto-despawn */ }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getEntityWorld().isClient() && definition != null) {
            BossLootHandler.dropLoot(this, definition);
        }
        bossBar.clearPlayers();
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        bossBar.clearPlayers();
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        writeBossCustomData(view);
    }

    /** Hook so {@link MinionEntity} can replace boss-specific persistence with its own. */
    protected void writeBossCustomData(WriteView view) {
        if (bossId != null) view.putString("BossId", bossId);
        if (phaseManager != null && phaseManager.getCurrentPhaseIndex() >= 0)
            view.putInt("BossPhase", phaseManager.getCurrentPhaseIndex());
        view.putString("BossState", bossState.name());
        view.putBoolean("PreDeathTriggered", preDeathTriggered);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view); // Restores vanilla fields (Health, etc.)
        readBossCustomData(view);
    }

    /** Hook so {@link MinionEntity} can replace boss-specific persistence with its own. */
    protected void readBossCustomData(ReadView view) {
        var bossIdOpt = view.getOptionalString("BossId");
        if (bossIdOpt.isEmpty()) return;

        this.bossId = bossIdOpt.get();
        BossDefinition def = BossConfigLoader.getDefinition(bossId);
        if (def == null) {
            FiwBosses.LOGGER.warn("Boss definition '{}' not found, entity will be removed", bossId);
            this.discard();
            return;
        }

        // Capture real HP before applyDefinition resets it to the definition's max.
        float savedHealth = getHealth();

        applyDefinition(def);

        // Restore the actual saved HP — applyDefinition calls setHealth(def.health).
        if (savedHealth > 0 && savedHealth <= getMaxHealth()) {
            setHealth(savedHealth);
        }

        // Restore saved phase silently (no transition messages or effects).
        int savedPhase = view.getInt("BossPhase", -1);
        if (savedPhase > 0 && phaseManager != null) {
            phaseManager.restoreToPhase(savedPhase);
        }

        // Restore boss state (overrides what applyDefinition set).
        var stateOpt = view.getOptionalString("BossState");
        if (stateOpt.isPresent()) {
            BossState savedState;
            try { savedState = BossState.valueOf(stateOpt.get()); }
            catch (IllegalArgumentException e) { savedState = BossState.ACTIVE; }

            // PRE_FIGHT → INACTIVE on reload (don't resume mid-dialogue)
            // PRE_DEATH → ACTIVE on reload (boss survived the restart, let players finish it)
            if (savedState == BossState.PRE_FIGHT) savedState = BossState.INACTIVE;
            if (savedState == BossState.PRE_DEATH)  savedState = BossState.ACTIVE;
            this.bossState = savedState;
        }

        this.preDeathTriggered = view.getBoolean("PreDeathTriggered", false);

        // Ensure goals are cleared if boss reloaded in INACTIVE state.
        if (this.bossState == BossState.INACTIVE) clearGoalsForInactive();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Removes all goals/targets except SwimGoal so INACTIVE boss just stands there. */
    private void clearGoalsForInactive() {
        scheduleGoalAction(() -> {
            var goals = goalSelector.getGoals().stream()
                    .map(PrioritizedGoal::getGoal).collect(Collectors.toList());
            goals.forEach(goalSelector::remove);
            var targets = targetSelector.getGoals().stream()
                    .map(PrioritizedGoal::getGoal).collect(Collectors.toList());
            targets.forEach(targetSelector::remove);
            goalSelector.add(0, new SwimGoal(this));
        });
    }

    private void tickAggroSwitch() {
        if (aggroSwitchTimer > 0) { aggroSwitchTimer--; return; }
        aggroSwitchTimer = AGGRO_SWITCH_MIN + getRandom().nextInt(AGGRO_SWITCH_MAX - AGGRO_SWITCH_MIN);

        LivingEntity current = getTarget();
        List<PlayerEntity> nearbyPlayers = getEntityWorld().getEntitiesByClass(
                PlayerEntity.class, getBoundingBox().expand(48),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (nearbyPlayers.size() <= 1) return;
        if (getRandom().nextFloat() < 0.4f) {
            if (current instanceof PlayerEntity) nearbyPlayers.remove(current);
            if (!nearbyPlayers.isEmpty())
                setTarget(nearbyPlayers.get(getRandom().nextInt(nearbyPlayers.size())));
        }
    }

    private void tickStrafing() {
        if (bossState != BossState.ACTIVE) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        boolean abilityHoldingMove = goalSelector.getGoals().stream().anyMatch(pg ->
                pg.isRunning()
                && pg.getGoal().getControls().contains(Goal.Control.MOVE)
                && !(pg.getGoal() instanceof MeleeAttackGoal)
                && !(pg.getGoal() instanceof WanderAroundFarGoal));
        if (abilityHoldingMove) { strafeTimer = 0; return; }

        double dist = distanceTo(target);
        if (dist < 7.0 && dist > 2.0) {
            strafeTimer++;
            if (strafeTimer % 30 == 0 && getRandom().nextFloat() < 0.5f) strafeDir *= -1;
            if (strafeTimer % 2 == 0) {
                getMoveControl().strafeTo(-0.3f, strafeDir * 0.6f);
                getLookControl().lookAt(target, 30.0f, 30.0f);
            }
        } else {
            strafeTimer = 0;
        }
    }

    // ── Idle despawn / heal ───────────────────────────────────────────────────

    private int idleTimer      = 0;
    private int idleHealTimer  = 0;

    private void tickIdleSystem() {
        if (definition == null || definition.idleTimeout <= 0) return;
        if (bossState != BossState.ACTIVE) return;

        boolean playerNearby = !getEntityWorld().getEntitiesByClass(
                PlayerEntity.class, getBoundingBox().expand(64),
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
                    setHealth(Math.min(getHealth() + definition.idleHealAmount, getMaxHealth()));
            }
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    // TODO 1.21.11 port: isImmuneToExplosion() was renamed/removed. Re-implement
    // explosion immunity via isInvulnerableTo(ServerWorld, DamageSource) when
    // verifying behaviour in an IDE. Boss still takes 0 damage via damage()
    // override during PRE_DEATH so loss of vanilla pushback only.
    public boolean isImmuneToExplosion() { return true; }

    // ── Minion management ────────────────────────────────────────────────────

    public void registerMinion(UUID minionUuid) { minionUuids.add(minionUuid); }
    public boolean isMinion(Entity entity) { return minionUuids.contains(entity.getUuid()); }
    public Set<UUID> getMinionUuids() { return minionUuids; }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getBossId() { return bossId; }
    public BossDefinition getDefinition() { return definition; }
    public BossPhaseManager getPhaseManager() { return phaseManager; }
    public net.minecraft.entity.ai.goal.GoalSelector getGoalSelector() { return this.goalSelector; }
    public net.minecraft.entity.ai.goal.GoalSelector getTargetSelector() { return this.targetSelector; }
    public BossState getBossState() { return bossState; }
    public boolean isActive() { return bossState == BossState.ACTIVE; }

    // Damage-tracking accessors (used by GuardianShieldGoal)
    public long   getLastDamageTick()     { return lastDamageTick; }
    public Entity getLastDamageAttacker() { return lastDamageAttacker; }

    // Mark-target accessors (used by DetectMarkGoal)
    public void setMarkTarget(UUID uuid, float bonus) { markedTarget = uuid; markDamageBonus = bonus; }
    public void clearMarkTarget() { markedTarget = null; markDamageBonus = 0f; }
    public UUID getMarkedTarget() { return markedTarget; }

    // ── Deferred goal-selector actions ───────────────────────────────────────

    private final java.util.Queue<Runnable> pendingGoalActions = new java.util.ArrayDeque<>();

    public void scheduleGoalAction(Runnable action) { pendingGoalActions.add(action); }

    // ── Shield damage reduction ───────────────────────────────────────────────

    private float damageReduction = 0.0f;
    public void setDamageReduction(float reduction) { this.damageReduction = reduction; }
    public float getDamageReduction() { return damageReduction; }
}
