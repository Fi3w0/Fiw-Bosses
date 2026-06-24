package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

public class BossEntityRenderer
        extends HumanoidMobRenderer<BossEntity, BossEntityRenderState, HumanoidModel<BossEntityRenderState>> {

    private static final Identifier DEFAULT_TEXTURE =
            Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "textures/entity/boss_default.png");

    public BossEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
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
    }

    @Override
    public Identifier getTextureLocation(BossEntityRenderState state) {
        Identifier skin = ClientSkinManager.getSkinTexture(state.entityId);
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
