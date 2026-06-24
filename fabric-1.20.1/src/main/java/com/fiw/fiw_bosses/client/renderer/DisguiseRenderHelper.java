package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

final class DisguiseRenderHelper {
    private DisguiseRenderHelper() {}

    static boolean render(BossEntity source, float entityYaw, float partialTick, PoseStack poseStack,
                          MultiBufferSource buffer, int packedLight, EntityRenderDispatcher dispatcher) {
        Entity fake = createFake(source);
        if (fake == null) return false;

        dispatcher.render(fake, 0.0, 0.0, 0.0, entityYaw, partialTick, poseStack, buffer, packedLight);
        return true;
    }

    private static Entity createFake(BossEntity source) {
        String disguiseId = source.getDisguiseEntity();
        if (disguiseId == null || disguiseId.isBlank()) {
            disguiseId = ClientDisguiseManager.getDisguise(source.getId());
        }
        if (disguiseId == null || disguiseId.isBlank()) return null;

        ResourceLocation id = ResourceLocation.tryParse(disguiseId);
        if (id == null) return null;
        if (FiwBossesCore.MOD_ID.equals(id.getNamespace())) return null;

        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty() || typeOpt.get() == source.getType()) return null;

        Entity fake = typeOpt.get().create(source.level());
        if (fake == null) return null;

        fake.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
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

        return fake;
    }
}
