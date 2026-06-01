package com.etl.engine.callback;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class LocalEventManager {

    private static final Map<String, PendingEvent> EVENTS = new ConcurrentHashMap<>();
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger(0);

    public static String generateEventId() {
        return "evt-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void registerEvent(String eventId, long timeoutMs) {
        PendingEvent event = new PendingEvent(eventId, timeoutMs);
        EVENTS.put(eventId, event);
        PENDING_COUNT.incrementAndGet();
    }

    public static Optional<PendingEvent> getEvent(String eventId) {
        return Optional.ofNullable(EVENTS.get(eventId));
    }

    public static boolean completeEvent(String eventId, Object data) {
        PendingEvent event = EVENTS.remove(eventId);
        if (event != null) {
            event.future().complete(data);
            PENDING_COUNT.decrementAndGet();
            return true;
        }
        return false;
    }

    public static boolean hasEvent(String eventId) {
        return EVENTS.containsKey(eventId);
    }

    public static boolean isCompleted(String eventId) {
        PendingEvent event = EVENTS.get(eventId);
        return event == null || event.future().isDone();
    }

    public static int getPendingCount() {
        return PENDING_COUNT.get();
    }

    public static class PendingEvent {
        private final String eventId;
        private final long timeoutMs;
        private final CompletableFuture<Object> future;

        public PendingEvent(String eventId, long timeoutMs) {
            this.eventId = eventId;
            this.timeoutMs = timeoutMs;
            this.future = new CompletableFuture<>();
        }

        public String getEventId() { return eventId; }
        public long getTimeoutMs() { return timeoutMs; }
        public CompletableFuture<Object> future() { return future; }
    }
}
