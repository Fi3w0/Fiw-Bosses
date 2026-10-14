package com.fiw.fiw_bosses.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Merges a rage's JSON over an HP phase: every key the rage sets replaces the phase's value. */
public final class PhaseOverlay {

    private static final Gson GSON = new Gson();

    private PhaseOverlay() {}

    public static PhaseDefinition merge(PhaseDefinition phase, JsonObject rage) {
        JsonObject merged = phase != null ? GSON.toJsonTree(phase).getAsJsonObject() : new JsonObject();
        if (rage != null) {
            for (Map.Entry<String, JsonElement> e : rage.entrySet()) {
                if (e.getKey().equals("hpThresholdPercent")) continue;
                merged.add(e.getKey(), e.getValue());
            }
        }
        return GSON.fromJson(merged, PhaseDefinition.class);
    }

    public static RageDefinition rageFields(JsonObject rage) {
        RageDefinition r = rage != null ? GSON.fromJson(rage, RageDefinition.class) : null;
        return r != null ? r : new RageDefinition();
    }
}
