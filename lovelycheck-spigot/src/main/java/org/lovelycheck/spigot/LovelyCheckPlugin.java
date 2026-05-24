package org.lovelycheck.spigot;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.lovelycheck.spigot.LovelyCheckConnectionPlugin;
import org.lovelycheck.checks.commands.LovelyCheckerCommand;
import org.lovelycheck.checks.listeners.AntiCheatListener;
import org.lovelycheck.checks.listeners.JoinListener;
import org.lovelycheck.checks.listeners.SignListener;
import org.lovelycheck.checks.managers.CheckManager;
import org.lovelycheck.checks.managers.ClientDataManager;
import org.lovelycheck.checks.managers.ConfigManager;
import org.lovelycheck.checks.managers.DatabaseManager;
import org.lovelycheck.checks.managers.MessageManager;

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
        if (databaseManager != null) {
            databaseManager.close();
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
