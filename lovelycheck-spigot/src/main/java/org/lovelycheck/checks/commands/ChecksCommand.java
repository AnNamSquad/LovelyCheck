package org.lovelycheck.checks.commands;

import org.lovelycheck.spigot.LovelyCheckPlugin;
import org.lovelycheck.checks.HackDefinition;
import org.lovelycheck.checks.utils.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChecksCommand implements CommandExecutor, TabCompleter {

    private final LovelyCheckPlugin plugin;

    public ChecksCommand(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAnyPermission(sender, "lovelycheck.check", "lovelychecker.check")) {
            sender.sendMessage(MessageUtil.parse(plugin, "no-permission", Map.of()));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfigManager().getPrefix()
                            + "<red>Usage: /lovelychecker scan <player> [hack1,hack2,...]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(plugin, "player-not-found",
                    Map.of("player", args[0])));
            return true;
        }
        if (plugin.getCheckManager().isChecking(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.parse(plugin, "already-checking",
                    Map.of("player", target.getName())));
            return true;
        }

        List<HackDefinition> hacks;
        if (args.length >= 2) {
            hacks = new ArrayList<>();
            for (String id : args[1].split(",")) {
                HackDefinition h = plugin.getConfigManager().getHack(id.trim().toLowerCase());
                if (h == null) {
                    sender.sendMessage(MessageUtil.parse(plugin, "invalid-hack",
                            Map.of("hack", id.trim())));
                    return true;
                }
                hacks.add(h);
            }
        } else {
            hacks = plugin.getConfigManager().getDefaultLovelyCheck();
        }

        if (hacks.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfigManager().getPrefix() + "<red>No valid hacks to check."));
            return true;
        }

        Player initiator = (sender instanceof Player p) ? p : null;
        String reason = initiator != null
                ? "Manual check by " + initiator.getName() : "Console check";
        plugin.getCheckManager().startCheck(target, initiator, hacks, false, reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAnyPermission(sender, "lovelycheck.check", "lovelychecker.check")) return List.of();
        if (args.length == 1)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        if (args.length == 2) {
            String typed = args[1];
            String prefix  = typed.contains(",") ? typed.substring(0, typed.lastIndexOf(',') + 1) : "";
            String current = typed.contains(",") ? typed.substring(typed.lastIndexOf(',') + 1) : typed;
            return plugin.getConfigManager().getHacks().keySet().stream()
                    .filter(id -> id.startsWith(current.toLowerCase()))
                    .map(id -> prefix + id)
                    .collect(Collectors.toList());
        }
        return List.of();
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
