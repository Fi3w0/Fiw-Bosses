package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.data.SpawnRules;
import com.fiw.fiw_bosses.text.TextCodes;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the Discord webhook payload (one embed) for a boss kill. */
public final class DiscordEmbed {

    private static final Gson GSON = new Gson();

    private DiscordEmbed() {}

    public record Kill(String bossName, String variant, String killer, long durationTicks,
                       List<FightLedger.Entry> byDamage, double totalDealt,
                       Map<String, String> uniqueDrops /* player -> item */, boolean fastestKill, int color, int topCount) {}

    public static String build(Kill k) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", plain(k.bossName()) + " defeated");
        embed.addProperty("color", k.color());
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        field(fields, "Killing blow", k.killer() != null ? escape(k.killer()) : "—", true);
        field(fields, "Fight time", SpawnRules.formatDuration(k.durationTicks() * 50L), true);
        if (k.variant() != null && !k.variant().isEmpty()) field(fields, "Variant", escape(k.variant()), true);

        StringBuilder top = new StringBuilder();
        int rank = 0;
        for (FightLedger.Entry e : k.byDamage()) {
            if (rank >= Math.max(1, k.topCount())) break;
            rank++;
            double share = k.totalDealt() > 0 ? e.damageDealt * 100.0 / k.totalDealt() : 0;
            top.append(rank).append(". ").append(escape(e.name)).append(" — ")
                    .append(String.format(Locale.ROOT, "%,.0f (%.0f%%)", e.damageDealt, share)).append('\n');
        }
        if (top.length() > 0) field(fields, "Top damage", top.toString().trim(), false);

        if (k.uniqueDrops() != null && !k.uniqueDrops().isEmpty()) {
            StringBuilder drops = new StringBuilder();
            k.uniqueDrops().forEach((player, item) ->
                    drops.append(escape(plain(item))).append(" → ").append(escape(plain(player))).append('\n'));
            field(fields, "Unique drops", drops.toString().trim(), false);
        }
        embed.add("fields", fields);
        if (k.fastestKill()) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "New fastest kill!");
            embed.add("footer", footer);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", "FIW Bosses");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return GSON.toJson(payload);
    }

    /** Text without & color codes. */
    public static String plain(String text) {
        StringBuilder sb = new StringBuilder();
        for (TextCodes.Span span : TextCodes.parse(text)) sb.append(span.text());
        return sb.toString();
    }

    /** Escapes Discord markdown so names like Big_Steve_ show as typed. */
    static String escape(String s) {
        return s.replaceAll("([\\\\*_~`|>])", "\\\\$1");
    }

    private static void field(JsonArray fields, String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value.length() > 1024 ? value.substring(0, 1021) + "..." : value);
        f.addProperty("inline", inline);
        fields.add(f);
    }

    /** Discord color for a boss bar color name. */
    public static int colorFor(String barColor) {
        if (barColor == null) return 0x9B59B6;
        return switch (barColor.toUpperCase(Locale.ROOT)) {
            case "PINK" -> 0xFF69B4;
            case "BLUE" -> 0x3498DB;
            case "RED" -> 0xE74C3C;
            case "GREEN" -> 0x2ECC71;
            case "YELLOW" -> 0xF1C40F;
            case "WHITE" -> 0xECF0F1;
            default -> 0x9B59B6;
        };
    }
}
