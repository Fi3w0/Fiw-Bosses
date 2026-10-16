package com.fiw.fiw_bosses.config;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    private static List<ConfigIssue> boss(String json) {
        return ConfigValidator.boss("b.json", new StringReader(json)).issues();
    }

    private static boolean has(List<ConfigIssue> issues, String field, String text) {
        return issues.stream().anyMatch(i -> i.field().equals(field) && i.message().contains(text));
    }

    @Test
    void cleanBossHasNoIssues() {
        assertEquals(List.of(), boss("{\"id\":\"b\",\"scale\":2,\"phases\":[{\"abilities\":[{\"type\":\"slam\"}]}]}"));
    }

    @Test
    void typosAreReportedAtAnyDepthWithAHint() {
        List<ConfigIssue> issues = boss("{\"id\":\"b\",\"scael\":2,\"phases\":[{\"hpThresholdPrecent\":0.5,"
                + "\"abilities\":[{\"type\":\"slam\",\"cooldownTick\":40}]}],\"loot\":[{\"item\":\"minecraft:stick\",\"cout\":2}]}");
        assertTrue(has(issues, "scael", "did you mean 'scale'"), issues.toString());
        assertTrue(has(issues, "phases[0].hpThresholdPrecent", "hpThresholdPercent"), issues.toString());
        assertTrue(has(issues, "phases[0].abilities[0].cooldownTick", "cooldownTicks"), issues.toString());
        assertTrue(has(issues, "loot[0].cout", "count"), issues.toString());
    }

    @Test
    void unknownAbilitiesIncludingDomainAttacksAndRages() {
        List<ConfigIssue> issues = boss("{\"id\":\"b\",\"phases\":[{\"abilities\":[{\"type\":\"slahs\"},"
                + "{\"type\":\"domain\",\"params\":{\"attacks\":[{\"type\":\"beem\"}]}}]}],"
                + "\"rages\":{\"mad\":{\"durationSecond\":5,\"abilities\":[{\"type\":\"nope_nope\"}]}}}");
        assertTrue(has(issues, "phases[0].abilities[0].type", "did you mean 'slam'"), issues.toString());
        assertTrue(has(issues, "phases[0].abilities[1].params.attacks[0].type", "'beam'"), issues.toString());
        assertTrue(has(issues, "rages.mad.durationSecond", "durationSeconds"), issues.toString());
        assertTrue(has(issues, "rages.mad.abilities[0].type", "unknown ability"), issues.toString());
    }

    @Test
    void valuesOutOfRange() {
        List<ConfigIssue> issues = boss("{\"id\":\"b\",\"health\":0,\"scale\":40,\"reach\":-1,"
                + "\"movement\":\"flying\",\"renderEntity\":\"Minecraft:Iron Golem\",\"phases\":[{}]}");
        assertTrue(has(issues, "health", "not above 0"));
        assertTrue(has(issues, "scale", "16.0 is used"));
        assertTrue(has(issues, "reach", "0.0 is used"));
        assertTrue(has(issues, "movement", "unknown movement"));
        assertTrue(has(issues, "renderEntity", "not a valid entity id"));
    }

    @Test
    void brokenFilesAreErrorsAndSkipped() {
        var missingId = ConfigValidator.boss("b.json", new StringReader("{\"health\":5}"));
        assertNull(missingId.value());
        assertEquals(ConfigIssue.Severity.ERROR, missingId.issues().get(0).severity());
        var badJson = ConfigValidator.boss("b.json", new StringReader("{\"id\":\"b\",}}"));
        assertNull(badJson.value());
        var wrongType = ConfigValidator.boss("b.json", new StringReader("{\"id\":\"b\",\"health\":\"lots\"}"));
        assertNull(wrongType.value());
    }

    @Test
    void scaleOnVanillaMinionsIsIgnored() {
        var c = ConfigValidator.minion("m.json", new StringReader("{\"id\":\"m\",\"baseEntity\":\"minecraft:zombie\",\"scale\":2}"));
        assertTrue(has(c.issues(), "scale", "only apply to custom minions"));
    }

    @Test
    void folderChecksReferencesDuplicatesAndSchedules() throws Exception {
        Path dir = Files.createTempDirectory("fiw-bosses-validate");
        Files.createDirectories(dir.resolve("bosses"));
        Files.createDirectories(dir.resolve("minions"));
        Files.writeString(dir.resolve("bosses/a.json"), "{\"id\":\"a\",\"phases\":[{\"minions\":[{\"minionId\":\"ghost\"}],"
                + "\"shield\":{\"minion\":\"pylon\"}}],\"summon\":[{\"item\":\"minecraft:paper\",\"variant\":\"hard\"}],"
                + "\"antiCheese\":{\"vehicleRage\":\"boated\",\"stuck\":{\"actions\":[\"rage:trapped\",\"explode\"]}},"
                + "\"schedule\":{\"intervalMinutes\":5,\"times\":[\"18:00\"],\"locations\":[{\"x\":0,\"z\":0}]}}");
        Files.writeString(dir.resolve("bosses/b.json"), "{\"id\":\"a\",\"phases\":[{}]}");
        Files.writeString(dir.resolve("settings.json"), "{\"domainBreakVolum\":20}");
        List<ConfigIssue> issues = ConfigValidator.validateDirectory(dir);
        String all = String.join("\n", issues.stream().map(ConfigIssue::describe).toList());
        assertTrue(all.contains("no minion 'ghost'"), all);
        assertTrue(all.contains("shield has no pylons"), all);
        assertTrue(all.contains("unknown variant 'hard'"), all);
        assertTrue(all.contains("no rage 'boated'"), all);
        assertTrue(all.contains("no rage 'trapped'"), all);
        assertTrue(all.contains("unknown action 'explode'"), all);
        assertTrue(all.contains("also used by bosses/a.json"), all);
        assertTrue(all.contains("domainBreakVolume"), all);
        assertTrue(issues.stream().anyMatch(i -> i.file().equals("schedules")), all);
    }

    @Test
    void hintsOnlyForCloseNames() {
        assertEquals("scale", ConfigValidator.closest("scael", List.of("scale", "speed")));
        assertNull(ConfigValidator.closest("banana", List.of("scale", "speed")));
        assertEquals(Map.of(), Map.of());
    }
}
