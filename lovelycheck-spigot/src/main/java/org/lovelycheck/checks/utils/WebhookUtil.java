package org.lovelycheck.checks.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class WebhookUtil {

    public static void sendResult(String webhookUrl, int color, String messageTemplate,
                                  String playerName, String checkerName, String reason,
                                  String hacksChecked, String resultText) {
        if (!isValid(webhookUrl)) return;
        String description = messageTemplate
                .replace("&name&",    playerName)
                .replace("&checker&", checkerName)
                .replace("&reason&",  reason)
                .replace("&hacks&",   hacksChecked)
                .replace("&results&", resultText);
        sendRaw(webhookUrl, color, description);
    }

    public static void sendRaw(String webhookUrl, int color, String description) {
        if (!isValid(webhookUrl)) return;
        String json = "{\"embeds\":[{"
                + "\"title\":\"lovelycheck report\","
                + "\"description\":\"" + escapeJson(description) + "\","
                + "\"color\":" + color + ","
                + "\"footer\":{\"text\":\"lovelycheck - sign translation exploit\"},"
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}]}";
        sendJson(webhookUrl, json);
    }

    private static boolean isValid(String url) {
        return url != null && !url.isBlank() && !url.contains("CHANGE_ME");
    }

    private static void sendJson(String webhookUrl, String json) {
        new Thread(() -> {
            try {
                HttpURLConnection conn =
                        (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "lovelycheck/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code < 200 || code >= 300)
                    System.err.println("[lovelycheck] Webhook HTTP " + code);
            } catch (Exception e) {
                System.err.println("[lovelycheck] Webhook error: " + e.getMessage());
            }
        }, "lovelycheck-webhook").start();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
