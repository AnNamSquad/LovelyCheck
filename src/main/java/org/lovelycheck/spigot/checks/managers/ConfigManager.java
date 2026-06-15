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
        if (!masterConfig.isConfigurationSection("fingerprints")
                && !masterConfig.isConfigurationSection("hacks")
                && legacyChecks.exists()) {
            hacksConfig = YamlConfiguration.loadConfiguration(legacyChecks);
            plugin.getLogger().warning("Loaded legacy checks.yml. Move those settings into config.yml to use the unified config.");
        }

        loadHacks();
    }

    private void loadHacks() {
        hacks.clear();
        ConfigurationSection section = getSection(hacksConfig, "fingerprints", "hacks");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String displayName = getString(section, id, id + ".display-name", id + ".label");
            String key = section.getString(id + ".key", "");
            if (key.isBlank()) continue;
            DetectionMode mode;
            try {
                mode = DetectionMode.valueOf(
                        getString(section, "TRANSLATE", id + ".mode", id + ".type").toUpperCase());
            } catch (IllegalArgumentException e) {
                mode = DetectionMode.TRANSLATE;
            }
            boolean bypassProtection = getBoolean(section, false,
                    id + ".bypass-protection", id + ".bypass_protection");
            hacks.put(id, new HackDefinition(id, displayName, key, mode, bypassProtection));
        }
        plugin.getLogger().info("Loaded " + hacks.size() + " fingerprints.");
    }

    public Map<String, HackDefinition> getHacks()     { return hacks; }
    public HackDefinition getHack(String id)           { return hacks.get(id); }

    public List<HackDefinition> getDefaultLovelyCheck() {
        return resolveHackList("scans.manual.fingerprints", "scans.manual.default-fingerprints", "default-check-hacks");
    }
    public List<HackDefinition> getJoinLovelyCheck() {
        return resolveHackList("scans.join.fingerprints", "auto-check-on-join.hacks");
    }
    public List<HackDefinition> getFlagLovelyCheck() {
        return resolveHackList("scans.anticheat.fingerprints", "detect-flag.hacks");
    }

    private List<HackDefinition> resolveHackList(String... paths) {
        List<HackDefinition> result = new ArrayList<>();
        for (String id : getStringList(hacksConfig, paths)) {
            HackDefinition h = hacks.get(id);
            if (h != null) result.add(h);
        }
        return result;
    }

    public String getPrefix() {
        return getString(masterConfig, "<yellow>[lovelycheck] <gray>", "general.prefix", "prefix");
    }
    public String getLanguage() {
        return getString(masterConfig, "en", "general.language", "language");
    }

    public boolean isDiscordEnabled() {
        return getBoolean(masterConfig, false, "webhooks.detection.enabled", "discord.enabled");
    }
    public String getWebhookUrl() {
        return getString(masterConfig, "", "webhooks.detection.url", "webhooks.detection.webhook-url", "discord.webhook-url");
    }
    public int getEmbedColor() {
        return getInt(masterConfig, 16776960, "webhooks.detection.embed-color", "discord.embed-color");
    }
    public String getDiscordMessage() {
        return getString(masterConfig, "", "webhooks.detection.message", "discord.message");
    }

    public boolean isBedrockEnabled()        { return getBoolean(masterConfig, true, "bedrock.enabled"); }
    public List<String> getBedrockPrefixes() { return getStringList(masterConfig, "bedrock.prefixes"); }

    public boolean isCommandIfPositiveEnabled() {
        return getBoolean(hacksConfig, false, "enforcement.commands.detected.enabled", "command-if-positive.enabled");
    }
    public String getPositiveCommand() {
        return getString(hacksConfig, "", "enforcement.commands.detected.command", "command-if-positive.command");
    }

    public boolean isPunishmentEnabled() {
        return getPunishmentBoolean("enabled", false);
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
        List<String> durations = new ArrayList<>(getStringList(hacksConfig,
                "enforcement.punishment.durations", "punishment.durations"));
        if (durations.isEmpty()) {
            durations.addAll(hacksConfig.getStringList("punishments.durations"));
        }
        if (!durations.isEmpty()) {
            return durations;
        }

        ConfigurationSection section = getSection(hacksConfig,
                "enforcement.punishment.durations", "punishment.durations");
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

    public boolean isCommandIfProtectedEnabled() {
        return getBoolean(hacksConfig, false, "enforcement.commands.protected.enabled", "command-if-protected.enabled");
    }
    public String getProtectedCommand() {
        return getString(hacksConfig, "", "enforcement.commands.protected.command", "command-if-protected.command");
    }

    public boolean isCommandIfCleanEnabled() {
        return getBoolean(hacksConfig, false, "enforcement.commands.clean.enabled", "command-if-clean.enabled");
    }
    public String getCleanCommand() {
        return getString(hacksConfig, "", "enforcement.commands.clean.command", "command-if-clean.command");
    }

    public boolean isDetectFlagEnabled() { return getBoolean(hacksConfig, false, "scans.anticheat.enabled", "detect-flag.enabled"); }
    public boolean isGrimEnabled()       { return getBoolean(hacksConfig, true, "scans.anticheat.integrations.grim", "detect-flag.anticheats.grim"); }
    public boolean isVulcanEnabled()     { return getBoolean(hacksConfig, true, "scans.anticheat.integrations.vulcan", "detect-flag.anticheats.vulcan"); }
    public boolean isSpartanEnabled()    { return getBoolean(hacksConfig, true, "scans.anticheat.integrations.spartan", "detect-flag.anticheats.spartan"); }
    public long    getFlagCooldownHours(){ return getLong(hacksConfig, 24, "scans.anticheat.cooldown-hours", "detect-flag.cooldown-hours"); }

    public boolean isJoinCheckEnabled()  { return getBoolean(hacksConfig, false, "scans.join.enabled", "auto-check-on-join.enabled"); }
    public boolean isOnlyFirstJoin()     { return getBoolean(hacksConfig, false, "scans.join.only-first-join", "auto-check-on-join.only-first-join"); }
    public long getJoinCheckDelayTicks() { return getLong(hacksConfig, 40L, "scans.join.delay-ticks", "auto-check-on-join.delay-ticks"); }

    public boolean isSilentCheck() { return getBoolean(hacksConfig, false, "general.silent-check", "silent-check"); }
    public boolean isDetectTranslationMaskingEnabled() {
        return getBoolean(hacksConfig, true, "engine.translation-masking.enabled", "detect-translation-masking.enabled");
    }
    public int getTranslationMaskingMinimumChecks() {
        return Math.max(1, getInt(hacksConfig, 1, "engine.translation-masking.minimum-checks", "detect-translation-masking.minimum-checks"));
    }
    public boolean isTranslationMaskingPunishable() {
        return getBoolean(hacksConfig, false, "engine.translation-masking.punishable", "detect-translation-masking.punishable");
    }
    public String getTranslationMaskingDisplayName() {
        return getString(hacksConfig, "Translation Masking Bypass",
                "engine.translation-masking.display-name", "detect-translation-masking.display-name");
    }

    public int getTimeoutTicks()      { return getInt(hacksConfig, 200, "engine.timeout-ticks", "timeout-ticks"); }
    public int getBetweenSignTicks()  { return getInt(hacksConfig, 20, "engine.batch-interval-ticks", "between-sign-ticks"); }

    public boolean isConfirmationEnabled() {
        return getBoolean(masterConfig, true, "engine.confirmation.enabled", "double-confirmation.enabled");
    }

    public int getShieldTimeoutTicks() {
        return Math.max(20, getInt(masterConfig, 20, "engine.first-probe.timeout-ticks", "shield-detection.timeout-ticks"));
    }

    public int getShieldTimeoutBufferTicks() {
        return Math.max(10, getInt(masterConfig, 20, "engine.first-probe.buffer-ticks", "shield-detection.buffer-ticks"));
    }

    public String getLocaleWebhookUrl() {
        return getString(masterConfig, "", "webhooks.locale.url", "webhooks.locale.webhook-url", "discord.locale.webhook-url");
    }

    public int getLocaleEmbedColor() {
        return getInt(masterConfig, 8355711, "webhooks.locale.embed-color", "discord.locale.embed-color");
    }

    private boolean getPunishmentBoolean(String key, boolean fallback) {
        String friendly = "enforcement.punishment." + key;
        if (hacksConfig.contains(friendly)) {
            return hacksConfig.getBoolean(friendly, fallback);
        }
        String singular = "punishment." + key;
        if (hacksConfig.contains(singular)) {
            return hacksConfig.getBoolean(singular, fallback);
        }
        return hacksConfig.getBoolean("punishments." + key, fallback);
    }

    private String getPunishmentString(String key, String fallback) {
        String friendly = "enforcement.punishment." + key;
        if (hacksConfig.contains(friendly)) {
            return hacksConfig.getString(friendly, fallback);
        }
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

    private ConfigurationSection getSection(FileConfiguration config, String... paths) {
        for (String path : paths) {
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    private String getString(ConfigurationSection section, String fallback, String... paths) {
        for (String path : paths) {
            String value = section.getString(path);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    private boolean getBoolean(ConfigurationSection section, boolean fallback, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return section.getBoolean(path, fallback);
            }
        }
        return fallback;
    }

    private String getString(FileConfiguration config, String fallback, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    private List<String> getStringList(FileConfiguration config, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getStringList(path);
            }
        }
        return List.of();
    }

    private boolean getBoolean(FileConfiguration config, boolean fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getBoolean(path, fallback);
            }
        }
        return fallback;
    }

    private int getInt(FileConfiguration config, int fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private long getLong(FileConfiguration config, long fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getLong(path, fallback);
            }
        }
        return fallback;
    }

}
