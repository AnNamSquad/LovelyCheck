package org.lovelycheck.checks.listeners;

import org.lovelycheck.spigot.LovelyCheckPlugin;
import org.lovelycheck.checks.ClientType;
import org.lovelycheck.checks.HackDefinition;
import org.lovelycheck.checks.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JoinListener implements Listener {

    private final LovelyCheckPlugin plugin;
    private final Set<UUID> alreadyHackChecked = ConcurrentHashMap.newKeySet();

    public JoinListener(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Set<String> channels = player.getListeningPluginChannels();
            ClientType type = detectClientType(channels);
            plugin.getClientDataManager().setClientType(uuid, type);
            plugin.getLogger().info("[lovelycheck] " + player.getName() + " client type: " + type
                    + " channels=" + channels);
        }, 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getConfigManager().isJoinCheckEnabled()) {
            if (!plugin.getConfigManager().isOnlyFirstJoin() || alreadyHackChecked.add(uuid)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    java.util.List<HackDefinition> hacks = plugin.getConfigManager().getJoinLovelyCheck();
                    if (hacks.isEmpty()) return;
                    plugin.getMessageManager().broadcastAlerts(
                            plugin.getMessageManager().get("join-check", Map.of("player", player.getName())));
                    plugin.getCheckManager().startCheck(player, null, hacks, true, "Auto-join check");
                }, plugin.getConfigManager().getJoinCheckDelayTicks());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getClientDataManager().remove(event.getPlayer().getUniqueId());
    }

    private ClientType detectClientType(Set<String> channels) {
        for (String ch : channels) {
            String lower = ch.toLowerCase();
            if (lower.startsWith("fabric") || lower.contains("fabric-api")
                    || lower.contains("fabric-networking") || lower.contains("fabric-screen")) {
                return ClientType.FABRIC;
            }
        }
        for (String ch : channels) {
            String lower = ch.toLowerCase();
            if (lower.startsWith("fml") || lower.startsWith("forge")
                    || lower.contains("forge:") || lower.contains("fml:")) {
                return ClientType.FORGE;
            }
        }
        if (channels.isEmpty()) return ClientType.VANILLA;
        return ClientType.UNKNOWN;
    }
}
