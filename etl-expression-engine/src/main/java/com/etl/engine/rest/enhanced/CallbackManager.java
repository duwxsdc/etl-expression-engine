package com.etl.engine.rest.enhanced;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CallbackManager {
    
    private static final CallbackManager INSTANCE = new CallbackManager();
    
    private final ConcurrentHashMap<String, PendingCallback> pendingCallbacks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(2);
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final int maxPendingCallbacks = 10000;
    private final long defaultTimeoutMs = 30000;
    
    private CallbackManager() {
    }
    
    public static CallbackManager getInstance() {
        return INSTANCE;
    }
    
    public String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    public CompletableFuture<Object> register(String eventId, long timeoutMs) {
        if (pendingCount.get() >= maxPendingCallbacks) {
            throw new CallbackCapacityExceededException("Callback capacity exceeded: max=" + maxPendingCallbacks);
        }
        
        CompletableFuture<Object> future = new CompletableFuture<>();
        PendingCallback pending = new PendingCallback(eventId, future, timeoutMs);
        pendingCallbacks.put(eventId, pending);
        pendingCount.incrementAndGet();
        
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Callback timeout: eventId=" + eventId));
                cleanup(eventId);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        pending.setTimeoutFuture(timeoutFuture);
        
        return future;
    }
    
    public CompletableFuture<Object> register(String eventId) {
        return register(eventId, defaultTimeoutMs);
    }
    
    public boolean complete(String eventId, Object result) {
        PendingCallback pending = pendingCallbacks.get(eventId);
        if (pending != null) {
            boolean completed = pending.getFuture().complete(result);
            if (completed) {
                cleanup(eventId);
            }
            return completed;
        }
        return false;
    }
    
    public boolean completeExceptionally(String eventId, Throwable exception) {
        PendingCallback pending = pendingCallbacks.get(eventId);
        if (pending != null) {
            boolean completed = pending.getFuture().completeExceptionally(exception);
            if (completed) {
                cleanup(eventId);
            }
            return completed;
        }
        return false;
    }
    
    public boolean exists(String eventId) {
        return pendingCallbacks.containsKey(eventId);
    }
    
    public PendingCallback getPendingCallback(String eventId) {
        return pendingCallbacks.get(eventId);
    }
    
    public int getPendingCount() {
        return pendingCount.get();
    }
    
    public void cleanup(String eventId) {
        PendingCallback pending = pendingCallbacks.remove(eventId);
        if (pending != null) {
            pending.cancelTimeout();
            pendingCount.decrementAndGet();
        }
    }
    
    public void cleanupAll() {
        for (PendingCallback pending : pendingCallbacks.values()) {
            pending.cancelTimeout();
            pending.getFuture().completeExceptionally(new CallbackException("Manager shutdown"));
        }
        pendingCallbacks.clear();
        pendingCount.set(0);
    }
    
    public static final class PendingCallback {
        private final String eventId;
        private final CompletableFuture<Object> future;
        private final long timeoutMs;
        private volatile ScheduledFuture<?> timeoutFuture;
        private final long createTime;
        
        PendingCallback(String eventId, CompletableFuture<Object> future, long timeoutMs) {
            this.eventId = eventId;
            this.future = future;
            this.timeoutMs = timeoutMs;
            this.createTime = System.currentTimeMillis();
        }
        
        public String getEventId() {
            return eventId;
        }
        
        public CompletableFuture<Object> getFuture() {
            return future;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > timeoutMs;
        }
        
        void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }
        
        void cancelTimeout() {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        }
    }
}