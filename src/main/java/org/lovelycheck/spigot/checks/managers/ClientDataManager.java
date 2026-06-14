package org.lovelycheck.spigot.checks.managers;

import org.lovelycheck.spigot.checks.ClientType;
import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.bedrock.BedrockDetector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDataManager {

    private final Map<UUID, ClientType> clientTypes = new ConcurrentHashMap<>();

    public void setClientType(UUID uuid, ClientType type) {
        clientTypes.put(uuid, type);
    }

    public ClientType getClientType(UUID uuid) {
        return clientTypes.getOrDefault(uuid, ClientType.UNKNOWN);
    }

    public boolean isBedrock(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayerIfPresent(uuid);
        if (playerData != null && playerData.isBedrockDetected()) {
            clientTypes.put(uuid, ClientType.BEDROCK);
            return true;
        }

        String source = BedrockDetector.detectSource(uuid);
        if (source == null) {
            return false;
        }

        LovelyCheckRegistry.getPlayer(uuid).markBedrockDetected(source);
        clientTypes.put(uuid, ClientType.BEDROCK);
        return true;
    }

    public void remove(UUID uuid) {
        clientTypes.remove(uuid);
    }
}
