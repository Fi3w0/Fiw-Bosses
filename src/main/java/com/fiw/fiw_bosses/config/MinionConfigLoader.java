package com.fiw.fiw_bosses.config;

import com.fiw.fiw_bosses.FiwBosses;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MinionConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, MinionDefinition> definitions = new HashMap<>();

    public static File getMinionsDir() {
        return BossConfigLoader.getConfigDir().resolve("minions").toFile();
    }

    public static void loadAll() {
        File minionsDir = getMinionsDir();
        if (!minionsDir.exists()) {
            minionsDir.mkdirs();
        }
        reload();
    }

    public static int reload() {
        definitions.clear();
        File minionsDir = getMinionsDir();
        File[] files = minionsDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            FiwBosses.LOGGER.info("No minion definition files found in {}", minionsDir.getPath());
            return 0;
        }

        int loaded = 0;
        int failed = 0;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                MinionDefinition def = GSON.fromJson(reader, MinionDefinition.class);
                if (def.id == null || def.id.isEmpty()) {
                    FiwBosses.LOGGER.error("Minion definition in {} has no 'id' field, skipping", file.getName());
                    failed++;
                    continue;
                }
                definitions.put(def.id, def);
                loaded++;
                FiwBosses.LOGGER.info("Loaded minion definition: {}", def.id);
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to load minion definition from {}: {}", file.getName(), e.getMessage());
                failed++;
            }
        }

        FiwBosses.LOGGER.info("Loaded {} minion definitions ({} failed)", loaded, failed);
        return loaded;
    }

    public static MinionDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public static Map<String, MinionDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }
}
