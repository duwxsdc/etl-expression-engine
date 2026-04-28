package com.etl.engine.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话上下文管理器
 * 管理所有会话的生命周期
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class SessionContextManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionContextManager.class);

    private static final long SESSION_TIMEOUT = 30 * 60 * 1000;
    private static final long CLEANUP_INTERVAL = 5 * 60 * 1000;

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);

    public SessionContextManager() {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "session-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
    }

    public SessionContext createSession() {
        String sessionId = generateSessionId();
        SessionContext context = new SessionContext(sessionId);
        sessions.put(sessionId, context);
        activeSessionCount.incrementAndGet();
        logger.info("创建新会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
        return context;
    }

    public SessionContext getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void destroySession(String sessionId) {
        SessionContext context = sessions.remove(sessionId);
        if (context != null) {
            context.clearVariables();
            activeSessionCount.decrementAndGet();
            logger.info("销毁会话: {}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
        }
    }

    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }

    private String generateSessionId() {
        return "SESSION-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredSessions();
            } catch (Exception e) {
                logger.error("会话清理任务异常", e);
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
        logger.info("会话清理任务已启动, 超时时间: {}ms, 清理间隔: {}ms", SESSION_TIMEOUT, CLEANUP_INTERVAL);
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            SessionContext context = entry.getValue();
            if (now - context.getLastAccessTime() > SESSION_TIMEOUT) {
                context.clearVariables();
                activeSessionCount.decrementAndGet();
                logger.warn("会话超时自动清理: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        sessions.clear();
        activeSessionCount.set(0);
        logger.info("会话管理器已关闭");
    }
}
