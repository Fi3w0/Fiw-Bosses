package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.config.MinionEntry;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.fiw.fiw_bosses.util.LegacyNbtToComponents;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        return boss.getTarget() != null && hasMinionsConfigured();
    }

    @Override
    public void start() {
        summonMinions();
        cooldownTimer = cooldown;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    private boolean hasMinionsConfigured() {
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        return phase != null && phase.minions != null && !phase.minions.isEmpty();
    }

    private void summonMinions() {
        if (boss.getEntityWorld().isClient()) return;
        ServerWorld world = (ServerWorld) boss.getEntityWorld();
        PhaseDefinition phase = boss.getPhaseManager().getCurrentPhase();
        if (phase == null || phase.minions == null) return;

        trackedMinions.removeIf(uuid -> {
            Entity e = world.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        boolean didSummon = false;

        for (MinionEntry minionDef : phase.minions) {
            long aliveCount = trackedMinions.stream()
                    .map(world::getEntity)
                    .filter(Objects::nonNull)
                    .filter(Entity::isAlive)
                    .count();

            if (aliveCount >= minionDef.maxAlive) continue;

            int toSpawn = Math.min(minionDef.count, minionDef.maxAlive - (int) aliveCount);

            for (int i = 0; i < toSpawn; i++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 1.5 + boss.getRandom().nextDouble() * minionDef.spawnRadius;
                double x = boss.getX() + Math.cos(angle) * dist;
                double z = boss.getZ() + Math.sin(angle) * dist;
                double y = boss.getY();

                Entity entity;

                if (minionDef.usesDefinition()) {
                    // New system: spawn a custom MinionEntity from minion definition
                    entity = spawnCustomMinion(world, minionDef, x, y, z);
                } else {
                    // Legacy: spawn a vanilla mob by entityType
                    entity = spawnVanillaMinion(world, minionDef, x, y, z);
                }

                if (entity != null) {
                    world.spawnEntity(entity);
                    trackedMinions.add(entity.getUuid());
                    boss.registerMinion(entity.getUuid());
                    didSummon = true;

                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, y + 0.5, z, 8, 0.3, 0.5, 0.3, 0.05);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            x, y + 0.3, z, 5, 0.2, 0.4, 0.2, 0.02);
                }
            }
        }

        if (didSummon) {
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 0.8f);

            String msg = tauntMessage != null ? tauntMessage : "&5Rise, my servants!";
            var bossName = boss.getCustomName();
            Text taunt = Text.literal("[").formatted(Formatting.DARK_GRAY)
                    .append(bossName != null ? bossName.copy() : Text.literal("Boss"))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                    .append(TextUtil.parseColorCodes(msg));
            for (var player : world.getPlayers()) {
                if (player.squaredDistanceTo(boss) <= 48 * 48) {
                    player.sendMessage(taunt, false);
                }
            }
        }
    }

    private Entity spawnCustomMinion(ServerWorld world, MinionEntry minionEntry, double x, double y, double z) {
        MinionDefinition def = MinionConfigLoader.getDefinition(minionEntry.minionId);
        if (def == null) return null;

        if (def.isCustom()) {
            // Custom skin minion — use MinionEntity
            MinionEntity minion = BossEntityRegistry.MINION_TYPE.create(world, SpawnReason.MOB_SUMMONED);
            if (minion == null) return null;
            minion.refreshPositionAndAngles(x, y, z, boss.getRandom().nextFloat() * 360, 0);
            minion.applyMinionDefinition(def, boss);
            minion.setPersistent();
            if (boss.getTarget() != null) {
                minion.setTarget(boss.getTarget());
            }
            return minion;
        } else {
            // Vanilla base entity with stat/equipment overrides from definition
            return spawnVanillaWithOverrides(world, def, x, y, z);
        }
    }

    private Entity spawnVanillaWithOverrides(ServerWorld world, MinionDefinition def, double x, double y, double z) {
        Identifier typeId = Identifier.tryParse(def.baseEntity);
        if (typeId == null) return null;

        Optional<EntityType<?>> entityTypeOpt = Registries.ENTITY_TYPE.getOptionalValue(typeId);
        if (entityTypeOpt.isEmpty()) return null;

        Entity entity = entityTypeOpt.get().create(world, SpawnReason.MOB_SUMMONED);
        if (entity == null) return null;

        entity.refreshPositionAndAngles(x, y, z, boss.getRandom().nextFloat() * 360, 0);
        if (entity instanceof MobEntity mob) {
            mob.initialize(world,
                    world.getLocalDifficulty(BlockPos.ofFloored(x, y, z)),
                    SpawnReason.MOB_SUMMONED, null);
            mob.setPersistent();

            // Override stats
            var healthAttr = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (healthAttr != null) { healthAttr.setBaseValue(def.health); mob.setHealth(def.health); }
            var armorAttr = mob.getAttributeInstance(EntityAttributes.ARMOR);
            if (armorAttr != null) armorAttr.setBaseValue(def.armor);
            var speedAttr = mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(def.speed);
            var dmgAttr = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
            if (dmgAttr != null) dmgAttr.setBaseValue(def.attackDamage);
            var kbAttr = mob.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) kbAttr.setBaseValue(def.knockbackResistance);

            // Custom name
            if (def.displayName != null) {
                mob.setCustomName(TextUtil.parseColorCodes(def.displayName));
                mob.setCustomNameVisible(true);
            }

            // Equipment
            if (def.equipment != null) {
                applyEquipmentToMob(mob, def);
            }

            if (boss.getTarget() != null) {
                mob.setTarget(boss.getTarget());
            }
        }
        return entity;
    }

    private void applyEquipmentToMob(MobEntity mob, MinionDefinition def) {
        if (def.equipment == null) return;
        setSlot(mob, EquipmentSlot.MAINHAND, def.equipment.mainHand);
        setSlot(mob, EquipmentSlot.OFFHAND, def.equipment.offHand);
        setSlot(mob, EquipmentSlot.HEAD, def.equipment.head);
        setSlot(mob, EquipmentSlot.CHEST, def.equipment.chest);
        setSlot(mob, EquipmentSlot.LEGS, def.equipment.legs);
        setSlot(mob, EquipmentSlot.FEET, def.equipment.feet);
    }

    private void setSlot(MobEntity mob, EquipmentSlot slot,
                         com.fiw.fiw_bosses.config.EquipmentEntry entry) {
        if (entry == null || entry.item == null) return;
        Identifier itemId = Identifier.tryParse(entry.item);
        if (itemId == null) return;
        var item = Registries.ITEM.get(itemId);
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        if (entry.nbt != null && !entry.nbt.isEmpty()) {
            try {
                NbtCompound tag = StringNbtReader.readCompound(entry.nbt);
                LegacyNbtToComponents.apply(stack, tag, mob.getRegistryManager());
            } catch (Exception ignored) {}
        }
        mob.equipStack(slot, stack);
    }

    private Entity spawnVanillaMinion(ServerWorld world, MinionEntry minionDef, double x, double y, double z) {
        Identifier typeId = Identifier.tryParse(minionDef.entityType);
        if (typeId == null) return null;

        Optional<EntityType<?>> entityTypeOpt = Registries.ENTITY_TYPE.getOptionalValue(typeId);
        if (entityTypeOpt.isEmpty()) return null;

        Entity entity = entityTypeOpt.get().create(world, SpawnReason.MOB_SUMMONED);
        if (entity == null) return null;

        entity.refreshPositionAndAngles(x, y, z, boss.getRandom().nextFloat() * 360, 0);
        if (entity instanceof MobEntity mob) {
            mob.initialize(world,
                    world.getLocalDifficulty(BlockPos.ofFloored(x, y, z)),
                    SpawnReason.MOB_SUMMONED, null);
            mob.setPersistent();
            if (boss.getTarget() != null) {
                mob.setTarget(boss.getTarget());
            }
        }
        return entity;
    }
}
