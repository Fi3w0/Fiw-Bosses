package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.client.model.BossEntityModel;
import com.fiw.fiw_bosses.client.skin.ClientSkinManager;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class BossEntityRenderer extends BipedEntityRenderer<BossEntity, BossEntityModel> {

    private static final Identifier DEFAULT_TEXTURE = new Identifier(FiwBosses.MOD_ID, "textures/entity/boss_default.png");

    public BossEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BossEntityModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);

        // Add armor rendering
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));

        // Add held item rendering
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(BossEntity entity) {
        Identifier skin = ClientSkinManager.getSkinTexture(entity.getId());
        if (skin != null) {
            return skin;
        }
        return DEFAULT_TEXTURE;
    }
}
