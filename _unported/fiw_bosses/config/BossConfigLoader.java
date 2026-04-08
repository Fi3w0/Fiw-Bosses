package com.fiw.fiw_bosses.config;

import com.fiw.fiw_bosses.FiwBosses;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BossConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, BossDefinition> definitions = new HashMap<>();

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("fiw_bosses");
    }

    public static Path getBossesDir() {
        return getConfigDir().resolve("bosses");
    }

    public static Path getSkinsDir() {
        return getConfigDir().resolve("skins");
    }

    public static void loadAll() {
        File bossesDir = getBossesDir().toFile();
        File skinsDir = getSkinsDir().toFile();

        if (!bossesDir.exists()) {
            bossesDir.mkdirs();
            generateExample(bossesDir);
        }
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
        }

        reload();
    }

    public static int reload() {
        definitions.clear();
        File bossesDir = getBossesDir().toFile();
        File[] files = bossesDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            FiwBosses.LOGGER.warn("No boss definition files found in {}", bossesDir.getPath());
            return 0;
        }

        int loaded = 0;
        int failed = 0;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                BossDefinition def = GSON.fromJson(reader, BossDefinition.class);
                if (def.id == null || def.id.isEmpty()) {
                    FiwBosses.LOGGER.error("Boss definition in {} has no 'id' field, skipping", file.getName());
                    failed++;
                    continue;
                }
                definitions.put(def.id, def);
                loaded++;
                FiwBosses.LOGGER.info("Loaded boss definition: {}", def.id);
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to load boss definition from {}: {}", file.getName(), e.getMessage());
                failed++;
            }
        }

        FiwBosses.LOGGER.info("Loaded {} boss definitions ({} failed)", loaded, failed);
        return loaded;
    }

    public static BossDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public static Map<String, BossDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    private static void generateExample(File bossesDir) {
        BossDefinition example = new BossDefinition();
        example.id = "example_boss";
        example.displayName = "&6&lExample Boss";
        example.health = 300.0f;
        example.armor = 8.0f;
        example.speed = 0.32f;
        example.knockbackResistance = 0.6f;
        example.attackDamage = 12.0f;

        example.skin = new SkinDefinition();
        example.skin.type = "player";
        example.skin.value = "Steve";

        example.equipment = new EquipmentConfig();
        example.equipment.mainHand = new EquipmentEntry();
        example.equipment.mainHand.item = "minecraft:iron_sword";

        // Phase 1: basic attacks
        PhaseDefinition phase1 = new PhaseDefinition();
        phase1.hpThresholdPercent = 1.0f;
        AbilityEntry slash = new AbilityEntry();
        slash.type = "melee_slash";
        slash.cooldownTicks = 40;
        slash.params.addProperty("range", 4.0f);
        slash.params.addProperty("arc", 90.0f);
        slash.params.addProperty("damage", 10.0f);
        slash.params.addProperty("taunt", "&c&lFeel my blade!");
        phase1.abilities.add(slash);
        AbilityEntry dodge = new AbilityEntry();
        dodge.type = "dodge";
        dodge.cooldownTicks = 60;
        dodge.params.addProperty("chance", 0.25f);
        dodge.params.addProperty("distance", 3.0f);
        phase1.abilities.add(dodge);
        example.phases.add(phase1);

        // Phase 2: enraged
        PhaseDefinition phase2 = new PhaseDefinition();
        phase2.hpThresholdPercent = 0.5f;
        phase2.speedMultiplier = 1.3f;
        phase2.damageMultiplier = 1.5f;
        phase2.transitionMessage = "&c&lThe Example Boss grows furious!";
        phase2.transitionSound = "minecraft:entity.wither.spawn";
        phase2.transitionParticle = "minecraft:explosion";
        AbilityEntry aoe = new AbilityEntry();
        aoe.type = "aoe_smash";
        aoe.cooldownTicks = 80;
        aoe.params.addProperty("radius", 5.0f);
        aoe.params.addProperty("damage", 15.0f);
        aoe.params.addProperty("knockback", 2.0f);
        aoe.params.addProperty("taunt", "&4&lThe ground shakes!");
        phase2.abilities.add(aoe);
        AbilityEntry summon = new AbilityEntry();
        summon.type = "summon_minions";
        summon.cooldownTicks = 200;
        summon.params.addProperty("taunt", "&5&lRise, my minions!");
        phase2.abilities.add(summon);
        MinionEntry minion = new MinionEntry();
        minion.entityType = "minecraft:zombie";
        minion.count = 2;
        minion.maxAlive = 4;
        minion.spawnRadius = 5.0f;
        phase2.minions.add(minion);
        example.phases.add(phase2);

        // Loot
        LootEntry loot1 = new LootEntry();
        loot1.item = "minecraft:diamond";
        loot1.count = 5;
        loot1.chance = 1.0f;
        example.loot.add(loot1);

        LootEntry loot2 = new LootEntry();
        loot2.item = "minecraft:golden_apple";
        loot2.count = 2;
        loot2.chance = 0.5f;
        example.loot.add(loot2);

        File exampleFile = new File(bossesDir, "example_boss.json");
        try (FileWriter writer = new FileWriter(exampleFile)) {
            GSON.toJson(example, writer);
            FiwBosses.LOGGER.info("Generated example boss config: {}", exampleFile.getName());
        } catch (IOException e) {
            FiwBosses.LOGGER.error("Failed to generate example boss config: {}", e.getMessage());
        }
    }
}
