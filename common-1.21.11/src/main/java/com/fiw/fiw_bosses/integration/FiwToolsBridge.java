package com.fiw.fiw_bosses.integration;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.EquipmentConfig;
import com.fiw.fiw_bosses.config.EquipmentEntry;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.config.PhaseDefinition;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Optional reflection-only integration with Fiw Tools.
 *
 * <p>No Fiw Tools classes are imported or declared as dependencies. If the API is
 * absent, {@code toolId} entries are skipped without crashing the boss mod.
 */
public final class FiwToolsBridge {
    private static final String API_CLASS = "com.fiw.tools.api.FiwToolsAPI";

    private static final boolean PRESENT;
    private static final Method GET_ITEMSTACK;
    private static final Method LIST_IDS;

    static {
        boolean present = false;
        Method getItemStack = null;
        Method listIds = null;
        try {
            Class<?> cls = Class.forName(API_CLASS);
            getItemStack = cls.getMethod("getItemStack", String.class, MinecraftServer.class, int.class);
            try {
                listIds = cls.getMethod("listIds");
            } catch (NoSuchMethodException ignored) {
                // Older Fiw Tools builds can still provide getItemStack.
            }
            present = true;

            int known = listIds != null ? safeListIds(listIds).size() : -1;
            if (known >= 0) {
                FiwBossesCore.LOGGER.info(
                        "Fiw Tools detected ({} item id(s) registered); toolId JSON entries are enabled.",
                        known);
            } else {
                FiwBossesCore.LOGGER.info("Fiw Tools detected; toolId JSON entries are enabled.");
            }
        } catch (ClassNotFoundException ignored) {
            // Fiw Tools is not installed.
        } catch (NoSuchMethodException e) {
            FiwBossesCore.LOGGER.warn("Fiw Tools is loaded but getItemStack(String, MinecraftServer, int) is missing; toolId integration disabled.");
        } catch (Exception t) {
            FiwBossesCore.LOGGER.warn("Fiw Tools detection failed: {}", t.getMessage());
        }
        PRESENT = present;
        GET_ITEMSTACK = getItemStack;
        LIST_IDS = listIds;
    }

    private FiwToolsBridge() {}

    public static boolean isPresent() {
        return PRESENT;
    }

    public static ItemStack getItemStack(String id, MinecraftServer server, int count) {
        if (!PRESENT || GET_ITEMSTACK == null || id == null || id.isEmpty() || server == null) return null;
        try {
            Object result = GET_ITEMSTACK.invoke(null, id, server, count);
            return result instanceof ItemStack stack ? stack : null;
        } catch (Exception t) {
            FiwBossesCore.LOGGER.warn("Fiw Tools getItemStack failed for id '{}': {}", id, t.getMessage());
            return null;
        }
    }

    public static Set<String> listIds() {
        if (!PRESENT || LIST_IDS == null) return Set.of();
        return safeListIds(LIST_IDS);
    }

    public static boolean isKnownId(String id) {
        if (id == null || id.isEmpty()) return false;
        if (LIST_IDS == null) return true;
        Set<String> ids = listIds();
        return ids.isEmpty() || ids.contains(id);
    }

    public static void reportUnknownToolIds() {
        if (!PRESENT || LIST_IDS == null) return;
        Set<String> known = listIds();
        if (known.isEmpty()) return;

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
            FiwBossesCore.LOGGER.warn(
                    "Boss/minion configs reference {} Fiw Tools id(s) not in the Fiw Tools registry: {}",
                    unknown.size(), String.join(", ", unknown));
        }
    }

    private static void collectUnknownEquipment(EquipmentConfig eq, Set<String> known, Set<String> out) {
        if (eq == null) return;
        collectUnknownEntry(eq.mainHand, known, out);
        collectUnknownEntry(eq.offHand, known, out);
        collectUnknownEntry(eq.head, known, out);
        collectUnknownEntry(eq.chest, known, out);
        collectUnknownEntry(eq.legs, known, out);
        collectUnknownEntry(eq.feet, known, out);
    }

    private static void collectUnknownEntry(EquipmentEntry entry, Set<String> known, Set<String> out) {
        if (entry != null && entry.toolId != null && !entry.toolId.isEmpty() && !known.contains(entry.toolId)) {
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
                for (Object id : set) {
                    if (id != null) out.add(id.toString());
                }
                return out;
            }
        } catch (Exception t) {
            FiwBossesCore.LOGGER.warn("Fiw Tools listIds() failed: {}", t.getMessage());
        }
        return Set.of();
    }
}
