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

final class DisguiseRenderHelper {
    private DisguiseRenderHelper() {}

    static EntityRenderState createState(BossEntity source, float partialTick, EntityRenderDispatcher dispatcher) {
        String disguiseId = source.getDisguiseEntity();
        if (disguiseId == null || disguiseId.isBlank()) {
            disguiseId = ClientDisguiseManager.getDisguise(source.getId());
        }
        if (disguiseId == null || disguiseId.isBlank()) {
            return null;
        }

        Identifier id = Identifier.tryParse(disguiseId);
        if (id == null) {
            return null;
        }
        if (FiwBossesCore.MOD_ID.equals(id.getNamespace())) {
            return null;
        }

        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty()) {
            return null;
        }
        if (typeOpt.get() == source.getType()) {
            return null;
        }

        Entity fake = typeOpt.get().create(source.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (fake == null) {
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
            EntityRenderState rs = dispatcher.extractEntity(fake, partialTick);
            // The throwaway entity never walks, so copy the boss's real limb-swing
            // (and body orientation) onto the extracted state so the disguise animates.
            if (rs instanceof LivingEntityRenderState lrs) {
                lrs.walkAnimationPos = source.walkAnimation.position(partialTick);
                lrs.walkAnimationSpeed = source.walkAnimation.speed(partialTick);
            }
            if (rs instanceof ArmedEntityRenderState ars) {
                ars.attackTime = source.getAttackAnim(partialTick);
            }
            return rs;
        } catch (Throwable t) {
            return null;
        }
    }
}
