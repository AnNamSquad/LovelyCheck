package org.lovelycheck.spigot.checks.managers;

import org.lovelycheck.spigot.LovelyCheckPlugin;
import org.lovelycheck.spigot.checks.DetectionMode;
import org.lovelycheck.spigot.checks.HackDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigManager {

    private static final List<String> DEFAULT_PUNISHMENT_DURATIONS =
            List.of("15m", "30m", "1d", "3d", "30d");

    private final LovelyCheckPlugin plugin;
    private FileConfiguration masterConfig;
    private FileConfiguration hacksConfig;
    private final Map<String, HackDefinition> hacks = new LinkedHashMap<>();

    public ConfigManager(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        masterConfig = plugin.getConfig();
        hacksConfig = masterConfig;
        File legacyChecks = new File(plugin.getDataFolder(), "checks.yml");
        if (!masterConfig.isConfigurationSection("hacks") && legacyChecks.exists()) {
            hacksConfig = YamlConfiguration.loadConfiguration(legacyChecks);
            plugin.getLogger().warning("Loaded legacy checks.yml. Move those settings into config.yml to use the unified config.");
        }

        loadHacks();
    }

    private void loadHacks() {
        hacks.clear();
        ConfigurationSection section = hacksConfig.getConfigurationSection("hacks");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String displayName = section.getString(id + ".display-name", id);
            String key = section.getString(id + ".key", "");
            if (key.isBlank()) continue;
            DetectionMode mode;
            try {
                mode = DetectionMode.valueOf(
                        section.getString(id + ".mode", "TRANSLATE").toUpperCase());
            } catch (IllegalArgumentException e) {
                mode = DetectionMode.TRANSLATE;
            }
            hacks.put(id, new HackDefinition(id, displayName, key, mode));
        }
        plugin.getLogger().info("Loaded " + hacks.size() + " hacks.");
    }

    public Map<String, HackDefinition> getHacks()     { return hacks; }
    public HackDefinition getHack(String id)           { return hacks.get(id); }

    public List<HackDefinition> getDefaultLovelyCheck() { return resolveHackList("default-check-hacks"); }
    public List<HackDefinition> getJoinLovelyCheck()    { return resolveHackList("auto-check-on-join.hacks"); }
    public List<HackDefinition> getFlagLovelyCheck()    { return resolveHackList("detect-flag.hacks"); }

    private List<HackDefinition> resolveHackList(String path) {
        List<HackDefinition> result = new ArrayList<>();
        for (String id : hacksConfig.getStringList(path)) {
            HackDefinition h = hacks.get(id);
            if (h != null) result.add(h);
        }
        return result;
    }

    public String getPrefix()    { return masterConfig.getString("prefix", "<yellow>[lovelycheck] <gray>"); }
    public String getLanguage()  { return masterConfig.getString("language", "en"); }

    public boolean isDiscordEnabled()   { return masterConfig.getBoolean("discord.enabled", false); }
    public String  getWebhookUrl()      { return masterConfig.getString("discord.webhook-url", ""); }
    public int     getEmbedColor()      { return masterConfig.getInt("discord.embed-color", 16776960); }
    public String  getDiscordMessage()  { return masterConfig.getString("discord.message", ""); }

    public boolean isBedrockEnabled()        { return masterConfig.getBoolean("bedrock.enabled", true); }
    public List<String> getBedrockPrefixes() { return masterConfig.getStringList("bedrock.prefixes"); }

    public boolean isCommandIfPositiveEnabled() { return hacksConfig.getBoolean("command-if-positive.enabled", false); }
    public String  getPositiveCommand()         { return hacksConfig.getString("command-if-positive.command", ""); }

    public boolean isPunishmentEnabled() {
        return getPunishmentBoolean("enabled", true);
    }


    public boolean isPunishmentKickFirst() {
        return getPunishmentBoolean("kick-first", true);
    }

    public String getPunishmentKickCommand() {
        return getPunishmentString("kick-command", "kick %player% %reason%");
    }

    public String getPunishmentCommand() {
        return getPunishmentString("command", "tempban %player% %duration% %reason%");
    }

    public String getPunishmentReason() {
        return getPunishmentString("reason", "Unallowed client/mod: %detections%");
    }

    public List<String> getPunishmentDurations() {
        List<String> durations = new ArrayList<>(hacksConfig.getStringList("punishment.durations"));
        if (durations.isEmpty()) {
            durations.addAll(hacksConfig.getStringList("punishments.durations"));
        }
        if (!durations.isEmpty()) {
            return durations;
        }

        ConfigurationSection section = hacksConfig.getConfigurationSection("punishment.durations");
        if (section == null) {
            section = hacksConfig.getConfigurationSection("punishments.durations");
        }
        if (section != null) {
            section.getKeys(false).stream()
                    .sorted(Comparator.comparingInt(this::parseDurationKey))
                    .map(section::getString)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .forEach(durations::add);
        }

        return durations.isEmpty() ? DEFAULT_PUNISHMENT_DURATIONS : durations;
    }

    public boolean isCommandIfProtectedEnabled() { return hacksConfig.getBoolean("command-if-protected.enabled", false); }
    public String  getProtectedCommand()         { return hacksConfig.getString("command-if-protected.command", ""); }

    public boolean isCommandIfCleanEnabled() { return hacksConfig.getBoolean("command-if-clean.enabled", false); }
    public String  getCleanCommand()         { return hacksConfig.getString("command-if-clean.command", ""); }

    public boolean isDetectFlagEnabled() { return hacksConfig.getBoolean("detect-flag.enabled", false); }
    public boolean isGrimEnabled()       { return hacksConfig.getBoolean("detect-flag.anticheats.grim", true); }
    public boolean isVulcanEnabled()     { return hacksConfig.getBoolean("detect-flag.anticheats.vulcan", true); }
    public boolean isSpartanEnabled()    { return hacksConfig.getBoolean("detect-flag.anticheats.spartan", true); }
    public long    getFlagCooldownHours(){ return hacksConfig.getLong("detect-flag.cooldown-hours", 24); }

    public boolean isJoinCheckEnabled()  { return hacksConfig.getBoolean("auto-check-on-join.enabled", false); }
    public boolean isOnlyFirstJoin()     { return hacksConfig.getBoolean("auto-check-on-join.only-first-join", false); }
    public long getJoinCheckDelayTicks() { return hacksConfig.getLong("auto-check-on-join.delay-ticks", 40L); }

    public boolean isSilentCheck() { return hacksConfig.getBoolean("silent-check", false); }
    public boolean isDetectTranslationMaskingEnabled() {
        return hacksConfig.getBoolean("detect-translation-masking.enabled", true);
    }
    public int getTranslationMaskingMinimumChecks() {
        return Math.max(1, hacksConfig.getInt("detect-translation-masking.minimum-checks", 1));
    }
    public boolean isTranslationMaskingPunishable() {
        return hacksConfig.getBoolean("detect-translation-masking.punishable", false);
    }
    public String getTranslationMaskingDisplayName() {
        return hacksConfig.getString("detect-translation-masking.display-name",
                "Translation Masking Bypass");
    }

    public int getTimeoutTicks()      { return hacksConfig.getInt("timeout-ticks", 200); }
    public int getBetweenSignTicks()  { return hacksConfig.getInt("between-sign-ticks", 20); }

    public boolean isConfirmationEnabled() {
        return masterConfig.getBoolean("double-confirmation.enabled", true);
    }

    public int getShieldTimeoutTicks() {
        return Math.max(20, masterConfig.getInt("shield-detection.timeout-ticks", 20));
    }

    public int getShieldTimeoutBufferTicks() {
        return Math.max(10, masterConfig.getInt("shield-detection.buffer-ticks", 20));
    }

    public String getLocaleWebhookUrl() {
        return masterConfig.getString("discord.locale.webhook-url", "");
    }

    public int getLocaleEmbedColor() {
        return masterConfig.getInt("discord.locale.embed-color", 8355711);
    }

    private boolean getPunishmentBoolean(String key, boolean fallback) {
        String singular = "punishment." + key;
        if (hacksConfig.contains(singular)) {
            return hacksConfig.getBoolean(singular, fallback);
        }
        return hacksConfig.getBoolean("punishments." + key, fallback);
    }

    private String getPunishmentString(String key, String fallback) {
        String singular = "punishment." + key;
        if (hacksConfig.contains(singular)) {
            return hacksConfig.getString(singular, fallback);
        }
        return hacksConfig.getString("punishments." + key, fallback);
    }

    private int parseDurationKey(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

}
