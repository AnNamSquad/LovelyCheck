package org.lovelycheck.spigot.checks.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class SignUtil {

    public static Location findHiddenFakeSignLocation(Player player, int batchIndex) {
        Location base = player.getLocation();
        if (base.getWorld() == null) return null;

        int y = base.getWorld().getMinHeight();
        int maxY = base.getWorld().getMaxHeight() - 1;
        y = Math.min(maxY, y + Math.max(0, batchIndex));

        return new Location(base.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }
}
