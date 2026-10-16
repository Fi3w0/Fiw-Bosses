package com.fiw.fiw_bosses.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every config under examples/ must load with zero warnings through the same validator the
 * mod uses, and every file in a negative/ folder must be reported.
 */
class ExampleConfigsTest {

    private static Path examples() {
        Path here = Path.of("").toAbsolutePath();
        Path root = here.getFileName().toString().equals("core") ? here.getParent() : here;
        return root.resolve("examples");
    }

    private static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> s = Files.walk(examples())) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static boolean under(Path file, String folder) {
        for (Path part : examples().relativize(file)) if (part.toString().equals(folder)) return true;
        return false;
    }

    @Test
    void everyExampleLoadsWithoutWarnings() throws IOException {
        List<ConfigIssue> issues = new ArrayList<>();
        Map<String, BossDefinition> bosses = new LinkedHashMap<>();
        Map<String, String> bossFiles = new LinkedHashMap<>();
        Map<String, MinionDefinition> minions = new LinkedHashMap<>();
        int checked = 0;
        for (Path file : files(".json")) {
            if (under(file, "items")) continue; // Fiw Tools item configs, checked by Fiw Tools
            String name = examples().relativize(file).toString();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                if (file.getFileName().toString().equals("settings.json")) {
                    issues.addAll(ConfigValidator.settings(name, r).issues());
                } else if (under(file, "minions")) {
                    var c = ConfigValidator.minion(name, r);
                    issues.addAll(c.issues());
                    if (c.value() != null) minions.put(c.value().id, c.value());
                } else {
                    var c = ConfigValidator.boss(name, r);
                    issues.addAll(c.issues());
                    if (c.value() != null) { bosses.put(c.value().id, c.value()); bossFiles.put(c.value().id, name); }
                }
            }
            checked++;
        }
        issues.addAll(ConfigValidator.references(bosses, bossFiles, minions));
        assertTrue(checked > 20, "only " + checked + " example files found under " + examples());
        assertTrue(issues.isEmpty(), "\n" + String.join("\n", issues.stream().map(ConfigIssue::describe).toList()));
    }

    @Test
    void everyNegativeExampleIsReported() throws IOException {
        List<Path> negatives = files(".invalid");
        assertFalse(negatives.isEmpty());
        for (Path file : negatives) {
            List<ConfigIssue> issues = ConfigValidator.validateDirectory(single(file));
            assertFalse(issues.isEmpty(), file + " produced no warning");
        }
    }

    /** Copies one negative file into a temporary config folder as an active boss/minion file. */
    private static Path single(Path file) throws IOException {
        Path dir = Files.createTempDirectory("fiw-bosses-negative");
        String name = file.getFileName().toString().replace(".invalid", "");
        Path target = dir.resolve(under(file, "minions") ? "minions" : "bosses").resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
        return dir;
    }
}
