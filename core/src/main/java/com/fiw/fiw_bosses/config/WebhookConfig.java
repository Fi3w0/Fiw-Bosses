package com.fiw.fiw_bosses.config;

/** Discord kill webhook (settings.json "webhooks", or a boss's own "webhook"). */
public class WebhookConfig {
    public String discord = "";
    // On a boss: false skips the webhook for that boss even when settings.json has one.
    public Boolean enabled;

    /** The URL to post to for a boss, or null for none. */
    public static String resolve(WebhookConfig boss, WebhookConfig global) {
        if (boss != null && Boolean.FALSE.equals(boss.enabled)) return null;
        if (boss != null && boss.discord != null && !boss.discord.isBlank()) return boss.discord.trim();
        if (global != null && !Boolean.FALSE.equals(global.enabled)
                && global.discord != null && !global.discord.isBlank()) return global.discord.trim();
        return null;
    }
}
