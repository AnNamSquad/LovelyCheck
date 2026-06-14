package org.lovelycheck.spigot;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.lovelycheck.spigot.LovelyCheckConnectionPlugin;
import org.lovelycheck.spigot.checks.HackDefinition;
import org.lovelycheck.spigot.checks.commands.LovelyCheckerCommand;
import org.lovelycheck.spigot.checks.listeners.AntiCheatListener;
import org.lovelycheck.spigot.checks.listeners.JoinListener;
import org.lovelycheck.spigot.checks.listeners.SignListener;
import org.lovelycheck.spigot.checks.managers.CheckManager;
import org.lovelycheck.spigot.checks.managers.ClientDataManager;
import org.lovelycheck.spigot.checks.managers.ConfigManager;
import org.lovelycheck.spigot.checks.managers.DatabaseManager;
import org.lovelycheck.spigot.checks.managers.MessageManager;
import org.lovelycheck.spigot.protocol.PacketEventsSignCheckPacketBridge;
import org.lovelycheck.spigot.protocol.SignCheckPacketBridge;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LovelyCheckPlugin extends LovelyCheckConnectionPlugin {

    private static LovelyCheckPlugin instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private CheckManager checkManager;
    private ClientDataManager clientDataManager;
    private SignCheckPacketBridge signCheckPacketBridge;
    private final Set<UUID> alertsDisabled = new HashSet<>();

    public LovelyCheckPlugin() throws NoSuchFieldException, IllegalAccessException {
        super();
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        enableLovelyCheckLayer();
    }

    @Override
    public void onDisable() {
        disableLovelyCheckLayer();
        super.onDisable();
    }

    private void enableLovelyCheckLayer() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        databaseManager = new DatabaseManager(this);
        clientDataManager = new ClientDataManager();
        checkManager = new CheckManager(this);
        enableSignCheckPacketBridge();

        LovelyCheckerCommand lovelyCheckerCommand = new LovelyCheckerCommand(this);
        registerCommand("lovelychecker", lovelyCheckerCommand, lovelyCheckerCommand);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new SignListener(this), this);
        pluginManager.registerEvents(new JoinListener(this), this);

        AntiCheatListener antiCheatListener = new AntiCheatListener(this);
        pluginManager.registerEvents(antiCheatListener, this);
        antiCheatListener.registerHooks();

        getLogger().info("lovelycheck sign-check layer enabled.");
    }

    private void disableLovelyCheckLayer() {
        if (checkManager != null) {
            checkManager.cleanup();
        }
        if (signCheckPacketBridge != null) {
            signCheckPacketBridge.unregister();
            signCheckPacketBridge = null;
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void enableSignCheckPacketBridge() {
        if (!isPacketEventsAvailable()) {
            return;
        }
        try {
            signCheckPacketBridge = new PacketEventsSignCheckPacketBridge(this);
            signCheckPacketBridge.register();
            getLogger().info("lovelycheck fake sign-check packet flow enabled.");
        } catch (Throwable e) {
            signCheckPacketBridge = null;
            getLogger().warning("Fake sign-check packet flow disabled: " + e.getMessage());
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public static LovelyCheckPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public boolean openFakeSignCheck(Player player, Location location, List<HackDefinition> batch, boolean includeControlLine) {
        return signCheckPacketBridge != null && signCheckPacketBridge.openFakeSign(player, location, batch, includeControlLine);
    }

    public void restoreFakeSignCheck(UUID playerUuid, Location location) {
        if (signCheckPacketBridge != null) {
            signCheckPacketBridge.restoreFakeSign(playerUuid, location);
        }
    }

    public ClientDataManager getClientDataManager() {
        return clientDataManager;
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        boolean defaultEnabled = getConfig().getBoolean("alerts.default-enabled", true);
        return defaultEnabled ? !alertsDisabled.contains(uuid) : alertsDisabled.contains(uuid);
    }

    public void toggleAlerts(UUID uuid) {
        if (!alertsDisabled.remove(uuid)) {
            alertsDisabled.add(uuid);
        }
    }
}
