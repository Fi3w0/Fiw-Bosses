package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.MinionEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class MinionEntityRenderer
        extends BipedEntityRenderer<MinionEntity, MinionEntityRenderState, BipedEntityModel<MinionEntityRenderState>> {

    private static final Identifier DEFAULT_TEXTURE =
            Identifier.of(FiwBosses.MOD_ID, "textures/entity/boss_default.png");

    private final BipedEntityModel<MinionEntityRenderState> classicModel;
    private final BipedEntityModel<MinionEntityRenderState> slimModel;

    public MinionEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        this.classicModel = this.model;
        // See BossEntityRenderer for why PLAYER_SLIM can't be referenced directly.
        this.slimModel = new BipedEntityModel<>(
                TexturedModelData.of(
                        PlayerEntityModel.getTexturedModelData(Dilation.NONE, true), 64, 64
                ).createModel());
        this.addFeature(new HeldItemFeatureRenderer<>(this));
        // See BossEntityRenderer for notes on the PLAYER_EQUIPMENT vs PLAYER_SLIM choice.
        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                EntityModelLayers.PLAYER_EQUIPMENT.map(layer ->
                        new BipedEntityModel<MinionEntityRenderState>(ctx.getPart(layer))),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public MinionEntityRenderState createRenderState() {
        return new MinionEntityRenderState();
    }

    @Override
    public void updateRenderState(MinionEntity entity, MinionEntityRenderState state, float tickProgress) {
        super.updateRenderState(entity, state, tickProgress);
        state.entityId = entity.getId();
        state.slim     = ClientSkinManager.isSlim(entity.getId());
        this.model = state.slim ? slimModel : classicModel;
    }

    @Override
    public Identifier getTexture(MinionEntityRenderState state) {
        Identifier skin = ClientSkinManager.getSkinTexture(state.entityId);
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
