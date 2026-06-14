package org.lovelycheck.spigot.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.ConfigsManager;
import org.lovelycheck.core.config.LunarConfig;
import org.lovelycheck.core.config.Message;
import org.lovelycheck.core.forge.ForgeConfig;
import org.lovelycheck.core.forge.ForgeModInfo;
import org.lovelycheck.core.lunar.LunarModInfo;
import org.lovelycheck.spigot.LovelyCheckPlugin;
import org.lovelycheck.spigot.utils.LovelyCheckInventoryView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles connection-detection review subcommands.
 */
public class BukkitCommandFallback implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "check", "list", "inv");

    private final JavaPlugin plugin;

    public BukkitCommandFallback(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUseConnectionCommands(sender)) {
            return true;
        }

        if (args.length == 0) {
            send(sender, Message.COMMANDS_HELP_SPIGOT);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "check" -> handleCheck(sender, args);
            case "list" -> handleList(sender);
            case "inv" -> handleInv(sender);
            default -> send(sender, Message.COMMANDS_HELP_SPIGOT);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!hasAnyPermission(sender, "lovelycheck.command.reload", "lovelychecker.command.reload",
                "lovelychecker.reload")) return;
        ConfigsManager.reload(plugin.getDataFolder());
        Bukkit.getOnlinePlayers().forEach(player -> LovelyCheckRegistry.registerPlayer(player.getUniqueId()));
        send(sender, Message.COMMANDS_RELOAD_SUCCESS);
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (!hasAnyPermission(sender, "lovelycheck.command.check", "lovelychecker.command.check",
                "lovelychecker.check")) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /lovelychecker check <player>"));
            return;
        }
        Player player = Bukkit.getPlayer(args[1]);
        if (player == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1]));
            return;
        }

        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(player.getUniqueId());
        List<String> signDetectedHacks = getPlugin().getCheckManager()
                .getLatestDetectedHacks(player.getUniqueId());
        boolean hasBedrock = playerData.isBedrockDetected();
        boolean hasSignDetections = !signDetectedHacks.isEmpty();
        boolean hasGenericChecks = !playerData.getGenericChecks().isEmpty();
        boolean showLunarMods = LunarConfig.isEnabled()
                && LunarConfig.shouldShowModsInCheck()
                && playerData.hasLunarModsData();
        boolean hasLunarMods = showLunarMods && !playerData.getLunarMods().isEmpty();

        if (hasBedrock || hasGenericChecks || hasSignDetections) {
            send(sender, Message.CHECK_MODS);
            if (hasBedrock) {
                sender.sendMessage(Message.MOD_LIST_FORMAT.toComponent(
                        Placeholder.unparsed("mod", formatBedrock(playerData))));
            }
            playerData.getGenericChecks().forEach(checkId -> {
                var check = LovelyCheckRegistry.getCheck(checkId);
                String modName = check != null ? check.getName() : checkId;
                sender.sendMessage(Message.MOD_LIST_FORMAT.toComponent(
                        Placeholder.parsed("mod", modName)));
            });
            for (String hackName : signDetectedHacks) {
                sender.sendMessage(Message.MOD_LIST_FORMAT.toComponent(
                        Placeholder.parsed("mod", hackName)));
            }
        } else if (!showLunarMods) {
            send(sender, Message.CHECK_NO_MODS);
        }

        if (showLunarMods) {
            if (hasLunarMods) {
                send(sender, Message.CHECK_LUNAR_MODS);
                for (LunarModInfo mod : playerData.getLunarMods()) {
                    sender.sendMessage(Message.LUNAR_MOD_LIST_FORMAT.toComponent(
                            Placeholder.parsed("mod", LunarConfig.formatMod(mod))));
                }
            } else {
                send(sender, Message.CHECK_LUNAR_NO_MODS);
            }
        }

        // Display Forge mods
        boolean showForgeMods = ForgeConfig.isEnabled()
                && ForgeConfig.shouldShowModsInCheck()
                && playerData.hasForgeModsData();
        boolean hasForgeMods = showForgeMods && !playerData.getForgeMods().isEmpty();

        if (showForgeMods) {
            if (hasForgeMods) {
                send(sender, Message.CHECK_FORGE_MODS);
                for (ForgeModInfo mod : playerData.getForgeMods()) {
                    sender.sendMessage(Message.FORGE_MOD_LIST_FORMAT.toComponent(
                            Placeholder.parsed("mod", ForgeConfig.formatMod(mod))));
                }
            } else {
                send(sender, Message.CHECK_FORGE_NO_MODS);
            }
        }
    }

    private void handleList(CommandSender sender) {
        if (!hasAnyPermission(sender, "lovelycheck.command.list", "lovelychecker.command.list",
                "lovelychecker.list")) return;
        var playersWithChecks = LovelyCheckRegistry.getPlayers().stream()
                .filter(player -> player.isBedrockDetected()
                        || !player.getGenericChecks().isEmpty()
                        || getPlugin().getCheckManager().hasLatestDetectedHacks(player.getUuid()))
                .collect(Collectors.toList());

        if (playersWithChecks.isEmpty()) {
            send(sender, Message.CHECK_PLAYERS_EMPTY);
            return;
        }

        send(sender, Message.CHECK_PLAYERS);
        playersWithChecks.forEach(playerData -> {
            sender.sendMessage(Message.PLAYER_LIST_FORMAT.toComponent(
                    Placeholder.parsed("player",
                            java.util.Objects.requireNonNullElse(
                                    Bukkit.getOfflinePlayer(playerData.getUuid()).getName(),
                                    playerData.getUuid().toString()))));
        });
    }

    private void handleInv(CommandSender sender) {
        if (!hasAnyPermission(sender, "lovelycheck.command.inv", "lovelychecker.command.inv",
                "lovelychecker.inv")) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return;
        }
        LovelyCheckInventoryView.openInvPage(player, 0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canUseConnectionCommands(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(prefix))
                    .filter(sub -> hasAnyPermission(sender, "lovelycheck.command." + sub,
                            "lovelychecker.command." + sub, "lovelychecker." + sub))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "check".equalsIgnoreCase(args[0])) {
            if (!hasAnyPermission(sender, "lovelycheck.command.check", "lovelychecker.command.check",
                    "lovelychecker.check")) return Collections.emptyList();
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private boolean canUseConnectionCommands(CommandSender sender) {
        return hasAnyPermission(sender,
                "lovelycheck.command",
                "lovelychecker.command",
                "lovelycheck.command.reload",
                "lovelychecker.command.reload",
                "lovelychecker.reload",
                "lovelycheck.command.check",
                "lovelychecker.command.check",
                "lovelychecker.check",
                "lovelycheck.command.list",
                "lovelychecker.command.list",
                "lovelychecker.list",
                "lovelycheck.command.inv",
                "lovelychecker.command.inv",
                "lovelychecker.inv");
    }

    private void send(CommandSender sender, Message message) {
        sender.sendMessage(message.toComponent());
    }

    private String formatBedrock(LovelyCheckPlayer playerData) {
        String source = playerData.getBedrockSource();
        return source != null ? "Bedrock Edition (" + source + ")" : "Bedrock Edition";
    }

    private LovelyCheckPlugin getPlugin() {
        return (LovelyCheckPlugin) plugin;
    }

    private boolean hasAnyPermission(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}
