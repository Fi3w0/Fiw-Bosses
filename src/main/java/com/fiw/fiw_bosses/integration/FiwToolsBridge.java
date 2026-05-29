package com.fiw.fiw_bosses.integration;

import com.fiw.fiw_bosses.FiwBosses;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/**
 * Optional integration with the Fiw Tools sibling mod.
 *
 * <p>Reflection-only — never imports any Fiw Tools class, never declares a
 * mod dependency. The bridge silently does nothing if Fiw Tools is not on
 * the classpath, so Fiw Bosses loads and runs identically whether the user
 * installs Fiw Tools or not. Boss / loot / equipment entries that reference
 * a {@code toolId} without Fiw Tools present are skipped without crashing.
 *
 * <p>When a Fabric 1.21.11 build of Fiw Tools eventually ships, this bridge
 * picks up its public static API automatically — no rebuild of Fiw Bosses
 * is required.
 */
public final class FiwToolsBridge {
    private static final String API_CLASS = "com.fiw.tools.api.FiwToolsAPI";

    private static final boolean PRESENT;
    /** Signature: ItemStack getItemStack(String id, MinecraftServer server, int count) */
    private static final Method GET_ITEMSTACK;

    static {
        boolean present = false;
        Method method = null;
        try {
            Class<?> cls = Class.forName(API_CLASS);
            method = cls.getMethod("getItemStack", String.class, MinecraftServer.class, int.class);
            present = true;
            FiwBosses.LOGGER.info("Fiw Tools detected — toolId references in boss/loot/equipment JSON are enabled.");
        } catch (ClassNotFoundException notInstalled) {
            // Fiw Tools simply isn't loaded. Stay inert.
        } catch (NoSuchMethodException apiDrift) {
            FiwBosses.LOGGER.warn("Fiw Tools is loaded but its API signature is unfamiliar — toolId integration disabled.");
        } catch (Throwable t) {
            FiwBosses.LOGGER.warn("Fiw Tools detection failed: {}", t.getMessage());
        }
        PRESENT = present;
        GET_ITEMSTACK = method;
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
}
