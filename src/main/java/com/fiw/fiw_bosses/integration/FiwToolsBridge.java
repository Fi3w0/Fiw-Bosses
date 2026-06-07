package com.fiw.fiw_bosses.integration;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.EquipmentConfig;
import com.fiw.fiw_bosses.config.EquipmentEntry;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Optional integration with the Fiw Tools sibling mod.
 *
 * <p>Reflection-only — never imports any Fiw Tools class, never declares a
 * mod dependency. The bridge silently does nothing if Fiw Tools is not on
 * the classpath, so Fiw Bosses loads and runs identically whether the user
 * installs Fiw Tools or not. Boss / loot / equipment entries that reference
 * a {@code toolId} without Fiw Tools present are skipped without crashing.
 *
 * <p>Target API ({@code com.fiw.tools.api.FiwToolsAPI}, a Kotlin {@code object}
 * with {@code @JvmStatic} members):
 * <pre>
 *   boolean        isLoaded()
 *   Set&lt;String&gt;   listIds()
 *   ItemStack      getItemStack(String id, MinecraftServer server, int count)   // count defaults to 1
 * </pre>
 *
 * <p>Cross-mapping note: Fiw Tools ships against Mojang mappings and Fiw Bosses
 * against Yarn, but both are remapped to <em>intermediary</em> at runtime, so the
 * reflective {@code MinecraftServer}/{@code ItemStack} types resolve to the same
 * runtime classes and the lookup matches. Anything Fiw Tools bakes into the returned
 * stack (custom abilities, curses, imbuements, components) rides along automatically —
 * Fiw Bosses just drops or equips whatever {@code ItemStack} it gets back.
 */
public final class FiwToolsBridge {
    private static final String API_CLASS = "com.fiw.tools.api.FiwToolsAPI";

    private static final boolean PRESENT;
    /** Signature: ItemStack getItemStack(String id, MinecraftServer server, int count) */
    private static final Method GET_ITEMSTACK;
    /** Signature: Set&lt;String&gt; listIds() — optional, newer Fiw Tools builds only. */
    private static final Method LIST_IDS;

    static {
        boolean present = false;
        Method getItemStack = null;
        Method listIds = null;
        try {
            Class<?> cls = Class.forName(API_CLASS);
            getItemStack = cls.getMethod("getItemStack", String.class, MinecraftServer.class, int.class);
            // Newer API surface — absent on older Fiw Tools builds, so resolve defensively.
            try { listIds = cls.getMethod("listIds"); } catch (NoSuchMethodException olderApi) { /* optional */ }
            present = true;

            int known = (listIds != null) ? safeListIds(listIds).size() : -1;
            if (known >= 0) {
                FiwBosses.LOGGER.info(
                        "Fiw Tools detected ({} item id(s) registered) — toolId references in boss/loot/equipment JSON are enabled.",
                        known);
            } else {
                FiwBosses.LOGGER.info(
                        "Fiw Tools detected — toolId references in boss/loot/equipment JSON are enabled.");
            }
        } catch (ClassNotFoundException notInstalled) {
            // Fiw Tools simply isn't loaded. Stay inert.
        } catch (NoSuchMethodException apiDrift) {
            FiwBosses.LOGGER.warn("Fiw Tools is loaded but its getItemStack(String, MinecraftServer, int) "
                    + "API is missing — toolId integration disabled.");
        } catch (Throwable t) {
            FiwBosses.LOGGER.warn("Fiw Tools detection failed: {}", t.getMessage());
        }
        PRESENT = present;
        GET_ITEMSTACK = getItemStack;
        LIST_IDS = listIds;
    }

    private FiwToolsBridge() {}

    /** True if a Fiw Tools build with a compatible API is on the mod classpath. */
    public static boolean isPresent() { return PRESENT; }

    /**
     * Returns the Fiw Tools item stack for {@code id}, or {@code null} if Fiw
     * Tools is missing, the id is unknown, the server reference is null, or
     * the reflection call fails. Callers should treat null as "skip this entry".
     */
    public static ItemStack getItemStack(String id, MinecraftServer server, int count) {
        if (!PRESENT || GET_ITEMSTACK == null) return null;
        if (id == null || id.isEmpty() || server == null) return null;
        try {
            Object result = GET_ITEMSTACK.invoke(null, id, server, count);
            return (result instanceof ItemStack stack) ? stack : null;
        } catch (Throwable t) {
            FiwBosses.LOGGER.warn("Fiw Tools getItemStack failed for id '{}': {}", id, t.getMessage());
            return null;
        }
    }

    /**
     * All item ids currently registered by Fiw Tools, or an empty set if Fiw Tools
     * is missing or too old to expose {@code listIds()}. Note this is only populated
     * once Fiw Tools has loaded its configs (server start / its own reload), so query
     * it at runtime — not during mod init.
     */
    public static Set<String> listIds() {
        if (!PRESENT || LIST_IDS == null) return Set.of();
        return safeListIds(LIST_IDS);
    }

    /**
     * Whether {@code id} is a known Fiw Tools item id. Returns {@code true} whenever the
     * id list can't be consulted (Fiw Tools absent/older API, or an empty registry) so a
     * lookup is never blocked on a false negative — {@link #getItemStack} stays the
     * source of truth. Use this only to surface helpful "unknown id" diagnostics.
     */
    public static boolean isKnownId(String id) {
        if (id == null || id.isEmpty()) return false;
        if (LIST_IDS == null) return true;          // can't tell → don't claim it's unknown
        Set<String> ids = listIds();
        return ids.isEmpty() || ids.contains(id);   // empty registry → don't spam false warnings
    }

    /**
     * Scans every loaded boss + minion config (equipment, per-phase equipment, and loot)
     * for {@code toolId} references and logs a single warning listing any that aren't in the
     * Fiw Tools registry — catching typos early with a clear message instead of a silent skip
     * at drop/equip time. No-op unless Fiw Tools is present, exposes {@code listIds()}, and has
     * actually loaded its items (so it's safe to call on {@code /boss reload} or SERVER_STARTED,
     * but not during mod init when the registry is still empty).
     */
    public static void reportUnknownToolIds() {
        if (!PRESENT || LIST_IDS == null) return;
        Set<String> known = listIds();
        if (known.isEmpty()) return; // registry not loaded yet → don't emit false warnings

        Set<String> unknown = new TreeSet<>();
        for (BossDefinition def : BossConfigLoader.getDefinitions().values()) {
            collectUnknownEquipment(def.equipment, known, unknown);
            collectUnknownLoot(def.loot, known, unknown);
            if (def.phases != null) {
                for (PhaseDefinition phase : def.phases) {
                    collectUnknownEquipment(phase.equipment, known, unknown);
                }
            }
        }
        for (MinionDefinition def : MinionConfigLoader.getDefinitions().values()) {
            collectUnknownEquipment(def.equipment, known, unknown);
            collectUnknownLoot(def.loot, known, unknown);
        }

        if (!unknown.isEmpty()) {
            FiwBosses.LOGGER.warn(
                    "Boss/minion configs reference {} Fiw Tools id(s) not in the Fiw Tools registry: {} "
                            + "— check spelling or run /fiwtools list for valid ids.",
                    unknown.size(), String.join(", ", unknown));
        }
    }

    private static void collectUnknownEquipment(EquipmentConfig eq, Set<String> known, Set<String> out) {
        if (eq == null) return;
        collectUnknownEntry(eq.mainHand, known, out);
        collectUnknownEntry(eq.offHand,  known, out);
        collectUnknownEntry(eq.head,     known, out);
        collectUnknownEntry(eq.chest,    known, out);
        collectUnknownEntry(eq.legs,     known, out);
        collectUnknownEntry(eq.feet,     known, out);
    }

    private static void collectUnknownEntry(EquipmentEntry entry, Set<String> known, Set<String> out) {
        if (entry != null && entry.toolId != null && !entry.toolId.isEmpty()
                && !known.contains(entry.toolId)) {
            out.add(entry.toolId);
        }
    }

    private static void collectUnknownLoot(List<LootEntry> loot, Set<String> known, Set<String> out) {
        if (loot == null) return;
        for (LootEntry entry : loot) {
            if (entry.toolId != null && !entry.toolId.isEmpty() && !known.contains(entry.toolId)) {
                out.add(entry.toolId);
            }
        }
    }

    private static Set<String> safeListIds(Method listIds) {
        try {
            Object result = listIds.invoke(null);
            if (result instanceof Set<?> set) {
                Set<String> out = new HashSet<>(set.size());
                for (Object o : set) if (o != null) out.add(o.toString());
                return out;
            }
        } catch (Throwable t) {
            FiwBosses.LOGGER.warn("Fiw Tools listIds() failed: {}", t.getMessage());
        }
        return Set.of();
    }
}
