package com.fiw.fiw_bosses.config;

public class LootEntry {
    public String item;
    public String nbt;
    /** Optional Fiw Tools item id. When set, takes precedence over {@link #item} and {@link #nbt}. */
    public String toolId;
    public int count = 1;
    public float chance = 1.0f;
}
