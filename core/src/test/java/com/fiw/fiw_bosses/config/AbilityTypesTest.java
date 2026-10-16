package com.fiw.fiw_bosses.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AbilityTypes.KNOWN must list exactly what every version's BossGoalFactory registers. */
class AbilityTypesTest {

    private static final Pattern REGISTER = Pattern.compile("register\\(\"([a-z0-9_]+)\"");

    @Test
    void matchesEveryGoalFactory() throws IOException {
        Path here = Path.of("").toAbsolutePath();
        Path root = here.getFileName().toString().equals("core") ? here.getParent() : here;
        int factories = 0;
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(p -> p.getFileName().toString().startsWith("common-")).toList()) {
                Path factory = module.resolve("src/main/java/com/fiw/fiw_bosses/goal/BossGoalFactory.java");
                if (!Files.isRegularFile(factory)) continue;
                Set<String> registered = new TreeSet<>();
                Matcher m = REGISTER.matcher(Files.readString(factory));
                while (m.find()) registered.add(m.group(1));
                assertEquals(new TreeSet<>(AbilityTypes.KNOWN), registered, module.getFileName().toString());
                factories++;
            }
        }
        assertTrue(factories >= 4, "found " + factories + " goal factories");
    }
}
