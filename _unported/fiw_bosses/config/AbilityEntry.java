package com.fiw.fiw_bosses.config;

import com.google.gson.JsonObject;

public class AbilityEntry {
    public String type;
    public int cooldownTicks = 60;
    public JsonObject params = new JsonObject();
}
