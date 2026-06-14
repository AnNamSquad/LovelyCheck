package org.lovelycheck.spigot.checks.managers;

import org.lovelycheck.spigot.LovelyCheckPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final LovelyCheckPlugin plugin;
    private ConfigurationSection messages;
    private final Map<String, String> fallbackMessages = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MessageManager(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        messages = plugin.getConfig().getConfigurationSection("messages");
        fallbackMessages.clear();
        loadBuiltInFallbacks();

        if (messages != null) {
            return;
        }

        String lang = plugin.getConfigManager().getLanguage();
        File file = new File(plugin.getDataFolder(), "messages/" + lang + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "messages/en.yml");
        }
        if (file.exists()) {
            messages = YamlConfiguration.loadConfiguration(file);
            plugin.getLogger().warning("Loaded legacy messages/" + file.getName()
                    + ". Move messages into config.yml to use the unified config.");
        }
    }

    public String getRaw(String key) {
        if (messages != null) {
            String value = messages.getString(key);
            if (value != null) {
                return value;
            }
        }
        return fallbackMessages.getOrDefault(key, "<red>Missing message: " + key);
    }

    public Component get(String key, Map<String, String> placeholders) {
        String prefix = plugin.getConfigManager().getPrefix();
        String raw = getRaw(key).replace("{prefix}", prefix);
        for (Map.Entry<String, String> e : placeholders.entrySet())
            raw = raw.replace("{" + e.getKey() + "}", e.getValue());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) raw = applyPapi(null, raw);
        return MM.deserialize(raw);
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public void broadcastAlerts(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers())
            if ((p.hasPermission("lovelycheck.alerts") || p.hasPermission("lovelychecker.alerts"))
                    && plugin.hasAlertsEnabled(p.getUniqueId()))
                p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private String applyPapi(Player player, String text) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papi.getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, text);
        } catch (Exception e) {
            return text;
        }
    }

    private void loadBuiltInFallbacks() {
        fallbackMessages.put("no-permission", "{prefix}<red>You don't have permission to do this.");
        fallbackMessages.put("player-not-found", "{prefix}<red>Player <white>{player} <red>not found.");
        fallbackMessages.put("already-checking", "{prefix}<yellow>{player} <white>is already being checked.");
        fallbackMessages.put("check-started", "{prefix}<white>Starting check on <yellow>{player}<white>...");
        fallbackMessages.put("check-complete", "{prefix}<white>Check complete for <yellow>{player}<white>:");
        fallbackMessages.put("check-timeout", "{prefix}<yellow>{player} <white>did not respond - marked as <red>PROTECTED<white>.");
        fallbackMessages.put("reload-done", "{prefix}<green>Configuration reloaded.");
        fallbackMessages.put("anticheat-trigger", "{prefix}<gray>Anticheat flagged <yellow>{player}<gray>. Queuing auto-check...");
        fallbackMessages.put("join-check", "{prefix}<gray>Auto-checking <yellow>{player} <gray>on join...");
        fallbackMessages.put("invalid-hack", "{prefix}<red>Unknown hack: <white>{hack}");
        fallbackMessages.put("bedrock-skip", "{prefix}<yellow>{player} <gray>is a Bedrock player - check skipped.");
        fallbackMessages.put("alerts-enabled", "{prefix}<green>Alerts enabled.");
        fallbackMessages.put("alerts-disabled", "{prefix}<red>Alerts disabled.");
    }
}
