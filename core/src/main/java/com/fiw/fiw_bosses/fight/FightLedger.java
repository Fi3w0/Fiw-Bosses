package com.fiw.fiw_bosses.fight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-boss combat record: who dealt damage, who took damage, who died, and since when.
 * MC-free so it is unit-testable; the entity feeds it and persists it via {@link #encode()}.
 * Damage is stored in configured-HP units (see BossEntity health scaling).
 */
public final class FightLedger {

    public static final class Entry {
        public final UUID id;
        public String name;
        public double damageDealt;
        public double damageTaken;
        public int deaths;

        Entry(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long startTick = -1L;

    private Entry entry(UUID id, String name, long tick) {
        if (startTick < 0) startTick = tick;
        Entry e = entries.computeIfAbsent(id, k -> new Entry(k, name));
        if (name != null && !name.isEmpty()) e.name = name;
        return e;
    }

    public void recordDealt(UUID id, String name, double amount, long tick) {
        if (amount > 0) entry(id, name, tick).damageDealt += amount;
    }

    public void recordTaken(UUID id, String name, double amount, long tick) {
        if (amount > 0) entry(id, name, tick).damageTaken += amount;
    }

    public void recordDeath(UUID id, String name, long tick) {
        entry(id, name, tick).deaths++;
    }

    public boolean hasDealtDamage(UUID id) {
        Entry e = entries.get(id);
        return e != null && e.damageDealt > 0;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public void clear() {
        entries.clear();
        startTick = -1L;
    }

    public long durationTicks(long nowTick) {
        return startTick < 0 ? 0 : Math.max(0, nowTick - startTick);
    }

    public double totalDealt() {
        double total = 0;
        for (Entry e : entries.values()) total += e.damageDealt;
        return total;
    }

    /** Everyone who dealt damage, highest first. */
    public List<Entry> byDamageDealt() {
        return sorted(e -> e.damageDealt);
    }

    /** Everyone who took damage while engaged, highest first. */
    public List<Entry> byDamageTaken() {
        return sorted(e -> e.damageTaken);
    }

    /** Everyone who died during the fight, most deaths first. */
    public List<Entry> byDeaths() {
        return sorted(e -> e.deaths);
    }

    /** Players who dealt at least {@code minDamage} (and more than zero), highest first. */
    public List<Entry> eligible(double minDamage) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byDamageDealt()) {
            if (e.damageDealt >= minDamage) out.add(e);
        }
        return out;
    }

    private List<Entry> sorted(java.util.function.ToDoubleFunction<Entry> key) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (key.applyAsDouble(e) > 0) out.add(e);
        }
        out.sort(Comparator.comparingDouble(key).reversed());
        return out;
    }

    // ── Persistence (one string tag, version-neutral) ────────────────────────
    // Format: "startTick;uuid|name|dealt|taken|deaths;..." (player names are [A-Za-z0-9_]).

    public String encode() {
        StringBuilder sb = new StringBuilder().append(startTick);
        for (Entry e : entries.values()) {
            sb.append(';').append(e.id).append('|').append(clean(e.name))
                    .append('|').append(e.damageDealt).append('|').append(e.damageTaken)
                    .append('|').append(e.deaths);
        }
        return sb.toString();
    }

    public static FightLedger decode(String data) {
        FightLedger ledger = new FightLedger();
        if (data == null || data.isEmpty()) return ledger;
        String[] parts = data.split(";");
        try {
            ledger.startTick = Long.parseLong(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                String[] f = parts[i].split("\\|", -1);
                if (f.length != 5) continue;
                Entry e = new Entry(UUID.fromString(f[0]), f[1]);
                e.damageDealt = Double.parseDouble(f[2]);
                e.damageTaken = Double.parseDouble(f[3]);
                e.deaths = Integer.parseInt(f[4]);
                ledger.entries.put(e.id, e);
            }
        } catch (RuntimeException ignored) {
            // Corrupt data: keep whatever parsed cleanly.
        }
        return ledger;
    }

    private static String clean(String name) {
        return name == null ? "" : name.replace("|", "").replace(";", "");
    }
}
