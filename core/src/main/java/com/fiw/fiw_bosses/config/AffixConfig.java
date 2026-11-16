package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

/** A boss's spawn affixes: every spawn rolls from {@link #pool} (see {@link BossAffixes}). */
public class AffixConfig {
    // Chance (0-1) that a spawn rolls any affixes at all.
    public float chance = 1.0f;
    // How many different affixes one spawn rolls (at most the pool size).
    public int count = 1;
    // The affixes to pick from, by weight and without repeats.
    public List<AffixDefinition> pool = new ArrayList<>();
    // Boss name with affixes: {affixes} = the rolled names, {name} = the boss's own name.
    public String nameFormat = "{affixes} {name}";
}
