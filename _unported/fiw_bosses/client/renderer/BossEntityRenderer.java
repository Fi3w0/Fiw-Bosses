package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
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
public class BossEntityRenderer extends BipedEntityRenderer<BossEntity, PlayerEntityModel<BossEntity>> {

    private static final Identifier DEFAULT_TEXTURE =
            new Identifier(FiwBosses.MOD_ID, "textures/entity/boss_default.png");

    /** Classic / Steve model (4-px wide arms). Passed to super as the default. */
    private final PlayerEntityModel<BossEntity> classicModel;
    /** Slim / Alex model (3-px wide arms). Used when ClientSkinManager.isSlim() returns true. */
    private final PlayerEntityModel<BossEntity> slimModel;

    public BossEntityRenderer(EntityRendererFactory.Context ctx) {
        // Use the classic (wide-arm) player model as the default; this gives us full
        // overlay rendering (hat, jacket, sleeves, pants) via PlayerEntityModel.
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.classicModel = this.model;
        this.slimModel    = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);

        // Armor rendering
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));

        // Held-item rendering
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    /**
     * Swap to the correct arm-width model before each render so that slim skins
     * (Alex model) have properly mapped arm UV coordinates.
     */
    @Override
    public void render(BossEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = ClientSkinManager.isSlim(entity.getId()) ? slimModel : classicModel;
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(BossEntity entity) {
        Identifier skin = ClientSkinManager.getSkinTexture(entity.getId());
        return skin != null ? skin : DEFAULT_TEXTURE;
    }
}
