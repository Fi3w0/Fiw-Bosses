package com.fiw.fiw_bosses.config;

public class EquipmentEntry {
    public String item;
    public String nbt;
    /** Optional Fiw Tools item id. When set, takes precedence over {@link #item} and {@link #nbt}. */
    public String toolId;
}
