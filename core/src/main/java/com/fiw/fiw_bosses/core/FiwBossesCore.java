package com.fiw.fiw_bosses.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Static entry point for the MC-free core. Each loader calls {@link #init(Platform)}
 * once during mod startup to hand the core a {@link Platform} (config dir source).
 * The core then owns the shared logger and config-dir resolution, so the config
 * loaders and skin fetchers have no loader/Minecraft imports.
 */
public final class FiwBossesCore {

    public static final String MOD_ID = "fiw_bosses";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Platform platform;

    private FiwBossesCore() {}

    public static void init(Platform p) {
        platform = p;
    }

    /** The mod's config directory, e.g. {@code config/fiw_bosses}. */
    public static Path configDir() {
        if (platform == null) {
            throw new IllegalStateException("FiwBossesCore.init(Platform) was not called");
        }
        return platform.configDir();
    }
}
