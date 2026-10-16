package com.fiw.fiw_bosses.skin;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinFetcherTest {

    @TempDir Path directory;

    @Test
    void loadsSameSkinWithAndWithoutPngExtension() throws Exception {
        FiwBossesCore.init(() -> directory);
        Files.createDirectories(directory.resolve("skins"));
        byte[] png = { (byte) 137, 80, 78, 71 };
        Files.write(directory.resolve("skins/guardian.png"), png);
        SkinData shortName = SkinFetcher.fetchLocalSkin("guardian", true).get(5, TimeUnit.SECONDS);
        SkinData fullName = SkinFetcher.fetchLocalSkin("guardian.png", false).get(5, TimeUnit.SECONDS);
        assertArrayEquals(png, shortName.png);
        assertArrayEquals(shortName.png, fullName.png);
        assertTrue(shortName.slim);
        assertFalse(fullName.slim);
    }
}
