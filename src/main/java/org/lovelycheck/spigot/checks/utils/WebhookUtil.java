package org.lovelycheck.spigot.checks.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class WebhookUtil {

    private static final java.util.concurrent.ThreadPoolExecutor executor;
    static {
        java.util.concurrent.ThreadPoolExecutor tpe = new java.util.concurrent.ThreadPoolExecutor(
                1, 2,
                60L, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "lovelycheck-webhook-thread");
                    t.setDaemon(true);
                    return t;
                },
                new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
        );
        tpe.allowCoreThreadTimeOut(true);
        executor = tpe;
    }

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

    public static void sendDetectionReport(String webhookUrl, int color, String playerName,
                                           String checkerName, String reason, java.util.List<String> confirmedModules) {
        if (!isValid(webhookUrl)) return;

        String modulesText = String.join(", ", confirmedModules);
        StringBuilder evidence = new StringBuilder();
        for (String mod : confirmedModules) {
            evidence.append(mod).append(": **DETECTED**\\n");
        }

        String json = "{"
                + "\"embeds\":[{"
                + "\"title\":\"ZD — Detection Report\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + "{\"name\":\"Target\",\"value\":\"" + escapeJson(playerName) + "\",\"inline\":true},"
                + "{\"name\":\"Operator\",\"value\":\"" + escapeJson(checkerName) + "\",\"inline\":true},"
                + "{\"name\":\"Trigger\",\"value\":\"" + escapeJson(reason) + "\",\"inline\":true},"
                + "{\"name\":\"Confirmed Modules\",\"value\":\"" + escapeJson(modulesText) + "\",\"inline\":false},"
                + "{\"name\":\"Evidence\",\"value\":\"" + escapeJson(evidence.toString()) + "\",\"inline\":false}"
                + "],"
                + "\"footer\":{\"text\":\"ZenithDetector • store.zenithmc.it\"},"
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}]}";

        sendJson(webhookUrl, json);
    }

    public static void sendLocaleReport(String webhookUrl, int color, String playerName,
                                        String checkerName, String detectedLocale) {
        if (!isValid(webhookUrl)) return;

        String json = "{"
                + "\"embeds\":[{"
                + "\"title\":\"ZD — Locale Report\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + "{\"name\":\"Target\",\"value\":\"" + escapeJson(playerName) + "\",\"inline\":true},"
                + "{\"name\":\"Operator\",\"value\":\"" + escapeJson(checkerName) + "\",\"inline\":true},"
                + "{\"name\":\"Detected Locale\",\"value\":\"" + escapeJson(detectedLocale) + "\",\"inline\":true}"
                + "],"
                + "\"footer\":{\"text\":\"ZenithDetector • store.zenithmc.it\"},"
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}]}";

        sendJson(webhookUrl, json);
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
        executor.submit(() -> {
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
        });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
