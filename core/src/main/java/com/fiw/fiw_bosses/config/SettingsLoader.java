package com.fiw.fiw_bosses.config;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads/saves config/fiw_bosses/settings.json; creates it with defaults when missing. */
public final class SettingsLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GlobalSettings settings = defaults();

    private SettingsLoader() {}

    public static Path getSettingsFile() {
        return BossConfigLoader.getConfigDir().resolve("settings.json");
    }

    public static GlobalSettings get() {
        return settings;
    }

    public static void reload() {
        Path file = getSettingsFile();
        if (!Files.exists(file)) {
            settings = defaults();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ConfigValidator.log(ConfigValidator.settings("settings.json", reader).issues());
        } catch (Exception ignored) {
            // Reported by the parse below.
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            settings = parse(reader);
        } catch (Exception e) {
            FiwBossesCore.LOGGER.error("Failed to load {}: {} — using defaults", file, e.getMessage());
            settings = defaults();
        }
    }

    /** Writes the current settings atomically (temp file + move). */
    public static boolean save() {
        Path file = getSettingsFile();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("settings.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            FiwBossesCore.LOGGER.error("Failed to save {}: {}", file, e.getMessage());
            return false;
        }
    }

    static GlobalSettings parse(Reader reader) {
        GlobalSettings parsed = GSON.fromJson(reader, GlobalSettings.class);
        if (parsed == null) parsed = new GlobalSettings();
        parsed.normalize();
        return parsed;
    }

    private static GlobalSettings defaults() {
        GlobalSettings s = new GlobalSettings();
        s.normalize();
        return s;
    }
}
