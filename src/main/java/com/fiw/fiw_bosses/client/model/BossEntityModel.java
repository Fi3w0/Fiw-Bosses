package com.fiw.fiw_bosses.client.model;

import com.fiw.fiw_bosses.entity.BossEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;

@Environment(EnvType.CLIENT)
public class BossEntityModel extends BipedEntityModel<BossEntity> {

    public BossEntityModel(ModelPart root) {
        super(root);
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = BipedEntityModel.getModelData(Dilation.NONE, 0.0f);
        // Standard player model proportions
        return TexturedModelData.of(modelData, 64, 64);
    }
}
