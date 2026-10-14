package com.fiw.fiw_bosses.api;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossPhaseManager;
import com.fiw.fiw_bosses.entity.MinionEntity;
import net.minecraft.world.entity.Entity;

/**
 * Stable public API for other mods (notably Fiw Tools, via reflection). Only vanilla
 * types in signatures. Phase numbers are 1-based, as shown to admins.
 */
public final class FiwBossesAPI {

    private FiwBossesAPI() {}

    /** True for Fiw Bosses bosses (not minions). */
    public static boolean isBoss(Entity entity) {
        return entity instanceof BossEntity && !(entity instanceof MinionEntity);
    }

    /** True for Fiw Bosses minions (custom minions and shadow clones). */
    public static boolean isMinion(Entity entity) {
        return entity instanceof MinionEntity;
    }

    /** The boss's difficulty variant name, or null for the normal boss / non-bosses. */
    public static String getVariant(Entity entity) {
        if (!isBoss(entity)) return null;
        var def = ((BossEntity) entity).getDefinition();
        return def != null ? def.variantName : null;
    }

    /** The boss's config id, or null. */
    public static String getBossId(Entity entity) {
        return isBoss(entity) ? ((BossEntity) entity).getBossId() : null;
    }

    /** Current phase (1-based), or 0 when not a boss / no phase yet. */
    public static int getPhase(Entity entity) {
        BossPhaseManager pm = phases(entity);
        return pm != null ? pm.getCurrentPhaseIndex() + 1 : 0;
    }

    public static int getPhaseCount(Entity entity) {
        BossPhaseManager pm = phases(entity);
        return pm != null ? pm.getPhaseCount() : 0;
    }

    /**
     * Forces a phase (1-based) on an active boss without changing its HP.
     * Returns false for non-bosses, inactive/dying bosses, out-of-range or unchanged phases.
     */
    public static boolean setPhase(Entity entity, int phase) {
        BossPhaseManager pm = phases(entity);
        if (pm == null || !((BossEntity) entity).isActive() || !entity.isAlive()) return false;
        return pm.forcePhase(phase - 1);
    }

    private static BossPhaseManager phases(Entity entity) {
        return isBoss(entity) ? ((BossEntity) entity).getPhaseManager() : null;
    }
}
