package org.lovelycheck.core;

import org.lovelycheck.core.config.Action;
import org.lovelycheck.core.config.GenericCheck;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LovelyCheckRegistry {

    private final static Map<UUID, LovelyCheckPlayer> players = new ConcurrentHashMap<>();
    private final static Map<String, Action> actions = new HashMap<>();
    private final static Map<String, GenericCheck> genericChecks = new HashMap<>();
    private final static Map<LovelyCheckPlayer, BoundedSet<MessagePayload>> messageHistory = new ConcurrentHashMap<>();

    private static class BoundedSet<E> {
        private final int maxSize;
        private final Map<E, Boolean> map;

        public BoundedSet(int maxSize) {
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<E, Boolean>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<E, Boolean> eldest) {
                    return size() > BoundedSet.this.maxSize;
                }
            };
        }

        public synchronized boolean add(E e) {
            return map.put(e, Boolean.TRUE) == null;
        }

        public synchronized void clear() {
            map.clear();
        }
    }

    public static void clear() {
        players.clear();
        actions.clear();
        genericChecks.clear();
        messageHistory.clear();
    }

    public static void registerPlayer(UUID uuid) {
        // Use computeIfAbsent to avoid replacing an existing player that may have
        // been created by getPlayer() during packet handling, which would lose
        // any pending actions that were queued.
        players.computeIfAbsent(uuid, LovelyCheckPlayer::new);
    }

    public static void removePlayer(UUID uuid) {
        LovelyCheckPlayer player = players.get(uuid);
        if (player != null) {
            messageHistory.remove(player);
        }
        players.remove(uuid);
    }

    public static void removePlayer(LovelyCheckPlayer player) {
        removePlayer(player.getUuid());
    }

    public static LovelyCheckPlayer getPlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, key -> new LovelyCheckPlayer(uuid));
    }

    /**
     * Returns the LovelyCheckPlayer for the given UUID only if already tracked.
     * Unlike {@link #getPlayer(UUID)}, this will NOT create a new entry if the
     * player has been removed (e.g. after disconnect). Use this in delayed tasks
     * where the player may have already left.
     */
    public static LovelyCheckPlayer getPlayerIfPresent(UUID uuid) {
        return players.get(uuid);
    }

    public static Collection<LovelyCheckPlayer> getPlayers() {
        return players.values();
    }

    public static void registerAction(Action action) {
        actions.put(action.getId(), action);
    }

    public static void removeAction(String id) {
        actions.remove(id);
    }

    public static void removeAction(Action action) {
        removeAction(action.getId());
    }

    public static Action getAction(String id) {
        return actions.get(id);
    }

    public static void registerCheck(GenericCheck check) {
        genericChecks.put(check.getId(), check);
    }

    public static void removeCheck(String id) {
        genericChecks.remove(id);
    }

    public static void removeCheck(GenericCheck check) {
        removeCheck(check.getId());
    }

    public static GenericCheck getCheck(String id) {
        return genericChecks.get(id);
    }

    public static Collection<GenericCheck> getChecks() {
        return genericChecks.values();
    }

    public static boolean isMessageDuplicate(LovelyCheckPlayer player, String channel, String message) {
        MessagePayload payload = new MessagePayload(channel, message);
        BoundedSet<MessagePayload> playerHistory = messageHistory.computeIfAbsent(player, k -> new BoundedSet<>(128));
        return !playerHistory.add(payload);
    }
}
