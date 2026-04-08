package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Stub — real implementation is ported in Batch E.
 * For now every ability yields a no-op goal so BossPhaseManager can reference it.
 */
public final class BossGoalFactory {
    private BossGoalFactory() {}

    public static Goal create(String type, BossEntity boss, int cooldownTicks, JsonObject params) {
        return new Goal() {
            { setFlags(EnumSet.noneOf(Flag.class)); }
            @Override public boolean canUse() { return false; }
        };
    }
}
