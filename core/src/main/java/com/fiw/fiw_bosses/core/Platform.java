package com.fiw.fiw_bosses.core;

import java.nio.file.Path;

/**
 * The small surface each loader provides to the MC-free core: where config lives.
 * All Minecraft-specific actions stay in the loader/common modules; the core only
 * reads/writes/validates config. Logging goes through {@link FiwBossesCore#LOGGER}
 * (SLF4J, provided by Minecraft at runtime).
 */
public interface Platform {

    /** Directory for this mod's config, e.g. {@code config/fiw_bosses}. */
    Path configDir();
}
