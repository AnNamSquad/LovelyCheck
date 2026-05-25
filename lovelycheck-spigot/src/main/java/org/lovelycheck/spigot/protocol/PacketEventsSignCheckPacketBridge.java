package org.lovelycheck.spigot.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.lovelycheck.checks.DetectionMode;
import org.lovelycheck.checks.HackDefinition;
import org.lovelycheck.spigot.LovelyCheckPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PacketEventsSignCheckPacketBridge implements SignCheckPacketBridge {

    private static final String CTRL_KEYBIND = "key.forward";

    private final LovelyCheckPlugin plugin;
    private final ConcurrentMap<UUID, FakeSignSession> sessions = new ConcurrentHashMap<>();
    private PacketListenerAbstract listener;

    public PacketEventsSignCheckPacketBridge(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register() {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getEventManager() == null) {
            throw new IllegalStateException("PacketEvents API is not initialized");
        }

        listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.UPDATE_SIGN) {
                    handleUpdateSign(event);
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    @Override
    public void unregister() {
        if (listener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
        sessions.clear();
    }

    @Override
    public boolean openFakeSign(Player player, Location location, List<HackDefinition> batch) {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getPlayerManager() == null) {
            return false;
        }

        Vector3i position = new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        FakeSignSession session = new FakeSignSession(location.clone(), position);
        sessions.put(player.getUniqueId(), session);

        try {
            var playerManager = PacketEvents.getAPI().getPlayerManager();
            WrappedBlockState signState = WrappedBlockState.getDefaultState(StateTypes.OAK_SIGN);

            playerManager.sendPacket(player, new WrapperPlayServerBlockChange(position, signState));
            playerManager.sendPacket(player, new WrapperPlayServerBlockEntityData(
                    position, BlockEntityTypes.SIGN, buildSignNbt(location, batch)));
            playerManager.sendPacket(player, new WrapperPlayServerOpenSignEditor(position, true));
            return true;
        } catch (Throwable e) {
            sessions.remove(player.getUniqueId(), session);
            plugin.getLogger().warning("[lovelycheck] fake sign packet flow failed for "
                    + player.getName() + ": " + e.getMessage());
            restoreClientBlock(player, location);
            return false;
        }
    }

    @Override
    public void restoreFakeSign(UUID playerUuid, Location location) {
        sessions.remove(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            restoreClientBlock(player, location);
        }
    }

    private void handleUpdateSign(PacketReceiveEvent event) {
        UUID playerUuid = event.getUser().getUUID();
        FakeSignSession session = sessions.get(playerUuid);
        if (session == null) {
            return;
        }

        WrapperPlayClientUpdateSign updateSign = new WrapperPlayClientUpdateSign(event);
        Vector3i updatePosition = updateSign.getBlockPosition();
        if (!samePosition(session.position(), updatePosition)) {
            return;
        }

        if (!sessions.remove(playerUuid, session)) {
            return;
        }

        event.setCancelled(true);
        String[] lines = updateSign.getTextLines();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (!plugin.getCheckManager().isChecking(playerUuid)) {
                restoreClientBlock(player, session.location());
                return;
            }
            plugin.getCheckManager().handleBatchResponse(player, lines);
        });
    }

    private boolean samePosition(Vector3i first, Vector3i second) {
        return first.x == second.x && first.y == second.y && first.z == second.z;
    }

    private NBTCompound buildSignNbt(Location location, List<HackDefinition> batch) {
        NBTCompound root = new NBTCompound();
        root.setTag("id", new NBTString("minecraft:sign"));
        root.setTag("x", new NBTInt(location.getBlockX()));
        root.setTag("y", new NBTInt(location.getBlockY()));
        root.setTag("z", new NBTInt(location.getBlockZ()));
        root.setTag("is_waxed", new NBTByte(false));
        root.setTag("front_text", buildTextSide(batch, true));
        root.setTag("back_text", buildTextSide(List.of(), false));
        return root;
    }

    private NBTCompound buildTextSide(List<HackDefinition> batch, boolean includeControlLine) {
        NBTCompound text = new NBTCompound();
        NBTList<NBTString> messages = NBTList.createStringList();

        for (int i = 0; i < 4; i++) {
            String json = i < batch.size()
                    ? componentJson(batch.get(i))
                    : (includeControlLine && i == 3 ? keybindJson(CTRL_KEYBIND) : "{\"text\":\"\"}");
            messages.addTag(new NBTString(json));
        }

        text.setTag("messages", messages);
        text.setTag("color", new NBTString("black"));
        text.setTag("has_glowing_text", new NBTByte(false));
        return text;
    }

    private String componentJson(HackDefinition hack) {
        if (hack.getMode() == DetectionMode.KEYBIND) {
            return keybindJson(hack.getKey());
        }
        return "{\"translate\":\"" + escapeJson(hack.getKey())
                + "\",\"fallback\":\"" + escapeJson(hack.getFallback()) + "\"}";
    }

    private String keybindJson(String keybind) {
        return "{\"keybind\":\"" + escapeJson(keybind) + "\"}";
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private void restoreClientBlock(Player player, Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
            return;
        }
        player.sendBlockChange(location, location.getBlock().getBlockData());
    }

    private record FakeSignSession(Location location, Vector3i position) {
    }
}
