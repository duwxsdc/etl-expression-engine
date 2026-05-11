package com.etl.engine.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 幂等控制器
 * 基于requestId的去重控制，使用时间窗口滑动清理
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class IdempotentController {
    
    private static final Logger logger = LoggerFactory.getLogger(IdempotentController.class);
    
    private final Map<String, Long> processedRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final int windowSeconds;
    
    public IdempotentController() {
        this.windowSeconds = 30;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotent-cleaner");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
    }
    
    /**
     * 检查请求是否已处理
     * @param requestId 请求ID
     * @return true=已处理（重复请求），false=未处理（首次请求）
     */
    public boolean isProcessed(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        
        Long processedTime = processedRequests.get(requestId);
        if (processedTime != null) {
            long elapsed = System.currentTimeMillis() - processedTime;
            if (elapsed < windowSeconds * 1000L) {
                logger.debug("请求已处理: requestId={}, elapsed={}ms", requestId, elapsed);
                return true;
            }
            processedRequests.remove(requestId);
        }
        return false;
    }
    
    /**
     * 标记请求为已处理
     * @param requestId 请求ID
     */
    public void markProcessed(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            processedRequests.put(requestId, System.currentTimeMillis());
            logger.debug("标记请求已处理: requestId={}", requestId);
        }
    }
    
    /**
     * 尝试处理请求（原子操作）
     * @param requestId 请求ID
     * @return true=可以处理，false=重复请求
     */
    public boolean tryProcess(String requestId) {
        if (isProcessed(requestId)) {
            return false;
        }
        markProcessed(requestId);
        return true;
    }
    
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long threshold = windowSeconds * 1000L;
                
                processedRequests.entrySet().removeIf(entry -> {
                    long elapsed = now - entry.getValue();
                    return elapsed >= threshold;
                });
                
                logger.debug("幂等清理任务完成, 当前缓存数量: {}", processedRequests.size());
            } catch (Exception e) {
                logger.error("幂等清理任务异常", e);
            }
        }, windowSeconds, windowSeconds, TimeUnit.SECONDS);
        
        logger.info("幂等控制器已启动, 时间窗口: {}秒", windowSeconds);
    }
    
    public void shutdown() {
        scheduler.shutdown();
        logger.info("幂等控制器已关闭");
    }
    
    public int getCacheSize() {
        return processedRequests.size();
    }
}
