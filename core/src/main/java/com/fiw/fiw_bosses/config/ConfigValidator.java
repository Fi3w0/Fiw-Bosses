package com.fiw.fiw_bosses.config;

import com.fiw.fiw_bosses.config.ConfigIssue.Severity;
import com.fiw.fiw_bosses.schedule.Schedules;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds config problems without Minecraft: unknown keys at any depth (Gson ignores them
 * silently), unknown ability types, out-of-range values and broken references between
 * files. Used by the loaders (logged on every reload), {@code /boss validate} and tests.
 */
public final class ConfigValidator {

    private static final Gson GSON = new Gson();
    private static final Pattern REGISTRY_ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Set<String> BOSS_MOVEMENT = Set.of(
            "side", "normal", "vanilla", "hit_and_run", "guard_point", "phase_walk", "hover", "sniper", "berserk");
    private static final Set<String> MINION_ONLY_MOVEMENT = Set.of("follow_boss", "static");
    private static final Set<String> STUCK_ACTIONS = Set.of(
            "teleport_to_target", "teleport_attack", "teleport_home", "heal", "reset", "despawn");

    private ConfigValidator() {}

    /** A parsed config (null when the file could not be loaded) and its problems. */
    public record Checked<T>(T value, List<ConfigIssue> issues) {}

    // ── Single files ─────────────────────────────────────────────────────────

    public static Checked<BossDefinition> boss(String file, Reader reader) {
        List<ConfigIssue> issues = new ArrayList<>();
        JsonObject raw = parseObject(file, reader, issues);
        if (raw == null) return new Checked<>(null, issues);
        BossDefinition def;
        try {
            def = GSON.fromJson(raw, BossDefinition.class);
        } catch (RuntimeException e) {
            issues.add(error(file, "", "wrong value type: " + rootMessage(e)));
            return new Checked<>(null, issues);
        }
        if (def.id == null || def.id.isBlank()) {
            issues.add(error(file, "id", "missing \"id\"; file skipped"));
            return new Checked<>(null, issues);
        }
        unknownKeys(raw, BossDefinition.class, "", file, issues);
        checkBody(file, def.health, def.baseEntity, def.renderEntity, def.scale, def.reach, issues);
        if (def.health > 10_000_000f) {
            issues.add(warn(file, "health", "above ~10,000,000 very small hits may stop registering (float precision)"));
        }
        if (def.phases == null || def.phases.isEmpty()) {
            issues.add(warn(file, "phases", "no phases: the boss has no abilities"));
        } else {
            for (int i = 0; i < def.phases.size(); i++) {
                PhaseDefinition p = def.phases.get(i);
                if (p != null) checkAbilities(file, "phases[" + i + "].abilities", p.abilities, issues);
            }
        }
        if (def.rages != null) {
            def.rages.forEach((name, rage) -> {
                if (rage == null) return;
                String path = "rages." + name;
                rageKeys(rage, path, file, issues);
                try {
                    PhaseDefinition overlay = GSON.fromJson(rage, PhaseDefinition.class);
                    checkAbilities(file, path + ".abilities", overlay.abilities, issues);
                } catch (RuntimeException e) {
                    issues.add(warn(file, path, "wrong value type: " + rootMessage(e) + "; rage ignored"));
                }
            });
        }
        checkMovement(file, def.movement, false, issues);
        checkPathfinding(file, def.pathfinding, issues);
        checkSkin(file, def.skin, issues);
        checkLoot(file, def.loot, issues);
        return new Checked<>(def, issues);
    }

    public static Checked<MinionDefinition> minion(String file, Reader reader) {
        List<ConfigIssue> issues = new ArrayList<>();
        JsonObject raw = parseObject(file, reader, issues);
        if (raw == null) return new Checked<>(null, issues);
        MinionDefinition def;
        try {
            def = GSON.fromJson(raw, MinionDefinition.class);
        } catch (RuntimeException e) {
            issues.add(error(file, "", "wrong value type: " + rootMessage(e)));
            return new Checked<>(null, issues);
        }
        if (def.id == null || def.id.isBlank()) {
            issues.add(error(file, "id", "missing \"id\"; file skipped"));
            return new Checked<>(null, issues);
        }
        unknownKeys(raw, MinionDefinition.class, "", file, issues);
        checkBody(file, def.health, def.baseEntity, def.renderEntity, def.scale, def.reach, issues);
        if (!def.isCustom() && (def.scale != 1.0f || def.reach != 0f)) {
            issues.add(warn(file, "scale", "scale/reach only apply to custom minions (baseEntity \"custom\"); ignored"));
        }
        checkAbilities(file, "abilities", def.abilities, issues);
        checkMovement(file, def.movement, true, issues);
        checkPathfinding(file, def.pathfinding, issues);
        checkSkin(file, def.skin, issues);
        checkLoot(file, def.loot, issues);
        return new Checked<>(def, issues);
    }

    public static Checked<GlobalSettings> settings(String file, Reader reader) {
        List<ConfigIssue> issues = new ArrayList<>();
        JsonObject raw = parseObject(file, reader, issues);
        if (raw == null) return new Checked<>(null, issues);
        GlobalSettings s;
        try {
            s = GSON.fromJson(raw, GlobalSettings.class);
        } catch (RuntimeException e) {
            issues.add(error(file, "", "wrong value type: " + rootMessage(e) + "; defaults used"));
            return new Checked<>(null, issues);
        }
        unknownKeys(raw, GlobalSettings.class, "", file, issues);
        if (s.domainBreakVolume < 0 || s.domainBreakVolume > 100) {
            issues.add(warn(file, "domainBreakVolume", s.domainBreakVolume + " is outside 0-100; clamped"));
        }
        checkPathfinding(file, s.pathfinding, issues);
        return new Checked<>(s, issues);
    }

    // ── Whole config folder ──────────────────────────────────────────────────

    /**
     * Checks {@code bosses/}, {@code minions/} and {@code settings.json} under a config folder,
     * plus references between them and every schedule. Changes nothing that is loaded.
     */
    public static List<ConfigIssue> validateDirectory(Path configDir) {
        List<ConfigIssue> issues = new ArrayList<>();
        Map<String, BossDefinition> bosses = new LinkedHashMap<>();
        Map<String, String> bossFiles = new LinkedHashMap<>();
        Map<String, MinionDefinition> minions = new LinkedHashMap<>();

        for (Path f : jsonFiles(configDir.resolve("bosses"))) {
            String name = "bosses/" + f.getFileName();
            Checked<BossDefinition> c = read(f, name, issues, ConfigValidator::boss);
            if (c == null || c.value() == null) continue;
            String previous = bossFiles.putIfAbsent(c.value().id, name);
            if (previous != null) {
                issues.add(warn(name, "id", "boss id '" + c.value().id + "' is also used by " + previous + "; only one is loaded"));
                continue;
            }
            bosses.put(c.value().id, c.value());
        }
        Map<String, String> minionFiles = new LinkedHashMap<>();
        for (Path f : jsonFiles(configDir.resolve("minions"))) {
            String name = "minions/" + f.getFileName();
            Checked<MinionDefinition> c = read(f, name, issues, ConfigValidator::minion);
            if (c == null || c.value() == null) continue;
            String previous = minionFiles.putIfAbsent(c.value().id, name);
            if (previous != null) {
                issues.add(warn(name, "id", "minion id '" + c.value().id + "' is also used by " + previous + "; only one is loaded"));
                continue;
            }
            minions.put(c.value().id, c.value());
        }
        GlobalSettings settings = null;
        Path settingsFile = configDir.resolve("settings.json");
        if (Files.isRegularFile(settingsFile)) {
            Checked<GlobalSettings> c = read(settingsFile, "settings.json", issues, ConfigValidator::settings);
            if (c != null) settings = c.value();
        }

        issues.addAll(references(bosses, bossFiles, minions));
        for (String w : Schedules.collect(bosses, settings != null ? settings.schedules : null).warnings()) {
            issues.add(warn("schedules", "", w));
        }
        return issues;
    }

    /** Cross-file checks: minion ids, shield pylons, summon variants and rage names. */
    public static List<ConfigIssue> references(Map<String, BossDefinition> bosses, Map<String, String> bossFiles,
                                               Map<String, MinionDefinition> minions) {
        List<ConfigIssue> issues = new ArrayList<>();
        for (BossDefinition def : bosses.values()) {
            String file = bossFiles.getOrDefault(def.id, "boss " + def.id);
            if (def.phases != null) {
                for (int i = 0; i < def.phases.size(); i++) {
                    PhaseDefinition p = def.phases.get(i);
                    if (p == null) continue;
                    if (p.minions != null) {
                        for (int m = 0; m < p.minions.size(); m++) {
                            MinionEntry e = p.minions.get(m);
                            String field = "phases[" + i + "].minions[" + m + "]";
                            if (e == null) continue;
                            if (e.minionId != null && !e.minionId.isBlank()) {
                                if (!minions.containsKey(e.minionId)) {
                                    issues.add(warn(file, field + ".minionId", "no minion '" + e.minionId + "' in minions/; nothing spawns"));
                                }
                            } else if (e.entityType == null || e.entityType.isBlank()) {
                                issues.add(warn(file, field, "needs \"minionId\" or \"entityType\""));
                            } else if (!REGISTRY_ID.matcher(e.entityType).matches()) {
                                issues.add(warn(file, field + ".entityType", "'" + e.entityType + "' is not a valid entity id"));
                            }
                        }
                    }
                    if (p.shield != null && (p.shield.minion == null || !minions.containsKey(p.shield.minion))) {
                        issues.add(warn(file, "phases[" + i + "].shield.minion", "no minion '" + p.shield.minion + "' in minions/; shield has no pylons"));
                    }
                }
            }
            if (def.summon != null) {
                for (int s = 0; s < def.summon.size(); s++) {
                    SummonEntry e = def.summon.get(s);
                    if (e != null && e.variant != null && !e.variant.isEmpty() && BossVariants.find(def, e.variant) == null) {
                        issues.add(warn(file, "summon[" + s + "].variant", "unknown variant '" + e.variant + "'; the normal boss spawns"));
                    }
                }
            }
            if (def.antiCheese != null) {
                checkRageName(file, "antiCheese.vehicleRage", def.antiCheese.vehicleRage, def, issues);
                StuckConfig stuck = def.antiCheese.stuck;
                if (stuck != null) {
                    List<String> actions = new ArrayList<>();
                    if (stuck.actions != null) actions.addAll(stuck.actions);
                    if (stuck.escalation != null) stuck.escalation.forEach(step -> { if (step != null) actions.addAll(step); });
                    for (String a : actions) {
                        if (a == null) continue;
                        if (a.startsWith("rage:")) checkRageName(file, "antiCheese.stuck", a.substring(5), def, issues);
                        else if (!STUCK_ACTIONS.contains(a)) {
                            issues.add(warn(file, "antiCheese.stuck", "unknown action '" + a + "'; ignored"));
                        }
                    }
                }
            }
        }
        return issues;
    }

    // ── Checks ───────────────────────────────────────────────────────────────

    private static void checkBody(String file, float health, String baseEntity, String renderEntity,
                                  float scale, float reach, List<ConfigIssue> issues) {
        if (health <= 0) issues.add(warn(file, "health", health + " is not above 0; it may die instantly"));
        checkEntityId(file, "baseEntity", baseEntity, issues);
        checkEntityId(file, "renderEntity", renderEntity, issues);
        if (BodySize.scale(scale) != scale) {
            issues.add(warn(file, "scale", scale + " is outside " + BodySize.MIN_SCALE + "-" + BodySize.MAX_SCALE
                    + "; " + BodySize.scale(scale) + " is used"));
        }
        if (reach < 0 || reach > BodySize.MAX_REACH || Float.isNaN(reach)) {
            issues.add(warn(file, "reach", reach + " is outside 0-" + BodySize.MAX_REACH + "; " + BodySize.reach(reach) + " is used"));
        }
    }

    private static void checkEntityId(String file, String field, String id, List<ConfigIssue> issues) {
        if (id == null || id.isEmpty() || "custom".equalsIgnoreCase(id)) return;
        if (!REGISTRY_ID.matcher(id).matches()) {
            issues.add(warn(file, field, "'" + id + "' is not a valid entity id (lowercase, like minecraft:zombie)"));
        }
    }

    private static void checkAbilities(String file, String path, List<AbilityEntry> abilities, List<ConfigIssue> issues) {
        if (abilities == null) return;
        for (int i = 0; i < abilities.size(); i++) {
            AbilityEntry a = abilities.get(i);
            if (a == null) continue;
            String field = path + "[" + i + "]";
            checkAbilityType(file, field + ".type", a.type, issues);
            if ("domain".equals(a.type) && a.params != null && a.params.has("attacks")
                    && a.params.get("attacks").isJsonArray()) {
                JsonArray attacks = a.params.getAsJsonArray("attacks");
                for (int j = 0; j < attacks.size(); j++) {
                    JsonElement atk = attacks.get(j);
                    String type = atk.isJsonObject() && atk.getAsJsonObject().has("type")
                            ? atk.getAsJsonObject().get("type").getAsString() : null;
                    checkAbilityType(file, field + ".params.attacks[" + j + "].type", type, issues);
                }
            }
        }
    }

    private static void checkAbilityType(String file, String field, String type, List<ConfigIssue> issues) {
        if (type == null || type.isBlank()) {
            issues.add(warn(file, field, "missing ability type; ability skipped"));
        } else if (!AbilityTypes.KNOWN.contains(type)) {
            String hint = closest(type, AbilityTypes.KNOWN);
            issues.add(warn(file, field, "unknown ability '" + type + "'; ability skipped"
                    + (hint != null ? " (did you mean '" + hint + "'?)" : "")));
        }
    }

    private static void checkMovement(String file, String movement, boolean minion, List<ConfigIssue> issues) {
        if (movement == null || movement.isBlank()) return;
        String m = movement.toLowerCase(java.util.Locale.ROOT);
        if (BOSS_MOVEMENT.contains(m) || (minion && MINION_ONLY_MOVEMENT.contains(m))) return;
        issues.add(warn(file, "movement", "unknown movement '" + movement + "'; " + (minion ? "normal" : "side") + " is used"));
    }

    private static void checkPathfinding(String file, String mode, List<ConfigIssue> issues) {
        if (mode == null || mode.isBlank() || "vanilla".equalsIgnoreCase(mode) || "shared".equalsIgnoreCase(mode)) return;
        issues.add(warn(file, "pathfinding", "unknown mode '" + mode + "' (vanilla or shared); vanilla is used"));
    }

    private static void checkSkin(String file, SkinDefinition skin, List<ConfigIssue> issues) {
        if (skin == null || skin.type == null) return;
        if (!"player".equalsIgnoreCase(skin.type) && !"file".equalsIgnoreCase(skin.type)) {
            issues.add(warn(file, "skin.type", "unknown skin type '" + skin.type + "' (player or file); treated as player"));
        }
    }

    private static void checkLoot(String file, List<LootEntry> loot, List<ConfigIssue> issues) {
        if (loot == null) return;
        for (int i = 0; i < loot.size(); i++) {
            LootEntry e = loot.get(i);
            if (e == null) continue;
            boolean hasItem = e.item != null && !e.item.isBlank();
            boolean hasTool = e.toolId != null && !e.toolId.isBlank();
            if (!hasItem && !hasTool) {
                issues.add(warn(file, "loot[" + i + "]", "needs \"item\" or \"toolId\"; entry skipped"));
            } else if (hasItem && !REGISTRY_ID.matcher(e.item).matches()) {
                issues.add(warn(file, "loot[" + i + "].item", "'" + e.item + "' is not a valid item id"));
            }
        }
    }

    private static void checkRageName(String file, String field, String name, BossDefinition def, List<ConfigIssue> issues) {
        if (name == null || name.isEmpty()) return;
        if (def.rages == null || !def.rages.containsKey(name)) {
            issues.add(warn(file, field, "no rage '" + name + "' in this boss's \"rages\""));
        }
    }

    // ── Unknown keys ─────────────────────────────────────────────────────────

    /** Walks raw JSON next to the class Gson maps it to and reports keys Gson would ignore. */
    static void unknownKeys(JsonElement json, Type type, String path, String file, List<ConfigIssue> issues) {
        if (json == null || json.isJsonNull()) return;
        Class<?> cls = rawClass(type);
        if (cls == null) return;
        if (isConfigClass(cls)) {
            if (!json.isJsonObject()) return;
            Map<String, Field> fields = gsonFields(cls);
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                String child = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                Field f = fields.get(e.getKey());
                if (f == null) {
                    String hint = closest(e.getKey(), fields.keySet());
                    issues.add(warn(file, child, "unknown key; ignored" + (hint != null ? " (did you mean '" + hint + "'?)" : "")));
                } else {
                    unknownKeys(e.getValue(), f.getGenericType(), child, file, issues);
                }
            }
        } else if (List.class.isAssignableFrom(cls) && json.isJsonArray()) {
            Type element = typeArgument(type, 0);
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) unknownKeys(array.get(i), element, path + "[" + i + "]", file, issues);
        } else if (Map.class.isAssignableFrom(cls) && json.isJsonObject()) {
            Type value = typeArgument(type, 1);
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                unknownKeys(e.getValue(), value, path + "." + e.getKey(), file, issues);
            }
        }
    }

    /** A rage holds any phase field plus the rage-only fields. */
    private static void rageKeys(JsonObject rage, String path, String file, List<ConfigIssue> issues) {
        Map<String, Field> phase = gsonFields(PhaseDefinition.class);
        Map<String, Field> all = new LinkedHashMap<>(phase);
        all.putAll(gsonFields(RageDefinition.class));
        for (Map.Entry<String, JsonElement> e : rage.entrySet()) {
            String child = path + "." + e.getKey();
            Field f = all.get(e.getKey());
            if (f == null) {
                String hint = closest(e.getKey(), all.keySet());
                issues.add(warn(file, child, "unknown key; ignored" + (hint != null ? " (did you mean '" + hint + "'?)" : "")));
            } else {
                unknownKeys(e.getValue(), f.getGenericType(), child, file, issues);
            }
        }
    }

    private static boolean isConfigClass(Class<?> cls) {
        return cls.getName().startsWith("com.fiw.fiw_bosses.") && !cls.isEnum();
    }

    /** The JSON keys Gson reads for a class: every non-static, non-transient field, inherited too. */
    private static Map<String, Field> gsonFields(Class<?> cls) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                fields.putIfAbsent(f.getName(), f);
            }
        }
        return fields;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType p && p.getActualTypeArguments().length > index) {
            return p.getActualTypeArguments()[index];
        }
        return Object.class;
    }

    /** A known name within two edits of {@code value}, for "did you mean" hints. */
    static String closest(String value, Iterable<String> known) {
        String best = null;
        int bestDistance = 3;
        for (String k : known) {
            int d = distance(value.toLowerCase(java.util.Locale.ROOT), k.toLowerCase(java.util.Locale.ROOT));
            if (d < bestDistance) { bestDistance = d; best = k; }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Writes issues to the server log: errors as ERROR, the rest as WARN. */
    public static void log(List<ConfigIssue> issues) {
        for (ConfigIssue i : issues) {
            if (i.severity() == Severity.ERROR) com.fiw.fiw_bosses.core.FiwBossesCore.LOGGER.error("Config: {}", i.describe());
            else com.fiw.fiw_bosses.core.FiwBossesCore.LOGGER.warn("Config: {}", i.describe());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private interface Parser<T> { Checked<T> parse(String file, Reader reader); }

    private static <T> Checked<T> read(Path path, String name, List<ConfigIssue> issues, Parser<T> parser) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Checked<T> c = parser.parse(name, reader);
            issues.addAll(c.issues());
            return c;
        } catch (IOException e) {
            issues.add(error(name, "", "cannot read file: " + e.getMessage()));
            return null;
        }
    }

    private static List<Path> jsonFiles(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isRegularFile(p)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static JsonObject parseObject(String file, Reader reader, List<ConfigIssue> issues) {
        try {
            JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonObject()) return root.getAsJsonObject();
            issues.add(error(file, "", "not a JSON object; file skipped"));
        } catch (RuntimeException e) {
            issues.add(error(file, "", "invalid JSON: " + rootMessage(e) + "; file skipped"));
        }
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private static ConfigIssue warn(String file, String field, String message) {
        return new ConfigIssue(Severity.WARNING, file, field, message);
    }

    private static ConfigIssue error(String file, String field, String message) {
        return new ConfigIssue(Severity.ERROR, file, field, message);
    }
}
