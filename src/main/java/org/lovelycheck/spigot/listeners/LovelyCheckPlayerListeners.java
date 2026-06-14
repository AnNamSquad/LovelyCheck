package org.lovelycheck.spigot.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.BedrockConfig;
import org.lovelycheck.spigot.LovelyCheckInventoryHolder;
import org.lovelycheck.spigot.LovelyCheckConnectionPlugin;
import org.lovelycheck.core.bedrock.BedrockDetector;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.lovelycheck.spigot.utils.LovelyCheckInventoryView;

import org.lovelycheck.core.utils.JoinWebhook;

import java.util.Map;

public class LovelyCheckPlayerListeners implements Listener {

    private static final long JOIN_WEBHOOK_DELAY_TICKS = 20L;

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        LovelyCheckRegistry.registerPlayer(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(player.getUniqueId());

        // Capture player data on the main thread before scheduling the async webhook task.
        // Accessing Bukkit Player objects from async threads is unsafe and can cause errors.
        final String playerName = player.getName();
        final java.util.UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLaterAsynchronously(LovelyCheckConnectionPlugin.get(), () -> {
            JoinWebhook.send(playerName, playerUuid);
        }, JOIN_WEBHOOK_DELAY_TICKS);

        if (playerData.hasPendingActions()) {
            // Execute pending actions after a short delay to ensure player is fully initialized
            // Use at least 1 tick to ensure the join event completes
            // Each action will then apply its own configured delay before executing commands
            Bukkit.getScheduler().runTaskLater(LovelyCheckConnectionPlugin.get(),
                    playerData::executePendingActions, 1L);
        }

        Bukkit.getScheduler().runTaskLater(LovelyCheckConnectionPlugin.get(), () -> {
            if (!player.isOnline()) {
                return;
            }
            for (PendingPayloads.PendingPayload payload : PendingPayloads.drainFor(player.getUniqueId())) {
                PayloadProcessor.process(player, payload.getChannel(), payload.getMessage());
            }
            handleBedrockDetection(player);
        }, 1L);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        LovelyCheckRegistry.removePlayer(event.getPlayer().getUniqueId());
        PendingPayloads.clearFor(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClickInv(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof LovelyCheckInventoryHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;

        Player player = (Player) event.getWhoClicked();
        if (slot == 45 && holder.getPage() > 0) {
            LovelyCheckInventoryView.openInvPage(player, holder.getPage() - 1);
        } else if (slot == 53 && inv.getItem(53) != null) {
            LovelyCheckInventoryView.openInvPage(player, holder.getPage() + 1);
        }
    }

    private void handleBedrockDetection(Player player) {
        if (!BedrockConfig.isEnabled()) {
            return;
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(player.getUniqueId());
        if (playerData.isBedrockDetected()) {
            return;
        }

        String source = BedrockDetector.detectSource(player.getUniqueId());
        if (source == null) {
            return;
        }

        playerData.markBedrockDetected(source);
        String label = BedrockConfig.getLabel();
        PayloadProcessor.runActions(player, label, BedrockConfig.getBedrockActions(),
                Map.of("source", source),
                Placeholder.unparsed("source", source));
    }
}
