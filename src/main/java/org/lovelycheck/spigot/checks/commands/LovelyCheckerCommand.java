package org.lovelycheck.spigot.checks.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.ConfigsManager;
import org.lovelycheck.spigot.LovelyCheckPlugin;
import org.lovelycheck.spigot.commands.BukkitCommandFallback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class LovelyCheckerCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("scan", "reload", "alerts", "check", "list", "inv", "lang", "checklang");

    private final LovelyCheckPlugin plugin;
    private final ChecksCommand scanCommand;
    private final AlertsCommand alertsCommand;
    private final BukkitCommandFallback connectionCommand;

    public LovelyCheckerCommand(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
        this.scanCommand = new ChecksCommand(plugin);
        this.alertsCommand = new AlertsCommand(plugin);
        this.connectionCommand = new BukkitCommandFallback(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);

        return switch (subcommand) {
            case "scan" -> scanCommand.onCommand(sender, command, label, tail);
            case "reload", "rl" -> reloadAll(sender);
            case "alerts", "alert" -> alertsCommand.onCommand(sender, command, label, tail);
            case "lang", "checklang" -> checkLang(sender, tail);
            case "check", "list", "inv" -> connectionCommand.onCommand(sender, command, label, args);
            case "help", "?" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean checkLang(CommandSender sender, String[] args) {
        if (!hasAnyPermission(sender, "lovelycheck.checklang", "lovelychecker.checklang",
                "lovelycheck.command.checklang", "lovelychecker.command.checklang")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix()
                    + "<red>Usage: /lovelychecker lang <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().get("player-not-found",
                    Map.of("player", args[0])));
            return true;
        }
        if (plugin.getCheckManager().isChecking(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().get("already-checking",
                    Map.of("player", target.getName())));
            return true;
        }

        plugin.getCheckManager().startLocaleProbe(target, sender instanceof Player p ? p : null);
        return true;
    }

    private boolean reloadAll(CommandSender sender) {
        if (!hasAnyPermission(sender, "lovelycheck.reload", "lovelychecker.reload",
                "lovelycheck.command.reload", "lovelychecker.command.reload")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }

        ConfigsManager.reload(plugin.getDataFolder());
        Bukkit.getOnlinePlayers().forEach(player -> LovelyCheckRegistry.registerPlayer(player.getUniqueId()));
        plugin.getConfigManager().reload();
        plugin.getMessageManager().load();
        sender.sendMessage(plugin.getMessageManager().get("reload-done"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(message("""
                <prefix><white>/lovelychecker scan <player> [hack1,hack2,...]
                <prefix><white>/lovelychecker reload
                <prefix><white>/lovelychecker alerts
                <prefix><white>/lovelychecker lang <player>
                <prefix><white>/lovelychecker check <player>
                <prefix><white>/lovelychecker list
                <prefix><white>/lovelychecker inv"""));
    }

    private Component message(String raw) {
        return MM.deserialize(raw.replace("<prefix>", plugin.getConfigManager().getPrefix()));
    }

    private boolean hasAnyPermission(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(prefix))
                    .filter(subcommand -> canUseSubcommand(sender, subcommand))
                    .collect(Collectors.toList());
        }
        if (args.length > 1) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            String[] tail = Arrays.copyOfRange(args, 1, args.length);
            return switch (subcommand) {
                case "scan" -> scanCommand.onTabComplete(sender, command, alias, tail);
                case "lang", "checklang" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(tail[0].toLowerCase()))
                        .collect(Collectors.toList());
                case "check", "list", "inv" -> connectionCommand.onTabComplete(sender, command, alias, args);
                default -> List.of();
            };
        }
        return List.of();
    }

    private boolean canUseSubcommand(CommandSender sender, String subcommand) {
        return switch (subcommand) {
            case "scan" -> hasAnyPermission(sender, "lovelycheck.check", "lovelychecker.check");
            case "reload" -> hasAnyPermission(sender, "lovelycheck.reload", "lovelychecker.reload",
                    "lovelycheck.command.reload", "lovelychecker.command.reload");
            case "alerts" -> sender instanceof Player
                    && hasAnyPermission(sender, "lovelycheck.alerts", "lovelychecker.alerts");
            case "check" -> hasAnyPermission(sender, "lovelycheck.command.check",
                    "lovelychecker.command.check", "lovelychecker.check");
            case "list" -> hasAnyPermission(sender, "lovelycheck.command.list",
                    "lovelychecker.command.list", "lovelychecker.list");
            case "inv" -> sender instanceof Player && hasAnyPermission(sender, "lovelycheck.command.inv",
                    "lovelychecker.command.inv", "lovelychecker.inv");
            case "lang", "checklang" -> hasAnyPermission(sender, "lovelycheck.checklang", "lovelychecker.checklang",
                    "lovelycheck.command.checklang", "lovelychecker.command.checklang");
            default -> false;
        };
    }
}
