package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步请求管理器，负责跟踪和管理异步HTTP请求的生命周期。
 * <p>
 * 该类提供异步请求的注册、完成跟踪、超时监控、批量取消等功能。
 * 内部使用定时任务检测长时间未完成的请求，防止资源泄漏。
 * 同时提供并发请求数量限制，保护系统免受过载影响。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class AsyncRequestManager {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncRequestManager.class);
    
    /**
     * 活跃请求映射表，键为请求ID，值为请求上下文
     */
    private static final ConcurrentHashMap<Long, AsyncRequestContext> ACTIVE_REQUESTS = new ConcurrentHashMap<>();
    
    /**
     * 请求ID生成器
     */
    private static final AtomicLong REQUEST_ID_GENERATOR = new AtomicLong(0);
    
    /**
     * 默认请求超时时间（毫秒）
     */
    private static final long DEFAULT_TIMEOUT = 60_000;
    
    /**
     * 最大请求持有时间（毫秒），超过此时间视为潜在泄漏
     */
    private static final long MAX_REQUEST_HOLD_TIME = 300_000;
    
    /**
     * 最大并发请求数
     */
    private static final int MAX_CONCURRENT_REQUESTS = 100;
    
    /**
     * 超时监控定时执行器
     */
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
    
    /**
     * 私有构造方法，防止实例化。
     */
    private AsyncRequestManager() {
    }
    
    /**
     * 注册新的异步请求。
     *
     * @param future 异步响应Future对象
     * @param url 请求URL
     * @param method HTTP方法
     * @return 请求ID
     */
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
    
    /**
     * 标记请求为已完成，从活跃列表中移除。
     *
     * @param requestId 请求ID
     */
    public static void completeRequest(long requestId) {
        AsyncRequestContext context = ACTIVE_REQUESTS.remove(requestId);
        if (context != null) {
            long duration = System.currentTimeMillis() - context.createTime();
            logger.debug("异步请求已完成: id={}, duration={}ms", requestId, duration);
        }
    }
    
    /**
     * 包装Future对象，添加完成跟踪。
     *
     * @param <T> Future结果类型
     * @param future 原始Future对象
     * @param description 请求描述
     * @return 包装后的Future对象
     */
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
    
    /**
     * 为Future添加超时控制。
     *
     * @param <T> Future结果类型
     * @param future 原始Future对象
     * @param timeoutMs 超时时间（毫秒）
     * @return 带超时控制的Future对象
     */
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
    
    /**
     * 取消所有活跃的异步请求。
     */
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
    
    /**
     * 取消指定线程的所有异步请求。
     *
     * @param threadName 线程名称
     * @return 取消的请求数量
     */
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
    
    /**
     * 检查并处理超时的异步请求。
     * <p>
     * 定时执行，检查持有时间超过阈值的请求，输出警告日志并取消请求。
     * </p>
     */
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
    
    /**
     * 获取当前活跃请求数量。
     *
     * @return 活跃请求数量
     */
    public static int getActiveRequestCount() {
        return ACTIVE_REQUESTS.size();
    }
    
    /**
     * 检查是否可以创建新的请求。
     *
     * @return 如果未超过最大并发限制返回true，否则返回false
     */
    public static boolean canCreateNewRequest() {
        return ACTIVE_REQUESTS.size() < MAX_CONCURRENT_REQUESTS;
    }
    
    /**
     * 关闭异步请求管理器，释放所有资源。
     */
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
    
    /**
     * 异步请求上下文记录，封装请求的元数据信息。
     *
     * @param requestId 请求ID
     * @param future 异步响应Future
     * @param url 请求URL
     * @param method HTTP方法
     * @param createTime 创建时间戳
     * @param threadName 创建线程名称
     * @param stackTrace 创建时的堆栈跟踪
     */
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
