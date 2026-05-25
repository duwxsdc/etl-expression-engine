package com.etl.engine.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地事件管理器
 * 
 * <p>负责管理异步回调事件的等待和唤醒机制。在分布式环境中，
 * 每个节点维护自己的事件等待队列，通过CompletableFuture实现
 * 事件的阻塞等待和异步唤醒。</p>
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li>事件注册：创建事件ID并关联CompletableFuture</li>
 *   <li>事件等待：通过Future阻塞等待回调</li>
 *   <li>事件唤醒：收到回调后唤醒等待线程</li>
 *   <li>超时清理：自动清理过期未完成的事件</li>
 *   <li>重复回调防护：忽略已处理事件的重复回调</li>
 * </ul>
 * 
 * <h3>设计特点：</h3>
 * <ul>
 *   <li>纯内存存储，无外部依赖</li>
 *   <li>支持虚拟线程执行清理任务</li>
 *   <li>线程安全的并发访问</li>
 *   <li>完善的超时和异常处理</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Autowired
 * private LocalEventManager eventManager;
 * 
 * // 注册事件
 * String eventId = eventManager.generateEventId();
 * PendingEvent event = eventManager.registerEvent(eventId, 30000);
 * 
 * // 等待回调
 * try {
 *     Object result = event.future().get();
 * } catch (TimeoutException e) {
 *     // 处理超时
 * }
 * 
 * // 唤醒事件（通常在回调接口中调用）
 * eventManager.completeEvent(eventId, callbackData);
 * }</pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>事件数据存储在内存中，节点重启会丢失</li>
 *   <li>最大等待事件数限制为10000</li>
 *   <li>建议设置合理的超时时间（30-60秒）</li>
 * </ul>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-24
 * @see PendingEvent
 * @see LocalNodeInfo
 * @see com.etl.engine.rest.InternalCallbackController
 */
@Component
public class LocalEventManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalEventManager.class);
    
    /**
     * 最大等待事件数量
     * 超过此限制将拒绝新事件注册
     */
    private static final int MAX_PENDING_EVENTS = 10000;
    
    /**
     * 过期事件清理周期（毫秒）
     * 每60秒执行一次清理
     */
    private static final long CLEANUP_INTERVAL_MS = 60000;
    
    /**
     * 等待中的事件映射表
     * Key: 事件ID，Value: 待处理事件
     */
    private final ConcurrentHashMap<String, PendingEvent> pendingEvents = new ConcurrentHashMap<>();
    
    /**
     * 已完成的事件集合
     * 用于防止重复回调
     */
    private final ConcurrentHashMap<String, Boolean> completedEvents = new ConcurrentHashMap<>();
    
    /**
     * 当前等待事件计数器
     */
    private final AtomicInteger eventCount = new AtomicInteger(0);
    
    /**
     * 过期事件清理调度器
     * 使用虚拟线程执行
     */
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().factory()
    );
    
    /**
     * 构造函数 - 初始化清理任务
     * 
     * <p>启动定时任务，定期清理过期事件和已完成事件记录</p>
     */
    public LocalEventManager() {
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredEvents,
            CLEANUP_INTERVAL_MS,
            CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        logger.info("LocalEventManager初始化完成，过期事件清理周期: {}ms", CLEANUP_INTERVAL_MS);
    }
    
    /**
     * 生成唯一事件ID
     * 
     * <p>使用UUID生成16位字符的事件ID</p>
     * 
     * @return 16位唯一事件标识符
     */
    public String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 注册新事件
     * 
     * <p>创建一个新的事件等待，返回可被外部唤醒的Future对象。
     * 注册后会自动设置超时，超时后事件将被自动清理。</p>
     * 
     * @param eventId 事件唯一标识符
     * @param timeoutMs 超时时间（毫秒）
     * @return 待处理事件对象，包含Future和时间信息
     * @throws IllegalStateException 当等待事件数超过限制或事件已完成时抛出
     */
    public PendingEvent registerEvent(String eventId, long timeoutMs) {
        if (eventCount.get() >= MAX_PENDING_EVENTS) {
            logger.error("等待事件数量超过限制: {}", MAX_PENDING_EVENTS);
            throw new IllegalStateException("等待事件数量超过限制: " + MAX_PENDING_EVENTS);
        }
        
        if (completedEvents.containsKey(eventId)) {
            logger.warn("事件已完成，忽略重复注册: {}", eventId);
            throw new IllegalStateException("事件已完成: " + eventId);
        }
        
        CompletableFuture<Object> future = new CompletableFuture<>();
        long expireTime = System.currentTimeMillis() + timeoutMs;
        
        PendingEvent event = new PendingEvent(eventId, future, Instant.now(), expireTime);
        PendingEvent previous = pendingEvents.putIfAbsent(eventId, event);
        
        if (previous != null) {
            logger.warn("事件ID已存在，返回已存在的事件: {}", eventId);
            return previous;
        }
        
        eventCount.incrementAndGet();
        logger.debug("事件已注册: eventId={}, timeoutMs={}, 当前等待数={}", 
            eventId, timeoutMs, eventCount.get());
        
        final String finalEventId = eventId;
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
              .whenComplete((result, ex) -> {
                  pendingEvents.remove(finalEventId);
                  eventCount.decrementAndGet();
                  completedEvents.put(finalEventId, Boolean.TRUE);
                  if (ex instanceof TimeoutException) {
                      logger.warn("事件等待超时自动清理: eventId={}", finalEventId);
                  }
              });
        
        return event;
    }
    
    /**
     * 完成事件 - 正常返回结果
     * 
     * <p>通过事件ID唤醒等待的Future，返回回调结果。
     * 如果事件不存在或已完成，将忽略此次调用。</p>
     * 
     * @param eventId 事件唯一标识符
     * @param result 回调结果数据
     * @return 如果成功唤醒返回true，否则返回false
     */
    public boolean completeEvent(String eventId, Object result) {
        if (completedEvents.containsKey(eventId)) {
            logger.warn("事件已完成，忽略重复回调: eventId={}", eventId);
            return false;
        }
        
        PendingEvent event = pendingEvents.get(eventId);
        if (event == null) {
            logger.warn("事件不存在或已超时: eventId={}", eventId);
            return false;
        }
        
        boolean completed = event.future().complete(result);
        if (completed) {
            completedEvents.put(eventId, Boolean.TRUE);
            logger.debug("事件已完成: eventId={}, 当前等待数={}", eventId, eventCount.get());
        }
        return completed;
    }
    
    /**
     * 完成事件 - 异常方式
     * 
     * <p>用于在发生错误时异常结束事件等待</p>
     * 
     * @param eventId 事件唯一标识符
     * @param ex 异常对象
     * @return 如果成功设置异常返回true，否则返回false
     */
    public boolean completeEventExceptionally(String eventId, Throwable ex) {
        PendingEvent event = pendingEvents.get(eventId);
        if (event == null) {
            logger.warn("事件不存在，无法设置异常: eventId={}", eventId);
            return false;
        }
        
        boolean completed = event.future().completeExceptionally(ex);
        if (completed) {
            completedEvents.put(eventId, Boolean.TRUE);
            logger.debug("事件异常完成: eventId={}, error={}", eventId, ex.getMessage());
        }
        return completed;
    }
    
    /**
     * 获取事件对象
     * 
     * @param eventId 事件唯一标识符
     * @return Optional包装的事件对象
     */
    public Optional<PendingEvent> getEvent(String eventId) {
        return Optional.ofNullable(pendingEvents.get(eventId));
    }
    
    /**
     * 检查事件是否存在
     * 
     * @param eventId 事件唯一标识符
     * @return 如果事件存在且未完成返回true
     */
    public boolean hasEvent(String eventId) {
        return pendingEvents.containsKey(eventId);
    }
    
    /**
     * 获取当前等待事件数量
     * 
     * @return 等待中的事件数量
     */
    public int getPendingCount() {
        return eventCount.get();
    }
    
    /**
     * 检查事件是否已完成
     * 
     * @param eventId 事件唯一标识符
     * @return 如果事件已完成返回true
     */
    public boolean isCompleted(String eventId) {
        return completedEvents.containsKey(eventId);
    }
    
    /**
     * 清理过期事件
     * 
     * <p>定时任务，清理已过期的事件和旧的已完成事件记录</p>
     */
    private void cleanupExpiredEvents() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (var entry : pendingEvents.entrySet()) {
            PendingEvent event = entry.getValue();
            if (event.expireTime() < now) {
                String eventId = entry.getKey();
                if (!completedEvents.containsKey(eventId)) {
                    event.future().completeExceptionally(new CallbackTimeoutException("事件等待超时"));
                    completedEvents.put(eventId, Boolean.TRUE);
                    cleaned++;
                }
                pendingEvents.remove(eventId);
                eventCount.decrementAndGet();
            }
        }
        
        long expireThreshold = now - 300000;
        completedEvents.entrySet().removeIf(entry -> {
            PendingEvent event = pendingEvents.get(entry.getKey());
            return event == null || event.expireTime() < expireThreshold;
        });
        
        if (cleaned > 0) {
            logger.info("清理过期事件: {}, 当前等待数: {}", cleaned, eventCount.get());
        }
    }
    
    /**
     * 关闭事件管理器
     * 
     * <p>清理所有等待中的事件并关闭清理任务</p>
     */
    @PreDestroy
    public void shutdown() {
        logger.info("LocalEventManager关闭，清理剩余{}个等待事件", pendingEvents.size());
        
        for (PendingEvent event : pendingEvents.values()) {
            event.future().completeExceptionally(new InterruptedException("服务关闭"));
        }
        pendingEvents.clear();
        completedEvents.clear();
        eventCount.set(0);
        
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 待处理事件记录
     * 
     * <p>使用Java Record定义不可变的事件数据结构</p>
     * 
     * @param eventId 事件唯一标识符
     * @param future 异步结果Future
     * @param createTime 创建时间
     * @param expireTime 过期时间戳
     */
    public record PendingEvent(
        String eventId,
        CompletableFuture<Object> future,
        Instant createTime,
        long expireTime
    ) {
        /**
         * 检查事件是否已过期
         * 
         * @return 如果当前时间超过过期时间返回true
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        /**
         * 获取剩余等待时间
         * 
         * @return 剩余毫秒数，最小为0
         */
        public long remainingTimeMs() {
            return Math.max(0, expireTime - System.currentTimeMillis());
        }
    }
}
