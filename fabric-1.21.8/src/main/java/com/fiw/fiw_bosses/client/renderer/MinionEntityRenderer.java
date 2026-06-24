package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class MinionEntityRenderer
        extends HumanoidMobRenderer<MinionEntity, MinionEntityRenderState, HumanoidModel<MinionEntityRenderState>> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "textures/entity/boss_default.png");

    public MinionEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public MinionEntityRenderState createRenderState() {
        return new MinionEntityRenderState();
    }

    @Override
    public void extractRenderState(MinionEntity entity, MinionEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entityId = entity.getId();
        state.slim = ClientSkinManager.isSlim(entity.getId());
        state.disguiseState = DisguiseRenderHelper.createState(entity, partialTick, this.entityRenderDispatcher);
    }

    @Override
    public void render(MinionEntityRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.disguiseState != null) {
            this.entityRenderDispatcher.render(state.disguiseState, 0.0, 0.0, 0.0, poseStack, buffer, packedLight);
            return;
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MinionEntityRenderState state) {
        ResourceLocation skin = ClientSkinManager.getSkinTexture(state.entityId);
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
