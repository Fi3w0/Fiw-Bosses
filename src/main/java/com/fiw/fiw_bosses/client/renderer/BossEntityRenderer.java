package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
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

/**
 * Renders {@link BossEntity} with the vanilla player skeleton + a server-supplied
 * skin texture looked up by entity id from {@link ClientSkinManager}.
 *
 * <p>1.21.11 uses a state-pattern renderer: per-frame data is captured into
 * {@link BossEntityRenderState} inside {@link #updateRenderState}, and the actual
 * draw call only reads the state. We swap {@code this.model} between the classic
 * (4-px arm) and slim (3-px arm) variants per render based on what the skin
 * manager reports for this entity id.
 */
@Environment(EnvType.CLIENT)
public class BossEntityRenderer
        extends BipedEntityRenderer<BossEntity, BossEntityRenderState, BipedEntityModel<BossEntityRenderState>> {

    private static final Identifier DEFAULT_TEXTURE =
            Identifier.of(FiwBosses.MOD_ID, "textures/entity/boss_default.png");

    private final BipedEntityModel<BossEntityRenderState> classicModel;
    private final BipedEntityModel<BossEntityRenderState> slimModel;

    public BossEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        this.classicModel = this.model;
        // EntityModelLayers.PLAYER_SLIM is declared twice (yarn maps two distinct fields
        // to the same name in 1.21.11), so we cannot reference it from Java source.
        // Bake the slim model from PlayerEntityModel's static model data instead.
        this.slimModel = new BipedEntityModel<>(
                TexturedModelData.of(
                        PlayerEntityModel.getTexturedModelData(Dilation.NONE, true), 64, 64
                ).createModel());
        this.addFeature(new HeldItemFeatureRenderer<>(this));

        // Armor (vanilla biped equipment layer). 1.21.11 has a duplicate-named PLAYER_SLIM
        // field for the equipment variant that's unreferenceable from Java source, so we
        // always use the classic PLAYER_EQUIPMENT layer — slim-armed bosses get slightly
        // wider arm armour, which is the same compromise other player-skin custom mobs make.
        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                EntityModelLayers.PLAYER_EQUIPMENT.map(layer ->
                        new BipedEntityModel<BossEntityRenderState>(ctx.getPart(layer))),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public BossEntityRenderState createRenderState() {
        return new BossEntityRenderState();
    }

    @Override
    public void updateRenderState(BossEntity entity, BossEntityRenderState state, float tickProgress) {
        super.updateRenderState(entity, state, tickProgress);
        state.entityId = entity.getId();
        state.slim     = ClientSkinManager.isSlim(entity.getId());
        // Swap model BEFORE the draw call so arm UVs match the skin variant.
        this.model = state.slim ? slimModel : classicModel;
    }

    @Override
    public Identifier getTexture(BossEntityRenderState state) {
        Identifier skin = ClientSkinManager.getSkinTexture(state.entityId);
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
