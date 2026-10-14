package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.core.FiwBossesCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts webhook payloads asynchronously: the server thread only queues the request.
 * One attempt with a timeout, no retries. The URL is a secret and is never logged.
 */
public final class WebhookSender {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private WebhookSender() {}

    public static void post(String url, String json) {
        if (url == null || !url.startsWith("https://")) {
            FiwBossesCore.LOGGER.warn("Ignoring Discord webhook: the URL must start with https://");
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (IllegalArgumentException e) {
            FiwBossesCore.LOGGER.warn("Ignoring Discord webhook: the URL is not valid");
            return;
        }
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                FiwBossesCore.LOGGER.warn("Discord webhook failed: {}", error.getClass().getSimpleName());
            } else if (response.statusCode() >= 300) {
                FiwBossesCore.LOGGER.warn("Discord webhook returned HTTP {}", response.statusCode());
            }
        });
    }
}
