package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.MinionEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class MinionEntityRenderer extends BipedEntityRenderer<MinionEntity, PlayerEntityModel<MinionEntity>> {

    private static final Identifier DEFAULT_TEXTURE =
            new Identifier(FiwBosses.MOD_ID, "textures/entity/boss_default.png");

    private final PlayerEntityModel<MinionEntity> classicModel;
    private final PlayerEntityModel<MinionEntity> slimModel;

    public MinionEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.classicModel = this.model;
        this.slimModel    = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);

        this.addFeature(new ArmorFeatureRenderer<>(this,
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));

        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public void render(MinionEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = ClientSkinManager.isSlim(entity.getId()) ? slimModel : classicModel;
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(MinionEntity entity) {
        Identifier skin = ClientSkinManager.getSkinTexture(entity.getId());
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
