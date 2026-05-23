package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncRequestManager {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncRequestManager.class);
    
    private static final ConcurrentHashMap<Long, AsyncRequestContext> ACTIVE_REQUESTS = new ConcurrentHashMap<>();
    
    private static final AtomicLong REQUEST_ID_GENERATOR = new AtomicLong(0);
    
    private static final long DEFAULT_TIMEOUT = 60_000;
    
    private static final long MAX_REQUEST_HOLD_TIME = 300_000;
    
    private static final int MAX_CONCURRENT_REQUESTS = 100;
    
    private static final ScheduledExecutorService TIMEOUT_MONITOR = 
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "async-request-monitor");
                t.setDaemon(true);
                return t;
            });
    
    static {
        TIMEOUT_MONITOR.scheduleAtFixedRate(
            AsyncRequestManager::checkTimeouts,
            10_000,
            10_000,
            TimeUnit.MILLISECONDS
        );
    }
    
    private AsyncRequestManager() {
    }
    
    public static long registerRequest(CompletableFuture<HttpResponse> future, String url, String method) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        AsyncRequestContext context = new AsyncRequestContext(
            requestId,
            future,
            url,
            method,
            System.currentTimeMillis(),
            threadName,
            stackTrace
        );
        
        ACTIVE_REQUESTS.put(requestId, context);
        
        logger.debug("异步请求已注册: id={}, url={}, method={}", requestId, url, method);
        
        return requestId;
    }
    
    public static void completeRequest(long requestId) {
        AsyncRequestContext context = ACTIVE_REQUESTS.remove(requestId);
        if (context != null) {
            long duration = System.currentTimeMillis() - context.createTime();
            logger.debug("异步请求已完成: id={}, duration={}ms", requestId, duration);
        }
    }
    
    public static <T> CompletableFuture<T> wrapFuture(CompletableFuture<T> future, String description) {
        long requestId = registerRequest(null, description, "WRAP");
        
        CompletableFuture<T> wrappedFuture = future.whenComplete((result, ex) -> {
            completeRequest(requestId);
            if (ex != null) {
                logger.warn("异步操作异常: id={}, description={}, error={}", 
                        requestId, description, ex.getMessage());
            }
        });
        
        return wrappedFuture;
    }
    
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeoutMs) {
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT;
        }
        
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();
        ACTIVE_REQUESTS.put(requestId, new AsyncRequestContext(
            requestId, null, "timeout-wrap", "TIMEOUT",
            System.currentTimeMillis(), Thread.currentThread().getName(),
            Thread.currentThread().getStackTrace()
        ));
        long effectiveTimeout = timeoutMs;
        
        return future.orTimeout(effectiveTimeout, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    completeRequest(requestId);
                    if (ex instanceof TimeoutException) {
                        logger.warn("异步请求超时: id={}, timeout={}ms", requestId, effectiveTimeout);
                    }
                });
    }
    
    public static void cancelAll() {
        logger.info("取消所有活跃异步请求, 数量={}", ACTIVE_REQUESTS.size());
        
        for (Map.Entry<Long, AsyncRequestContext> entry : ACTIVE_REQUESTS.entrySet()) {
            AsyncRequestContext context = entry.getValue();
            if (context.future() != null && !context.future().isDone()) {
                context.future().cancel(true);
                logger.debug("异步请求已取消: id={}, url={}", entry.getKey(), context.url());
            }
        }
        
        ACTIVE_REQUESTS.clear();
    }
    
    public static int cancelForThread(String threadName) {
        int cancelled = 0;
        
        var iterator = ACTIVE_REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            AsyncRequestContext context = entry.getValue();
            
            if (context.threadName().equals(threadName)) {
                if (context.future() != null && !context.future().isDone()) {
                    context.future().cancel(true);
                    cancelled++;
                    logger.debug("线程异步请求已取消: thread={}, requestId={}", threadName, entry.getKey());
                }
                iterator.remove();
            }
        }
        
        return cancelled;
    }
    
    public static void checkTimeouts() {
        long now = System.currentTimeMillis();
        int timeoutCount = 0;
        
        for (Map.Entry<Long, AsyncRequestContext> entry : ACTIVE_REQUESTS.entrySet()) {
            AsyncRequestContext context = entry.getValue();
            long holdTime = now - context.createTime();
            
            if (holdTime > MAX_REQUEST_HOLD_TIME) {
                timeoutCount++;
                logger.warn("检测到长时间持有异步请求: id={}, url={}, holdTime={}ms, thread={}",
                        entry.getKey(),
                        context.url(),
                        holdTime,
                        context.threadName());
                
                if (context.future() != null && !context.future().isDone()) {
                    context.future().cancel(true);
                    logger.info("已取消长时间持有的异步请求: id={}", entry.getKey());
                }
                
                ACTIVE_REQUESTS.remove(entry.getKey());
            }
        }
        
        if (timeoutCount > 0) {
            logger.warn("异步请求超时检测完成: 取消{}个请求", timeoutCount);
        }
    }
    
    public static int getActiveRequestCount() {
        return ACTIVE_REQUESTS.size();
    }
    
    public static boolean canCreateNewRequest() {
        return ACTIVE_REQUESTS.size() < MAX_CONCURRENT_REQUESTS;
    }
    
    public static void shutdown() {
        cancelAll();
        TIMEOUT_MONITOR.shutdown();
        try {
            if (!TIMEOUT_MONITOR.awaitTermination(5, TimeUnit.SECONDS)) {
                TIMEOUT_MONITOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            TIMEOUT_MONITOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("异步请求管理器已关闭");
    }
    
    public record AsyncRequestContext(
        long requestId,
        CompletableFuture<HttpResponse> future,
        String url,
        String method,
        long createTime,
        String threadName,
        StackTraceElement[] stackTrace
    ) {}
}
