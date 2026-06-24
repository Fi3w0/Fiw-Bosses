package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;

public class MinionEntityRenderer
        extends HumanoidMobRenderer<MinionEntity, MinionEntityRenderState, HumanoidModel<MinionEntityRenderState>> {

    private static final Identifier DEFAULT_TEXTURE =
            Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "textures/entity/boss_default.png");

    public MinionEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
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
    public void submit(MinionEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.disguiseState != null) {
            this.entityRenderDispatcher.submit(state.disguiseState, cameraState, state.x, state.y, state.z, poseStack, collector);
            return;
        }
        super.submit(state, poseStack, collector, cameraState);
    }

    @Override
    public Identifier getTextureLocation(MinionEntityRenderState state) {
        Identifier skin = ClientSkinManager.getSkinTexture(state.entityId);
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
