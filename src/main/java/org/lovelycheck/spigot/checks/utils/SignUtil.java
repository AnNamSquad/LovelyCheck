package org.lovelycheck.spigot.checks.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SignUtil {

    public static void setAllowedEditor(Location loc, UUID playerUUID, Plugin plugin) {
        try {
            Object world = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            Object bp = bpClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Method gbe = Arrays.stream(world.getClass().getMethods())
                    .filter(m -> m.getName().equals("getBlockEntity") && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (gbe == null) return;
            Object be = gbe.invoke(world, bp);
            if (be == null) return;
            for (Method m : be.getClass().getMethods()) {
                if (m.getName().equals("setAllowedPlayerEditor") && m.getParameterCount() == 1) {
                    m.invoke(be, playerUUID);
                    return;
                }
            }
            for (Field f : getAllFields(be.getClass())) {
                if (f.getType().equals(UUID.class)) {
                    f.setAccessible(true);
                    f.set(be, playerUUID);
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[lovelycheck] setAllowedEditor: " + e.getMessage());
        }
    }

    public static void sendBlockEntityPacket(Player player, Location loc, Plugin plugin) {
        try {
            Object world = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            Object bp = bpClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Method gbe = Arrays.stream(world.getClass().getMethods())
                    .filter(m -> m.getName().equals("getBlockEntity") && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (gbe == null) return;
            Object be = gbe.invoke(world, bp);
            if (be == null) return;
            Class<?> pktClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket");
            Method create = Arrays.stream(pktClass.getMethods())
                    .filter(m -> m.getName().equals("create") && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (create == null) return;
            sendPacket(player, create.invoke(null, be), plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("[lovelycheck] sendBlockEntityPacket: " + e.getMessage());
        }
    }

    public static void sendOpenSignPacket(Player player, Location loc, Plugin plugin) {
        try {
            Class<?> bpClass  = Class.forName("net.minecraft.core.BlockPos");
            Class<?> pktClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket");
            Object bp     = bpClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Object packet = pktClass.getConstructor(bpClass, boolean.class).newInstance(bp, true);
            sendPacket(player, packet, plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("[lovelycheck] sendOpenSignPacket: " + e.getMessage());
        }
    }

    public static void sendPacket(Player player, Object packet, Plugin plugin) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = null;
            for (String name : new String[]{"connection", "networkManager", "playerConnection"}) {
                try {
                    Field f;
                    try { f = handle.getClass().getField(name); }
                    catch (NoSuchFieldException ex) {
                        f = handle.getClass().getDeclaredField(name);
                        f.setAccessible(true);
                    }
                    Object v = f.get(handle);
                    if (v != null) { conn = v; break; }
                } catch (Exception ignored) {}
            }
            if (conn == null) throw new IllegalStateException("connection not found");
            Method send = null;
            for (Method m : conn.getClass().getMethods())
                if (m.getName().equals("send") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(packet.getClass())) {
                    send = m; break;
                }
            if (send == null)
                for (Method m : conn.getClass().getMethods())
                    if (m.getName().equals("send") && m.getParameterCount() == 1) { send = m; break; }
            if (send == null) throw new IllegalStateException("send() not found");
            send.invoke(conn, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("[lovelycheck] sendPacket: " + e.getMessage());
        }
    }

    /**
     * Finds a location to place the real-sign probe block.
     *
     * Primary strategy: absolute bottom of the world (getMinHeight()). This is at or
     * below the bedrock floor in every dimension, so players can never see the sign
     * appear. The original block state (bedrock / whatever is there) is always saved
     * and restored by CheckManager, so this is safe.
     *
     * Fallback (should rarely trigger): scan above the player's head if the world
     * somehow has no valid minimum height.
     */
    public static Location findAirBlock(Player player) {
        Location base = player.getLocation().clone();
        org.bukkit.World world = base.getWorld();

        // --- Primary: place at world bottom, below bedrock --- //
        if (world != null) {
            int minY = world.getMinHeight();
            if (minY < world.getMaxHeight()) {
                return new Location(world, base.getBlockX(), minY, base.getBlockZ());
            }
        }

        // --- Fallback: scan above the player --- //
        for (int dy = 3; dy <= 6; dy++) {
            Location loc = base.clone().add(0, dy, 0);
            if (canUseProbeBlock(loc)) return loc;
        }
        int[][] offsets = {
                {1,3,0},{-1,3,0},{0,3,1},{0,3,-1},
                {1,2,0},{-1,2,0},{0,2,1},{0,2,-1},
                {2,3,0},{-2,3,0},{0,3,2},{0,3,-2}
        };
        for (int[] off : offsets) {
            Location loc = base.clone().add(off[0], off[1], off[2]);
            if (canUseProbeBlock(loc)) return loc;
        }
        return null;
    }

    public static Location findHiddenFakeSignLocation(Player player, int batchIndex) {
        Location base = player.getLocation();
        if (base.getWorld() == null) return null;

        int y = base.getWorld().getMinHeight();
        int maxY = base.getWorld().getMaxHeight() - 1;
        y = Math.min(maxY, y + Math.max(0, batchIndex));

        return new Location(base.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }

    private static boolean canUseProbeBlock(Location loc) {
        if (loc.getWorld() == null) return false;
        int y = loc.getBlockY();
        if (y < loc.getWorld().getMinHeight() || y >= loc.getWorld().getMaxHeight()) return false;
        return loc.getBlock().getType().isAir();
    }

    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
