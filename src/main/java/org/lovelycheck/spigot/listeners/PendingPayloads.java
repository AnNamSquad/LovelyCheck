package org.lovelycheck.spigot.listeners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingPayloads {

    private static final long MAX_AGE_MS = 30_000L;
    private static final int MAX_PAYLOADS_PER_PLAYER = 256;
    private static final Map<UUID, PendingQueue> PENDING = new ConcurrentHashMap<>();

    private PendingPayloads() {
    }

    public static void queue(UUID uuid, String channel, String message) {
        if (uuid == null) {
            return;
        }
        PendingQueue queue = PENDING.computeIfAbsent(uuid, key -> new PendingQueue());
        queue.add(channel, message);
    }

    public static List<PendingPayload> drainFor(UUID uuid) {
        if (uuid == null) {
            return List.of();
        }
        PendingQueue removed = PENDING.remove(uuid);
        if (removed != null) {
            return removed.drain();
        }
        return List.of();
    }

    public static void clearFor(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PENDING.remove(uuid);
    }

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingQueue> entry : PENDING.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                PENDING.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void startPruningTask(org.bukkit.plugin.Plugin plugin) {
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, PendingPayloads::pruneExpired, 600L, 600L);
    }

    public static final class PendingPayload {
        private final String channel;
        private final String message;
        private final long receivedAt;

        public PendingPayload(String channel, String message, long receivedAt) {
            this.channel = channel;
            this.message = message;
            this.receivedAt = receivedAt;
        }

        public String getChannel() {
            return channel;
        }

        public String getMessage() {
            return message;
        }

        public long getReceivedAt() {
            return receivedAt;
        }
    }

    private static final class PendingQueue {
        private final Deque<PendingPayload> payloads = new ArrayDeque<>();
        private long lastUpdated = System.currentTimeMillis();

        private synchronized void add(String channel, String message) {
            long now = System.currentTimeMillis();
            if (payloads.size() >= MAX_PAYLOADS_PER_PLAYER) {
                payloads.pollFirst();
            }
            payloads.addLast(new PendingPayload(channel, message, now));
            lastUpdated = now;
        }

        private synchronized List<PendingPayload> drain() {
            if (payloads.isEmpty()) {
                return List.of();
            }
            List<PendingPayload> drained = new ArrayList<>(payloads);
            payloads.clear();
            return drained;
        }

        private synchronized boolean isExpired(long now) {
            return now - lastUpdated > MAX_AGE_MS;
        }
    }
}
