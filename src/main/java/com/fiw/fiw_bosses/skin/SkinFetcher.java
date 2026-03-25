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

    /** Fetch the skin for a Minecraft player name, auto-detecting slim/classic. */
    public static CompletableFuture<SkinData> fetchPlayerSkin(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuid = getUuidFromName(playerName);
                if (uuid == null) {
                    FiwBosses.LOGGER.warn("Could not find UUID for player: {}", playerName);
                    return null;
                }

                SkinResult result = getSkinResult(uuid);
                if (result == null) {
                    FiwBosses.LOGGER.warn("Could not find skin URL for player: {}", playerName);
                    return null;
                }

                byte[] png = downloadBytes(result.url);
                if (png == null) return null;
                return new SkinData(png, result.slim);
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to fetch skin for player {}: {}", playerName, e.getMessage());
                return null;
            }
        }, EXECUTOR);
    }

    /** Read a local skin file.  slim comes from the boss config (SkinDefinition.slim). */
    public static CompletableFuture<SkinData> fetchLocalSkin(String filename, boolean slim) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File skinFile = BossConfigLoader.getSkinsDir().resolve(filename).toFile();
                if (!skinFile.exists()) {
                    FiwBosses.LOGGER.warn("Skin file not found: {}", skinFile.getPath());
                    return null;
                }
                byte[] png = Files.readAllBytes(skinFile.toPath());
                return new SkinData(png, slim);
            } catch (Exception e) {
                FiwBosses.LOGGER.error("Failed to read skin file {}: {}", filename, e.getMessage());
                return null;
            }
        }, EXECUTOR);
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** URL + slim flag extracted from the Mojang session server. */
    private record SkinResult(String url, boolean slim) {}

    private static String getUuidFromName(String name) throws Exception {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return json.get("id").getAsString();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static SkinResult getSkinResult(String uuid) throws Exception {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
                for (var prop : profile.getAsJsonArray("properties")) {
                    JsonObject propObj = prop.getAsJsonObject();
                    if (!"textures".equals(propObj.get("name").getAsString())) continue;

                    String base64  = propObj.get("value").getAsString();
                    String decoded = new String(Base64.getDecoder().decode(base64));
                    JsonObject root     = JsonParser.parseString(decoded).getAsJsonObject();
                    JsonObject textures = root.getAsJsonObject("textures");
                    if (!textures.has("SKIN")) return null;

                    JsonObject skinObj = textures.getAsJsonObject("SKIN");
                    String skinUrl = skinObj.get("url").getAsString();

                    // Mojang embeds {"metadata":{"model":"slim"}} for Alex-model skins
                    boolean slim = false;
                    if (skinObj.has("metadata")) {
                        JsonObject meta = skinObj.getAsJsonObject("metadata");
                        slim = meta.has("model") && "slim".equals(meta.get("model").getAsString());
                    }
                    return new SkinResult(skinUrl, slim);
                }
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] downloadBytes(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try {
            if (conn.getResponseCode() != 200) return null;
            return conn.getInputStream().readAllBytes();
        } finally {
            conn.disconnect();
        }
    }
}
