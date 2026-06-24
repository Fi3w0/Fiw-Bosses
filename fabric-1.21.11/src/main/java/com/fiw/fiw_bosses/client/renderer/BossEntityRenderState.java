package com.fiw.fiw_bosses.client.renderer;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class BossEntityRenderState extends HumanoidRenderState {
    public int entityId;
    public boolean slim;
    public EntityRenderState disguiseState;
}
