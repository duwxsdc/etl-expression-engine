package com.etl.engine.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ETL上下文管理器
 * 分布式Redis版会话上下文管理器
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class EtlContextManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EtlContextManager.class);
    
    private static final String REDIS_KEY_PREFIX = "etl:context:";
    private static final long SESSION_TIMEOUT_SECONDS = 30 * 60; // 30 minutes
    private static final long CLEANUP_INTERVAL_SECONDS = 5 * 60; // 5 minutes
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);
    
    @Autowired
    public EtlContextManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etl-context-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
    }
    
    /**
     * 创建新会话上下文
     * @return 新创建的EtlContext
     */
    public EtlContext createSession() {
        String sessionId = generateSessionId();
        EtlContext context = new EtlContext(sessionId);
        
        // 存储到Redis
        redisTemplate.opsForValue().set(
            REDIS_KEY_PREFIX + sessionId, 
            context, 
            Duration.ofSeconds(SESSION_TIMEOUT_SECONDS)
        );
        
        activeSessionCount.incrementAndGet();
        logger.info("创建新会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
        return context;
    }
    
    /**
     * 获取会话上下文
     * @param sessionId 会话ID
     * @return 会话上下文，如果不存在则返回null
     */
    public EtlContext getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        
        try {
            EtlContext context = (EtlContext) redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + sessionId);
            if (context != null) {
                // 更新访问时间
                redisTemplate.expire(REDIS_KEY_PREFIX + sessionId, Duration.ofSeconds(SESSION_TIMEOUT_SECONDS));
                logger.debug("获取会话上下文: {}", sessionId);
            }
            return context;
        } catch (Exception e) {
            logger.error("获取会话上下文失败: {}", sessionId, e);
            return null;
        }
    }
    
    /**
     * 销毁会话上下文
     * @param sessionId 会话ID
     */
    public void destroySession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
            activeSessionCount.decrementAndGet();
            logger.info("销毁会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
        } catch (Exception e) {
            logger.error("销毁会话上下文失败: {}", sessionId, e);
        }
    }
    
    /**
     * 检查会话是否存在
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return redisTemplate.hasKey(REDIS_KEY_PREFIX + sessionId);
    }
    
    /**
     * 获取活跃会话数量
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }
    
    /**
     * 更新会话上下文到Redis
     * @param context 会话上下文
     */
    public void updateSession(EtlContext context) {
        if (context == null || context.getSessionId() == null) {
            return;
        }
        
        try {
            redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + context.getSessionId(), 
                context, 
                Duration.ofSeconds(SESSION_TIMEOUT_SECONDS)
            );
            logger.debug("更新会话上下文: {}", context.getSessionId());
        } catch (Exception e) {
            logger.error("更新会话上下文失败: {}", context.getSessionId(), e);
        }
    }
    
    private String generateSessionId() {
        return "ETL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
    
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Redis会话自动过期，不需要手动清理
                logger.debug("Redis会话清理任务执行（Redis自动过期）");
            } catch (Exception e) {
                logger.error("Redis会话清理任务异常", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("Redis会话管理器已启动, 超时时间: {}秒, 清理间隔: {}秒", 
                   SESSION_TIMEOUT_SECONDS, CLEANUP_INTERVAL_SECONDS);
    }
    
    public void shutdown() {
        scheduler.shutdown();
        logger.info("Redis会话管理器已关闭");
    }
}
