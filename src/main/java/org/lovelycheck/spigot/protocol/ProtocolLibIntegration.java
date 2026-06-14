package org.lovelycheck.spigot.protocol;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.lovelycheck.spigot.LovelyCheckConnectionPlugin;
import org.lovelycheck.spigot.listeners.CustomPayloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * Isolates ProtocolLib references to prevent class loading failures when ProtocolLib is not installed.
 * This class should only be loaded when ProtocolLib is confirmed to be available.
 */
public final class ProtocolLibIntegration {

    private final ProtocolManager protocolManager;
    private final CustomPayloadListener customPayloadListener;

    public ProtocolLibIntegration(LovelyCheckConnectionPlugin plugin) {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.customPayloadListener = new CustomPayloadListener(protocolManager, plugin);
    }

    public void register() {
        customPayloadListener.register();
    }

    public void unregister() {
        customPayloadListener.unregister();
    }

    @Nullable
    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }
}
