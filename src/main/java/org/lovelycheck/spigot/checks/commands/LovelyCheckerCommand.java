package org.lovelycheck.spigot.checks.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.lovelycheck.spigot.checks.HackDefinition;
import org.lovelycheck.spigot.checks.commands.ChecksCommand;
import org.lovelycheck.spigot.checks.commands.AlertsCommand;
import org.lovelycheck.spigot.checks.managers.ConfigManager;
import org.lovelycheck.spigot.commands.BukkitCommandFallback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class LovelyCheckerCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("scan", "reload", "alerts", "check", "list", "inv", "lang", "checklang", "edit");

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
            case "edit" -> openEditDialog(sender);
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

    private boolean openEditDialog(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix() + "<red>Only players can use this command."));
            return true;
        }

        if (!hasAnyPermission(player, "lovelycheck.edit", "lovelychecker.edit", "lovelycheck.command.edit",
                "lovelychecker.command.edit")) {
            player.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }

        ConfigManager configManager = plugin.getConfigManager();
        List<DialogInput> inputs = new java.util.ArrayList<>();

        inputs.add(DialogInput.bool("join_check", Component.text("Auto Check on Join"))
                .initial(configManager.isJoinCheckEnabled())
                .build());
        inputs.add(DialogInput.bool("punishment", Component.text("Enable Punishments"))
                .initial(configManager.isPunishmentEnabled())
                .build());
        inputs.add(DialogInput.bool("silent", Component.text("Silent Checks"))
                .initial(configManager.isSilentCheck())
                .build());
        inputs.add(DialogInput.bool("detect_flag", Component.text("Anticheat Flag Checks"))
                .initial(configManager.isDetectFlagEnabled())
                .build());
        inputs.add(DialogInput.bool("double_confirm", Component.text("Double Confirmation"))
                .initial(configManager.isConfirmationEnabled())
                .build());

        Map<String, HackDefinition> registeredHacks = configManager.getHacks();
        List<HackDefinition> defaultHacks = configManager.getDefaultLovelyCheck();
        for (HackDefinition hack : registeredHacks.values()) {
            boolean active = defaultHacks.contains(hack);
            inputs.add(DialogInput.bool("hack_" + hack.getId().replace('-', '_'), Component.text("Check " + hack.getDisplayName()))
                    .initial(active)
                    .build());
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("LovelyCheck Configuration", NamedTextColor.GOLD))
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                                .tooltip(Component.text("Save configuration changes"))
                                .action(DialogAction.customClick((view, audience) -> {
                                    if (audience instanceof Player p) {
                                        boolean joinCheck = view.getBoolean("join_check") != null && view.getBoolean("join_check");
                                        boolean punishment = view.getBoolean("punishment") != null && view.getBoolean("punishment");
                                        boolean silent = view.getBoolean("silent") != null && view.getBoolean("silent");
                                        boolean detectFlag = view.getBoolean("detect_flag") != null && view.getBoolean("detect_flag");
                                        boolean doubleConfirm = view.getBoolean("double_confirm") != null && view.getBoolean("double_confirm");

                                        plugin.getConfig().set("auto-check-on-join.enabled", joinCheck);
                                        plugin.getConfig().set("punishment.enabled", punishment);
                                        plugin.getConfig().set("silent-check", silent);
                                        plugin.getConfig().set("detect-flag.enabled", detectFlag);
                                        plugin.getConfig().set("double-confirmation.enabled", doubleConfirm);

                                        List<String> enabledHacksList = new java.util.ArrayList<>();
                                        for (String id : registeredHacks.keySet()) {
                                            Boolean checked = view.getBoolean("hack_" + id.replace('-', '_'));
                                            if (checked != null && checked) {
                                                enabledHacksList.add(id);
                                            }
                                        }

                                        plugin.getConfig().set("default-check-hacks", enabledHacksList);
                                        plugin.getConfig().set("auto-check-on-join.hacks", enabledHacksList);
                                        plugin.getConfig().set("detect-flag.hacks", enabledHacksList);

                                        plugin.saveConfig();
                                        plugin.getConfigManager().reload();

                                        p.closeDialog();
                                        p.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix() + "<green>Configuration updated successfully."));
                                    }
                                }, ClickCallback.Options.builder().uses(1).build()))
                                .build(),
                        ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                                .tooltip(Component.text("Cancel changes"))
                                .build()
                ))
        );

        player.showDialog(dialog);
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
                <prefix><white>/lovelychecker inv
                <prefix><white>/lovelychecker edit"""));
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
            case "edit" -> sender instanceof Player && hasAnyPermission(sender, "lovelycheck.edit", "lovelychecker.edit",
                    "lovelycheck.command.edit", "lovelychecker.command.edit");
            default -> false;
        };
    }
}
