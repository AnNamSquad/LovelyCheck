package org.lovelycheck.spigot.protocol;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.lovelycheck.checks.HackDefinition;

import java.util.List;
import java.util.UUID;

public interface SignCheckPacketBridge {

    void register();

    void unregister();

    boolean openFakeSign(Player player, Location location, List<HackDefinition> batch);

    void restoreFakeSign(UUID playerUuid, Location location);
}
