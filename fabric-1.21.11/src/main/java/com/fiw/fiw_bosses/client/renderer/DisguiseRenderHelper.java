package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class DisguiseRenderHelper {
    private DisguiseRenderHelper() {}
    private static final Set<Integer> LOGGED = ConcurrentHashMap.newKeySet();

    static EntityRenderState createState(BossEntity source, float partialTick, EntityRenderDispatcher dispatcher) {
        String disguiseId = source.getDisguiseEntity();
        if (disguiseId == null || disguiseId.isBlank()) {
            disguiseId = ClientDisguiseManager.getDisguise(source.getId());
        }
        if (disguiseId == null || disguiseId.isBlank()) {
            logOnce(source, "no disguise on entityData/client map");
            return null;
        }

        Identifier id = Identifier.tryParse(disguiseId);
        if (id == null) {
            logOnce(source, "invalid disguise id '" + disguiseId + "'");
            return null;
        }
        if (FiwBossesCore.MOD_ID.equals(id.getNamespace())) {
            logOnce(source, "ignored FIW entity disguise '" + disguiseId + "'");
            return null;
        }

        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty()) {
            logOnce(source, "unknown entity type '" + disguiseId + "'");
            return null;
        }
        if (typeOpt.get() == source.getType()) {
            logOnce(source, "disguise matched source type '" + disguiseId + "'");
            return null;
        }

        Entity fake = typeOpt.get().create(source.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (fake == null) {
            logOnce(source, "failed to create fake entity '" + disguiseId + "'");
            return null;
        }

        fake.snapTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
        fake.tickCount = source.tickCount;
        fake.setCustomName(source.getCustomName());
        fake.setCustomNameVisible(source.isCustomNameVisible());
        fake.setInvisible(source.isInvisible());

        if (fake instanceof LivingEntity living) {
            living.yBodyRot = source.yBodyRot;
            living.yBodyRotO = source.yBodyRotO;
            living.yHeadRot = source.yHeadRot;
            living.yHeadRotO = source.yHeadRotO;
            living.hurtTime = source.hurtTime;
            living.deathTime = source.deathTime;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                living.setItemSlot(slot, source.getItemBySlot(slot));
            }
        }

        try {
            EntityRenderState state = dispatcher.extractEntity(fake, partialTick);
            // The throwaway entity never walks or swings, so copy the boss's
            // current animation values onto the extracted disguise state.
            if (state instanceof LivingEntityRenderState livingState) {
                livingState.walkAnimationPos = source.walkAnimation.position(partialTick);
                livingState.walkAnimationSpeed = source.walkAnimation.speed(partialTick);
            }
            if (state instanceof ArmedEntityRenderState armedState) {
                armedState.attackTime = source.getAttackAnim(partialTick);
            }
            if (LOGGED.add(source.getId())) {
                FiwBossesCore.LOGGER.info("Created disguise render state entity={} bossId={} disguise={} stateType={} entityType={}",
                        source.getId(), source.getBossId(), disguiseId, state.getClass().getName(), state.entityType);
            }
            return state;
        } catch (Throwable t) {
            logOnce(source, "extractEntity failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    private static void logOnce(BossEntity source, String reason) {
        if (LOGGED.add(source.getId())) {
            FiwBossesCore.LOGGER.info("No disguise render state entity={} bossId={} reason={} entityData='{}' clientMap='{}'",
                    source.getId(), source.getBossId(), reason, source.getDisguiseEntity(),
                    ClientDisguiseManager.getDisguise(source.getId()));
        }
    }
}
