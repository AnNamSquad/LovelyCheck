package org.lovelycheck.checks;

import java.util.Locale;

public class HackDefinition {

    private final String id;
    private final String displayName;
    private final String key;
    private final DetectionMode mode;
    private final String fallback;

    public HackDefinition(String id, String displayName, String key, DetectionMode mode) {
        this.id          = id;
        this.displayName = displayName;
        this.key         = key;
        this.mode        = mode;
        this.fallback    = "\u27e6NO_" + id.toUpperCase(Locale.ROOT).replace("-", "_") + "\u27e7";
    }

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public String getKey()         { return key; }
    public DetectionMode getMode() { return mode; }
    public String getFallback()    { return fallback; }

    public boolean matchesModId(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        String normalizedModId = normalizeToken(modId);
        if (normalizedModId.isEmpty()) {
            return false;
        }
        return normalizeToken(id).equals(normalizedModId)
                || normalizeToken(displayName).equals(normalizedModId)
                || containsKeyToken(normalizedModId);
    }

    private boolean containsKeyToken(String normalizedModId) {
        for (String token : key.split("[^A-Za-z0-9_-]+")) {
            if (normalizeToken(token).equals(normalizedModId)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeToken(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
