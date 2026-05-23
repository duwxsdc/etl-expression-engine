package com.etl.engine.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EtlContextManager {

    private static final Logger logger = LoggerFactory.getLogger(EtlContextManager.class);

    private static final String REDIS_KEY_PREFIX = "etl:context:";
    private static final long SESSION_TIMEOUT_SECONDS = 30 * 60;
    private static final long CLEANUP_INTERVAL_SECONDS = 5 * 60;

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, EtlContext> localSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);
    private final boolean redisAvailable;

    @Autowired(required = false)
    public EtlContextManager(@Nullable RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisAvailable = redisTemplate != null;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etl-context-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
        logger.info("ETL上下文管理器已启动, 模式: {}", redisAvailable ? "Redis" : "本地内存");
    }

    public EtlContext createSession() {
        String sessionId = generateSessionId();
        EtlContext context = new EtlContext(sessionId);

        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + sessionId,
                    context,
                    Duration.ofSeconds(SESSION_TIMEOUT_SECONDS)
                );
            } catch (Exception e) {
                logger.warn("Redis写入失败，降级为本地存储: {}", e.getMessage());
                localSessions.put(sessionId, context);
            }
        } else {
            localSessions.put(sessionId, context);
        }

        activeSessionCount.incrementAndGet();
        logger.info("创建新会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
        return context;
    }

    public EtlContext getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        if (redisAvailable) {
            try {
                EtlContext context = (EtlContext) redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + sessionId);
                if (context != null) {
                    redisTemplate.expire(REDIS_KEY_PREFIX + sessionId, Duration.ofSeconds(SESSION_TIMEOUT_SECONDS));
                    return context;
                }
            } catch (Exception e) {
                logger.warn("Redis读取失败，尝试本地存储: {}", e.getMessage());
            }
        }

        return localSessions.get(sessionId);
    }

    public void destroySession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        if (redisAvailable) {
            try {
                redisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
            } catch (Exception e) {
                logger.warn("Redis删除失败: {}", e.getMessage());
            }
        }

        localSessions.remove(sessionId);
        activeSessionCount.decrementAndGet();
        logger.info("销毁会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
    }

    public boolean hasSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        if (redisAvailable) {
            try {
                Boolean exists = redisTemplate.hasKey(REDIS_KEY_PREFIX + sessionId);
                if (exists != null && exists) {
                    return true;
                }
            } catch (Exception e) {
                logger.warn("Redis检查失败: {}", e.getMessage());
            }
        }

        return localSessions.containsKey(sessionId);
    }

    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }

    public void updateSession(EtlContext context) {
        if (context == null || context.getSessionId() == null) {
            return;
        }

        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + context.getSessionId(),
                    context,
                    Duration.ofSeconds(SESSION_TIMEOUT_SECONDS)
                );
            } catch (Exception e) {
                logger.warn("Redis更新失败，降级为本地存储: {}", e.getMessage());
                localSessions.put(context.getSessionId(), context);
            }
        } else {
            localSessions.put(context.getSessionId(), context);
        }
    }

    private String generateSessionId() {
        return "ETL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredLocalSessions();
            } catch (Exception e) {
                logger.error("会话清理任务异常", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("会话清理任务已启动, 超时时间: {}秒, 清理间隔: {}秒",
                   SESSION_TIMEOUT_SECONDS, CLEANUP_INTERVAL_SECONDS);
    }

    private void cleanupExpiredLocalSessions() {
        long now = System.currentTimeMillis();
        localSessions.entrySet().removeIf(entry -> {
            EtlContext context = entry.getValue();
            if (now - context.getLastAccessTime() > SESSION_TIMEOUT_SECONDS * 1000) {
                activeSessionCount.decrementAndGet();
                logger.warn("本地会话超时自动清理: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        localSessions.clear();
        logger.info("ETL上下文管理器已关闭");
    }
}
