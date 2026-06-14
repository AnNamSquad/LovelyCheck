package org.lovelycheck.spigot;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.bedrock.BedrockDetector;
import org.lovelycheck.core.config.ConfigsManager;
import org.lovelycheck.core.config.Message;
import org.lovelycheck.spigot.hopper.LovelyCheckHopper;
import org.lovelycheck.spigot.listeners.LovelyCheckPlayerListeners;
import org.lovelycheck.spigot.listeners.PendingPayloads;
import org.lovelycheck.spigot.listeners.LunarApolloListener;
import org.lovelycheck.spigot.protocol.PacketEventsIntegration;
import org.lovelycheck.spigot.utils.logs.Logs;
import org.jetbrains.annotations.Nullable;

public class LovelyCheckConnectionPlugin extends JavaPlugin {

    private static LovelyCheckConnectionPlugin instance;
    private boolean packetEventsAvailable = false;
    @Nullable
    private PacketEventsIntegration packetEventsIntegration;
    @Nullable
    private LunarApolloListener lunarApolloListener;

    public LovelyCheckConnectionPlugin() throws NoSuchFieldException, IllegalAccessException {
        instance = this;
        Logs.enableFilter(this);
        ConfigsManager.init(getDataFolder());
        // Register dependencies with Hopper for auto-download
        LovelyCheckHopper.register(this);
    }

    @Override
    public void onLoad() {
        // Download and load dependencies via Hopper
        LovelyCheckHopper.download(this);

        // Check for PacketEvents availability and initialize if present
        // Must be done in onLoad() before onEnable()
        packetEventsAvailable = isPacketEventsPresent();
        if (packetEventsAvailable) {
            try {
                packetEventsIntegration = new PacketEventsIntegration(this);
                packetEventsIntegration.load();
            } catch (Throwable e) {
                getLogger().warning("Failed to initialize PacketEvents: " + e.getMessage());
                packetEventsAvailable = false;
                if (packetEventsIntegration != null) {
                    try {
                        packetEventsIntegration.unregister();
                    } catch (Throwable ignored) {}
                    packetEventsIntegration = null;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        // Re-check PacketEvents availability during onEnable() if not found during onLoad()
        // Paper's new plugin system may not have loaded packetevents' classes during our onLoad()
        if (!packetEventsAvailable) {
            if (packetEventsIntegration != null) {
                try {
                    packetEventsIntegration.unregister();
                } catch (Throwable ignored) {}
                packetEventsIntegration = null;
            }
            packetEventsAvailable = isPacketEventsPresent();
            if (packetEventsAvailable) {
                try {
                    packetEventsIntegration = new PacketEventsIntegration(this);
                    packetEventsIntegration.load();
                } catch (Throwable e) {
                    getLogger().warning("Failed to initialize PacketEvents: " + e.getMessage());
                    packetEventsAvailable = false;
                    if (packetEventsIntegration != null) {
                        try {
                            packetEventsIntegration.unregister();
                        } catch (Throwable ignored) {}
                        packetEventsIntegration = null;
                    }
                }
            }
        }

        if (!packetEventsAvailable) {
            if (LovelyCheckHopper.requiresRestart()) {
                getLogger().warning("PacketEvents was downloaded but requires a server restart to load.");
                getLogger().warning("Please restart your server to enable lovelycheck functionality.");
            } else if (!LovelyCheckHopper.isEnabled()) {
                getLogger().warning("PacketEvents is not installed and auto-download is disabled.");
                getLogger().warning("Please install one manually or enable auto_download_dependencies in lovelycheck.toml");
            } else {
                getLogger().severe("PacketEvents is not available! lovelycheck packet detection will be disabled.");
                getLogger().severe("Please install PacketEvents manually.");
            }
        }

        new Metrics(this, 2008);

        // Initialize bedrock detection (auto-detects Geyser/Floodgate APIs)
        BedrockDetector.initialize(getLogger());
        Bukkit.getPluginManager().registerEvents(new LovelyCheckPlayerListeners(), this);
        PendingPayloads.startPruningTask(this);

        if (packetEventsAvailable && packetEventsIntegration != null) {
            getLogger().info("Using PacketEvents for packet interception");
            try {
                packetEventsIntegration.register();
            } catch (Throwable e) {
                getLogger().severe("Failed to initialize PacketEvents: " + e.getMessage());
            }
        }

        lunarApolloListener = new LunarApolloListener(this);

        // Commands are registered by LovelyCheckPlugin as a single /lovelychecker command.
        Logs.logComponent(Message.PLUGIN_LOADED.toComponent());

        Bukkit.getOnlinePlayers().forEach(player -> LovelyCheckRegistry.registerPlayer(player.getUniqueId()));
    }

    @Override
    public void onDisable() {
        if (packetEventsIntegration != null) {
            packetEventsIntegration.unregister();
        }
        if (lunarApolloListener != null) {
            lunarApolloListener.unregister();
        }
        LovelyCheckRegistry.clear();
    }

    /**
     * Check if PacketEvents is present on the server.
     */
    private boolean isPacketEventsPresent() {
        // Check for PacketEvents plugin
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            return true;
        }
        // Also check if the PacketEvents classes are available (could be shaded)
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static LovelyCheckConnectionPlugin get() {
        return instance;
    }

    public boolean isPacketEventsAvailable() {
        return packetEventsAvailable
                && packetEventsIntegration != null
                && packetEventsIntegration.isReadyForListeners();
    }
}
