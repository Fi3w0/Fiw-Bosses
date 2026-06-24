package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BossEntityRenderer
        extends HumanoidMobRenderer<BossEntity, BossEntityRenderState, HumanoidModel<BossEntityRenderState>> {

    private static final Identifier DEFAULT_TEXTURE =
            Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "textures/entity/boss_default.png");
    private static final Set<Integer> LOGGED_SUBMIT = ConcurrentHashMap.newKeySet();
    private final HumanoidModel<BossEntityRenderState> classicModel;
    private final HumanoidModel<BossEntityRenderState> slimModel;

    public BossEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.classicModel = this.model;
        this.slimModel = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM));
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public BossEntityRenderState createRenderState() {
        return new BossEntityRenderState();
    }

    @Override
    public void extractRenderState(BossEntity entity, BossEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entityId = entity.getId();
        state.slim = ClientSkinManager.isSlim(entity.getId());
        state.disguiseState = DisguiseRenderHelper.createState(entity, partialTick, this.entityRenderDispatcher);
        if (state.disguiseState != null) {
            hidePlayerModel(state);
        }
    }

    private static void hidePlayerModel(BossEntityRenderState state) {
        state.isInvisible = true;
        state.isInvisibleToPlayer = true;
    }

    @Override
    public void submit(BossEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.disguiseState != null) {
            if (LOGGED_SUBMIT.add(state.entityId)) {
                FiwBossesCore.LOGGER.info("Boss renderer submitting disguise entity={} stateType={} entityType={} at {},{},{}",
                        state.entityId,
                        state.disguiseState.getClass().getName(),
                        state.disguiseState.entityType,
                        state.x, state.y, state.z);
            }
            this.entityRenderDispatcher.submit(state.disguiseState, cameraState, 0.0, 0.0, 0.0, poseStack, collector);
            return;
        }
        if (LOGGED_SUBMIT.add(state.entityId)) {
            FiwBossesCore.LOGGER.info("Boss renderer using player fallback entity={} hasClientDisguise={} skin={}",
                    state.entityId,
                    ClientDisguiseManager.hasDisguise(state.entityId),
                    ClientSkinManager.getSkinTexture(state.entityId));
        }
        this.model = state.slim ? slimModel : classicModel;
        super.submit(state, poseStack, collector, cameraState);
    }

    public void render(BossEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        submit(state, poseStack, collector, cameraState);
    }

    @Override
    public Identifier getTextureLocation(BossEntityRenderState state) {
        int entityId = state.entityId;
        Identifier skin = ClientSkinManager.getSkinTexture(entityId);
        if (skin != null) {
            ClientSkinManager.logTextureUseOnce(entityId, skin);
        }
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
