package com.fiw.fiw_bosses.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;

@Environment(EnvType.CLIENT)
public class MinionEntityRenderState extends BipedEntityRenderState {
    public int entityId;
    public boolean slim;
}
