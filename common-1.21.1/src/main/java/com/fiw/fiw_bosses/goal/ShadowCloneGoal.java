package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.config.SkinDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.skin.SkinData;
import com.fiw.fiw_bosses.util.Colors;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spawns fake copies of the boss that look the same (it mirrors the boss's disguise/skin),
 * follow the boss, have 1 HP and deal 1 damage by default. When a clone dies it bursts into
 * black smoke and can optionally apply a debuff to whoever killed it.
 */
public class ShadowCloneGoal extends Goal {

    private static final DustParticleOptions BLACK_SMOKE =
            new DustParticleOptions(Colors.rgb(0.05f, 0.05f, 0.05f), 1.6f);

    private final BossEntity boss;
    private final int count;
    private final float cloneHealth;
    private final float cloneDamage;
    private final Holder<MobEffect> debuff;
    private final int debuffDuration;
    private final int debuffAmplifier;
    private final String taunt;
    private final int cooldown;

    private int cooldownTimer;
    private final Map<UUID, Vec3> clonePositions = new HashMap<>();

    public ShadowCloneGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.count = Math.max(1, params.has("count") ? params.get("count").getAsInt() : 3);
        this.cloneHealth = params.has("health") ? params.get("health").getAsFloat() : 1.0f;
        this.cloneDamage = params.has("damage") ? params.get("damage").getAsFloat() : 1.0f;
        this.debuffDuration = (params.has("debuffSeconds") ? params.get("debuffSeconds").getAsInt() : 5) * 20;
        this.debuffAmplifier = params.has("debuffAmplifier") ? params.get("debuffAmplifier").getAsInt() : 0;
        this.taunt = params.has("taunt") ? params.get("taunt").getAsString() : null;
        this.cooldown = cooldownTicks;

        Holder<MobEffect> resolved = null;
        if (params.has("debuff") && !params.get("debuff").getAsString().isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(params.get("debuff").getAsString());
            if (id != null) {
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
                if (holder.isPresent()) resolved = holder.get();
            }
        }
        this.debuff = resolved;

        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        // Only summon a fresh wave when no clones from a previous wave remain.
        return target != null && target.isAlive() && clonePositions.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return !clonePositions.isEmpty();
    }

    @Override
    public void start() {
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        MinionDefinition def = buildCloneDefinition();
        SkinData bossSkin = SkinCache.getSkin(boss.getBossId());
        if (bossSkin != null) {
            SkinCache.cacheSkin("minion_" + def.id, bossSkin);
        }

        for (int i = 0; i < count; i++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double dist = 1.5 + boss.getRandom().nextDouble() * 2.0;
            double x = boss.getX() + Math.cos(angle) * dist;
            double z = boss.getZ() + Math.sin(angle) * dist;
            double y = boss.getY();

            MinionEntity clone = ModRefs.MINION.create(level);
            if (clone == null) continue;
            clone.moveTo(x, y, z, boss.getRandom().nextFloat() * 360, 0);
            clone.applyMinionDefinition(def, boss);
            clone.setPersistenceRequired();
            if (boss.getTarget() != null) clone.setTarget(boss.getTarget());

            level.addFreshEntity(clone);
            boss.registerMinion(clone.getUUID());
            clonePositions.put(clone.getUUID(), clone.position());

            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.6, z, 12, 0.25, 0.5, 0.25, 0.02);
            level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.6, z, 4, 0.2, 0.3, 0.2, 0.0);
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.3f, 0.6f);
        sendTaunt(level);
    }

    @Override
    public void tick() {
        if (boss.level().isClientSide() || clonePositions.isEmpty()) return;
        ServerLevel level = (ServerLevel) boss.level();
        LivingEntity target = boss.getTarget();

        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, Vec3> entry : clonePositions.entrySet()) {
            Entity e = level.getEntity(entry.getKey());
            if (e instanceof LivingEntity clone && clone.isAlive()) {
                entry.setValue(clone.position());
                if (target != null && (clone instanceof MinionEntity m) && (m.getTarget() == null || !m.getTarget().isAlive())) {
                    m.setTarget(target);
                }
            } else {
                Vec3 pos = (e != null) ? e.position() : entry.getValue();
                LivingEntity killer = (e instanceof LivingEntity dyingClone) ? dyingClone.getLastHurtByMob() : null;
                onCloneDeath(level, pos, killer);
                dead.add(entry.getKey());
            }
        }
        dead.forEach(clonePositions::remove);
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        clonePositions.clear();
    }

    private void onCloneDeath(ServerLevel level, Vec3 pos, LivingEntity killer) {
        level.sendParticles(BLACK_SMOKE, pos.x, pos.y + 0.9, pos.z, 30, 0.4, 0.7, 0.4, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.7, pos.z, 18, 0.35, 0.6, 0.35, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 0.8, pos.z, 8, 0.3, 0.5, 0.3, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FOX_AGGRO, SoundSource.HOSTILE, 0.8f, 0.5f);

        if (debuff != null && killer instanceof Player player) {
            player.addEffect(new MobEffectInstance(debuff, debuffDuration, debuffAmplifier, false, true));
        }
    }

    private MinionDefinition buildCloneDefinition() {
        BossDefinition bd = boss.getDefinition();
        String bossId = boss.getBossId() != null ? boss.getBossId() : "boss";

        MinionDefinition def = new MinionDefinition();
        def.id = "shadow_clone_" + bossId;
        def.displayName = bd != null ? bd.displayName : null;
        def.health = cloneHealth;
        def.armor = 0.0f;
        def.speed = bd != null ? bd.speed : 0.3f;
        def.knockbackResistance = 0.0f;
        def.attackDamage = cloneDamage;
        def.movement = "follow_boss";
        def.equipment = bd != null ? bd.equipment : null;
        def.skin = bd != null && bd.skin != null ? bd.skin : new SkinDefinition();

        String disguise = boss.getDisguiseEntity();
        def.renderEntity = (disguise != null && !disguise.isBlank()) ? disguise : null;

        return def;
    }

    private void sendTaunt(ServerLevel level) {
        if (taunt == null) return;
        var bossName = boss.getCustomName();
        Component tauntText = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(taunt));
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= 48 * 48) {
                player.sendSystemMessage(tauntText);
            }
        }
    }
}
