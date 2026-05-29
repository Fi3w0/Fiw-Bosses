package com.fiw.fiw_bosses.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;

/**
 * Render state for {@link com.fiw.fiw_bosses.entity.BossEntity}. Stores the entity
 * id (so the renderer can look up the dynamic skin from {@code ClientSkinManager}
 * without holding the live entity) and the slim-arm flag (so we can swap between
 * classic and slim {@code BipedEntityModel} variants per render).
 */
@Environment(EnvType.CLIENT)
public class BossEntityRenderState extends BipedEntityRenderState {
    public int entityId;
    public boolean slim;
}
