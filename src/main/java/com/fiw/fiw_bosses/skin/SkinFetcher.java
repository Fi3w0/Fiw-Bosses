package com.fiw.fiw_bosses.skin;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SkinFetcher {

    private static final Executor EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "FIW-Bosses-SkinFetcher");
        t.setDaemon(true);
        return t;
    });

    public static CompletableFuture<byte[]> fetchPlayerSkin(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Get UUID from name
                String uuid = getUuidFromName(playerName);
                if (uuid == null) {
                    FiwBosses.LOGGER.warn("Could not find UUID for player: {}", playerName);
                    return null;
                }

                // Step 2: Get profile with textures
                String skinUrl = getSkinUrl(uuid);
                if (skinUrl == null) {
                    FiwBosses.LOGGER.warn("Could not find skin URL for player: {}", playerName);
                    return null;
                }

                // Step 3: Download skin PNG
                return downloadBytes(skinUrl);
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to fetch skin for player {}: {}", playerName, e.getMessage());
                return null;
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<byte[]> fetchLocalSkin(String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File skinFile = BossConfigLoader.getSkinsDir().resolve(filename).toFile();
                if (!skinFile.exists()) {
                    FiwBosses.LOGGER.warn("Skin file not found: {}", skinFile.getPath());
                    return null;
                }
                return Files.readAllBytes(skinFile.toPath());
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to read skin file {}: {}", filename, e.getMessage());
                return null;
            }
        }, EXECUTOR);
    }

    private static String getUuidFromName(String name) throws Exception {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) return null;

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }

    private static String getSkinUrl(String uuid) throws Exception {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) return null;

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            var properties = json.getAsJsonArray("properties");
            for (var prop : properties) {
                JsonObject propObj = prop.getAsJsonObject();
                if ("textures".equals(propObj.get("name").getAsString())) {
                    String base64 = propObj.get("value").getAsString();
                    String decoded = new String(Base64.getDecoder().decode(base64));
                    JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
                    JsonObject texturesMap = textures.getAsJsonObject("textures");
                    if (texturesMap.has("SKIN")) {
                        return texturesMap.getAsJsonObject("SKIN").get("url").getAsString();
                    }
                }
            }
        }
        return null;
    }

    private static byte[] downloadBytes(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) return null;

        return conn.getInputStream().readAllBytes();
    }
}
