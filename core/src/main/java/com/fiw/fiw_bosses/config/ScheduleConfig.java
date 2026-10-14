package com.fiw.fiw_bosses.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A scheduled world spawn. Lives in settings.json "schedules" (needs "id" and "boss") or in a
 * boss file as "schedule" (id and boss default to the boss's id). Timing is exactly one of
 * intervalMinutes or times. At most one boss from a schedule is alive at a time.
 */
public class ScheduleConfig {
    public String id;
    public String boss;
    public String variant;
    public boolean enabled = true;
    // One is picked at random for each spawn.
    public List<Location> locations = new ArrayList<>();
    // Every N minutes (counted across restarts).
    public int intervalMinutes = 0;
    // Or fixed daily times in the server's timezone, "HH:mm".
    public List<String> times;
    // Countdown broadcasts, seconds before the spawn.
    public List<Integer> announceSeconds = new ArrayList<>(List.of(600, 60, 10));
    // Placeholders: {boss}, {time}, {x}, {y}, {z}, {dimension}.
    public String announceMessage = "&e{boss}&e awakens in {time} at {x}, {y}, {z}!";
    public String spawnMessage = "&6{boss}&6 has awakened at {x}, {y}, {z}!";

    public static class Location {
        public String dimension = "minecraft:overworld";
        public double x;
        // Optional: absent = top block at x/z.
        public Double y;
        public double z;
    }

    public boolean hasInterval() {
        return intervalMinutes > 0;
    }

    public boolean hasTimes() {
        return times != null && !times.isEmpty();
    }
}
