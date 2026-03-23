package com.merchant.monitor.notification;

import com.merchant.monitor.config.MonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final MonitorConfig config;
    private final HttpClient httpClient;

    public SlackNotifier(MonitorConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void send(String markdownText) {
        try {
            String jsonPayload = "{\"text\": " + escapeJson(markdownText) + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getNotification().getSlackWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Slack notification sent successfully");
            } else {
                log.error("Slack notification failed with status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send Slack notification", e);
        }
    }

    private String escapeJson(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
