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
            // The disguise is drawn from this throwaway mob, so it needs the boss's `scale`.
            var scale = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(source.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
            }
            living.yBodyRot = source.yBodyRot;
            living.yBodyRotO = source.yBodyRotO;
            living.yHeadRot = source.yHeadRot;
            living.yHeadRotO = source.yHeadRotO;
            living.hurtTime = source.hurtTime;
            living.deathTime = source.deathTime;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                living.setItemSlot(slot, source.getItemBySlot(slot));
            }
            copyAnimation(source, living);
        }

        return fake;
    }

    /**
     * The throwaway entity never walks or swings, so without this a disguised boss glides with
     * frozen limbs. WalkAnimationState only exposes setSpeed/update, so replay the boss's state:
     * update(f, g) sets speedOld = speed, speed += (f - speed) * g, position += speed.
     */
    private static void copyAnimation(BossEntity source, LivingEntity fake) {
        float position = source.walkAnimation.position();
        float speed = source.walkAnimation.speed();
        float speedOld = source.walkAnimation.speed(0.0f);
        fake.walkAnimation.setSpeed(position - speed);
        fake.walkAnimation.update(0.0f, 0.0f);   // position = position - speed
        fake.walkAnimation.setSpeed(speedOld);
        fake.walkAnimation.update(speed, 1.0f);  // speedOld, speed and position now match
        fake.attackAnim = source.attackAnim;
        fake.oAttackAnim = source.oAttackAnim;
        fake.swinging = source.swinging;
        fake.swingingArm = source.swingingArm;
        fake.swingTime = source.swingTime;
    }
}
