package org.lovelycheck.spigot.listeners;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.Action;
import org.lovelycheck.core.lunar.LunarActionTrigger;
import org.lovelycheck.core.lunar.LunarApolloHandshakeParser;
import org.lovelycheck.core.lunar.LunarHandshakeProcessor;
import org.lovelycheck.core.lunar.LunarHandshakeResult;
import org.lovelycheck.spigot.LovelyCheckConnectionPlugin;
import org.lovelycheck.spigot.utils.logs.Logs;

import java.util.List;

public final class LunarApolloListener implements PluginMessageListener {

    private final JavaPlugin plugin;

    public LunarApolloListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,
                LunarApolloHandshakeParser.CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,
                LunarApolloHandshakeParser.CHANNEL);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin,
                LunarApolloHandshakeParser.CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin,
                LunarApolloHandshakeParser.CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!LunarApolloHandshakeParser.CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }

        LunarApolloHandshakeParser.parseMods(message).ifPresent(mods -> {
            LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(player.getUniqueId());
            LunarHandshakeResult result = LunarHandshakeProcessor.process(playerData, mods);
            if (!result.hasTriggers()) {
                return;
            }
            for (LunarActionTrigger trigger : result.getTriggers()) {
                runActions(trigger.getActions(), player, trigger.getName());
            }
        });
    }

    private void runActions(List<Action> actions, Player player, String checkName) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (Action action : actions) {
            performActions(action, player, checkName);
        }
    }

    private void performActions(Action action, Player player, String checkName) {
        TagResolver.Single[] templates = new TagResolver.Single[]{
                Placeholder.unparsed("player", player.getName()),
                Placeholder.parsed("name", checkName)
        };
        if (action.hasAlert()) {
            Logs.logComponent(action.getAlert(templates));
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (hasAlertPermission(admin)) {
                    admin.sendMessage(action.getAlert(templates));
                }
            }
        }
        if (hasBypassPermission(player)) {
            return;
        }

        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(player.getUniqueId());
        if (!isPlayerFullyOnline(player)) {
            playerData.queuePendingAction(() -> executeCommands(action, player, checkName));
            return;
        }

        executeCommands(action, player, checkName);
    }

    private boolean isPlayerFullyOnline(Player player) {
        Player onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
        return onlinePlayer != null && onlinePlayer.isOnline();
    }

    private void executeCommands(Action action, Player player, String checkName) {
        long delayTicks = action.getDelayTicks();

        Runnable commandRunner = () -> {
            Player onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                return;
            }
            if (hasBypassPermission(onlinePlayer)) {
                return;
            }

            for (String command : action.getConsoleCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        command.replace("<player>", onlinePlayer.getName())
                                .replace("<name>", checkName));
            }
            for (String command : action.getPlayerCommands()) {
                Bukkit.dispatchCommand(onlinePlayer,
                        command.replace("<player>", onlinePlayer.getName())
                                .replace("<name>", checkName));
            }
            for (String command : action.getOppedPlayerCommands()) {
                boolean op = onlinePlayer.isOp();
                onlinePlayer.setOp(true);
                Bukkit.dispatchCommand(onlinePlayer,
                        command.replace("<player>", onlinePlayer.getName())
                                .replace("<name>", checkName));
                onlinePlayer.setOp(op);
            }
        };

        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(LovelyCheckConnectionPlugin.get(), commandRunner, delayTicks);
        } else {
            Bukkit.getScheduler().runTask(LovelyCheckConnectionPlugin.get(), commandRunner);
        }
    }

    private boolean hasAlertPermission(Player player) {
        return player.hasPermission("lovelycheck.alert") || player.hasPermission("lovelychecker.alert");
    }

    private boolean hasBypassPermission(Player player) {
        return player.hasPermission("lovelycheck.bypass") || player.hasPermission("lovelychecker.bypass");
    }
}
