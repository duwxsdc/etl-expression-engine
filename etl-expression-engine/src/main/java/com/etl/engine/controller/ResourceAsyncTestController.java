package com.etl.engine.controller;

import com.etl.engine.http.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/etl/expression/test")
public class ResourceAsyncTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceAsyncTestController.class);
    
    private static final AtomicInteger activeResources = new AtomicInteger(0);
    private static final AtomicLong resourceIdGenerator = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Long> activeResourceMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ResourceMetadata> resourceMetadataMap = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<String, AsyncTaskInfo> asyncTaskRegistry = new ConcurrentHashMap<>();
    
    private static final long LEAK_THRESHOLD_MS = 60_000;
    
    private static final AtomicInteger asyncTaskCounter = new AtomicInteger(0);
    private static final AtomicInteger asyncTaskCompletedCounter = new AtomicInteger(0);
    
    @GetMapping("/resource/create")
    public ResponseEntity<Map<String, Object>> testResourceCreate(
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(defaultValue = "false") boolean autoRelease) {
        
        List<Long> resourceIds = new ArrayList<>();
        String threadName = Thread.currentThread().getName();
        String stackTrace = getCallerInfo();
        
        for (int i = 0; i < count; i++) {
            long resourceId = resourceIdGenerator.incrementAndGet();
            resourceIds.add(resourceId);
            
            if (!autoRelease) {
                long createTime = System.currentTimeMillis();
                activeResourceMap.put(resourceId, createTime);
                resourceMetadataMap.put(resourceId, new ResourceMetadata(
                    resourceId, threadName, stackTrace, createTime
                ));
                activeResources.incrementAndGet();
                
                logger.debug("资源创建: id={}, thread={}, caller={}", resourceId, threadName, stackTrace);
            }
        }
        
        logger.info("资源创建完成: 创建数量={}, 自动释放={}, 当前活跃资源数={}", 
                count, autoRelease, activeResources.get());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", resourceIds.size());
        result.put("resourceIds", resourceIds);
        result.put("activeResourceCount", activeResources.get());
        result.put("autoRelease", autoRelease);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/resource/release")
    public ResponseEntity<Map<String, Object>> testResourceRelease(
            @RequestBody List<Long> resourceIds) {
        
        int releasedCount = 0;
        List<Long> notFound = new ArrayList<>();
        
        for (Long id : resourceIds) {
            Long createTime = activeResourceMap.remove(id);
            if (createTime != null) {
                activeResources.decrementAndGet();
                ResourceMetadata metadata = resourceMetadataMap.remove(id);
                releasedCount++;
                
                long holdTime = System.currentTimeMillis() - createTime;
                logger.debug("资源释放: id={}, 持有时间={}ms, 创建线程={}", 
                        id, holdTime, metadata != null ? metadata.threadName() : "unknown");
                
                if (holdTime > LEAK_THRESHOLD_MS) {
                    logger.warn("检测到长时间持有的资源被释放: id={}, 持有时间={}ms, 创建者={}", 
                            id, holdTime, metadata != null ? metadata.callerInfo() : "unknown");
                }
            } else {
                notFound.add(id);
                logger.debug("资源未找到: id={}", id);
            }
        }
        
        logger.info("资源释放完成: 释放数量={}, 未找到数量={}, 剩余活跃资源数={}", 
                releasedCount, notFound.size(), activeResources.get());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("releasedCount", releasedCount);
        result.put("notFoundCount", notFound.size());
        result.put("notFoundIds", notFound);
        result.put("remainingResourceCount", activeResources.get());
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/resource/status")
    public ResponseEntity<Map<String, Object>> testResourceStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeResourceCount", activeResources.get());
        result.put("trackedResourceCount", activeResourceMap.size());
        result.put("totalCreated", resourceIdGenerator.get());
        result.put("asyncTaskActive", asyncTaskCounter.get() - asyncTaskCompletedCounter.get());
        result.put("asyncTaskTotal", asyncTaskCounter.get());
        result.put("asyncTaskCompleted", asyncTaskCompletedCounter.get());
        
        List<Map<String, Object>> resourceDetails = new ArrayList<>();
        List<Map<String, Object>> potentialLeaks = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        activeResourceMap.forEach((id, createTime) -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            long holdTime = now - createTime;
            detail.put("resourceId", id);
            detail.put("createTime", createTime);
            detail.put("holdTimeMs", holdTime);
            
            ResourceMetadata metadata = resourceMetadataMap.get(id);
            if (metadata != null) {
                detail.put("threadName", metadata.threadName());
                detail.put("callerInfo", metadata.callerInfo());
            }
            
            if (holdTime > LEAK_THRESHOLD_MS) {
                detail.put("potentialLeak", true);
                potentialLeaks.add(detail);
            }
            
            resourceDetails.add(detail);
        });
        result.put("resources", resourceDetails);
        result.put("potentialLeaks", potentialLeaks);
        result.put("potentialLeakCount", potentialLeaks.size());
        result.put("timestamp", System.currentTimeMillis());
        
        if (!potentialLeaks.isEmpty()) {
            logger.warn("资源状态检查: 发现{}个潜在资源泄漏", potentialLeaks.size());
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/async/immediate")
    public ResponseEntity<Map<String, Object>> testAsyncImmediate(
            @RequestParam(defaultValue = "test") String message) {
        
        int taskId = asyncTaskCounter.incrementAndGet();
        String taskIdStr = "immediate-" + taskId;
        
        asyncTaskRegistry.put(taskIdStr, new AsyncTaskInfo(
            taskIdStr, "immediate", System.currentTimeMillis(), Thread.currentThread().getName()
        ));
        
        logger.debug("异步任务开始: taskId={}, type=immediate", taskIdStr);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "immediate");
        result.put("message", message);
        result.put("taskId", taskIdStr);
        result.put("processingTimeMs", 0);
        result.put("timestamp", System.currentTimeMillis());
        
        asyncTaskRegistry.remove(taskIdStr);
        asyncTaskCompletedCounter.incrementAndGet();
        logger.debug("异步任务完成: taskId={}", taskIdStr);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/async/delayed")
    public ResponseEntity<Map<String, Object>> testAsyncDelayed(
            @RequestParam(defaultValue = "100") int delayMs,
            @RequestParam(defaultValue = "task") String taskName) {
        
        int taskId = asyncTaskCounter.incrementAndGet();
        String taskIdStr = "delayed-" + taskId;
        long startTime = System.currentTimeMillis();
        
        asyncTaskRegistry.put(taskIdStr, new AsyncTaskInfo(
            taskIdStr, "delayed", startTime, Thread.currentThread().getName()
        ));
        
        logger.info("延迟任务开始: taskId={}, delayMs={}, taskName={}", taskIdStr, delayMs, taskName);
        
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("延迟任务被中断: taskId={}", taskIdStr);
        }
        long actualDelay = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "delayed");
        result.put("taskId", taskIdStr);
        result.put("taskName", taskName);
        result.put("requestedDelayMs", delayMs);
        result.put("actualDelayMs", actualDelay);
        result.put("timestamp", System.currentTimeMillis());
        
        asyncTaskRegistry.remove(taskIdStr);
        asyncTaskCompletedCounter.incrementAndGet();
        logger.info("延迟任务完成: taskId={}, actualDelayMs={}", taskIdStr, actualDelay);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/async/concurrent")
    public ResponseEntity<Map<String, Object>> testAsyncConcurrent(
            @RequestParam(defaultValue = "3") int taskCount,
            @RequestParam(defaultValue = "100") int delayMs) {
        
        String batchId = "batch-" + System.currentTimeMillis();
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        long start = System.currentTimeMillis();
        
        logger.info("并发任务批次开始: batchId={}, taskCount={}, delayMs={}", batchId, taskCount, delayMs);
        
        for (int i = 0; i < taskCount; i++) {
            final int taskIndex = i;
            final String taskId = batchId + "-task-" + taskIndex;
            
            asyncTaskCounter.incrementAndGet();
            asyncTaskRegistry.put(taskId, new AsyncTaskInfo(
                taskId, "concurrent", System.currentTimeMillis(), "async-pool"
            ));
            
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Map<String, Object> taskResult = new LinkedHashMap<>();
                taskResult.put("taskIndex", taskIndex);
                taskResult.put("taskId", taskId);
                taskResult.put("completedAt", System.currentTimeMillis());
                
                asyncTaskRegistry.remove(taskId);
                asyncTaskCompletedCounter.incrementAndGet();
                logger.debug("并发任务完成: taskId={}, taskIndex={}", taskId, taskIndex);
                
                return taskResult;
            }).exceptionally(ex -> {
                logger.error("并发任务异常: taskId={}, error={}", taskId, ex.getMessage());
                asyncTaskRegistry.remove(taskId);
                asyncTaskCompletedCounter.incrementAndGet();
                return Map.of("error", true, "taskId", taskId, "message", ex.getMessage());
            }));
        }
        
        List<Map<String, Object>> taskResults = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<Map<String, Object>> future : futures) {
                taskResults.add(future.get());
            }
        } catch (Exception e) {
            logger.error("并发任务批次执行失败: batchId={}, error={}", batchId, e.getMessage());
        }
        
        long totalTime = System.currentTimeMillis() - start;
        
        logger.info("并发任务批次完成: batchId={}, 完成任务数={}, 总耗时={}ms, 并发效率={}", 
                batchId, taskResults.size(), totalTime, 
                taskCount > 0 ? String.format("%.1f%%", (double)(taskCount * delayMs) / totalTime * 100) : "N/A");
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "concurrent");
        result.put("batchId", batchId);
        result.put("taskCount", taskCount);
        result.put("requestedDelayMs", delayMs);
        result.put("totalTimeMs", totalTime);
        result.put("taskResults", taskResults);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/async/cancellable")
    public ResponseEntity<Map<String, Object>> testAsyncCancellable(
            @RequestParam(defaultValue = "10000") int totalDelayMs,
            @RequestParam(defaultValue = "task-001") String taskId) {
        
        int taskNum = asyncTaskCounter.incrementAndGet();
        String fullTaskId = "cancellable-" + taskNum + "-" + taskId;
        long startTime = System.currentTimeMillis();
        
        asyncTaskRegistry.put(fullTaskId, new AsyncTaskInfo(
            fullTaskId, "cancellable", startTime, Thread.currentThread().getName()
        ));
        
        logger.info("可取消任务注册: taskId={}, totalDelayMs={}", fullTaskId, totalDelayMs);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "cancellable");
        result.put("taskId", fullTaskId);
        result.put("totalDelayMs", totalDelayMs);
        result.put("status", "started");
        result.put("startTime", startTime);
        result.put("message", "任务已启动，可在完成前取消");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/security/check")
    public ResponseEntity<Map<String, Object>> testSecurityCheck(
            @RequestParam(defaultValue = "read") String permission,
            @RequestParam(defaultValue = "user") String role) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permission", permission);
        result.put("role", role);
        
        boolean allowed = switch (role) {
            case "admin" -> true;
            case "user" -> "read".equals(permission) || "list".equals(permission);
            case "guest" -> "list".equals(permission);
            default -> false;
        };
        
        result.put("allowed", allowed);
        result.put("checkedAt", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/security/resource-access")
    public ResponseEntity<Map<String, Object>> testResourceAccess(
            @RequestParam(defaultValue = "resource-001") String resourceId,
            @RequestParam(defaultValue = "read") String action,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("action", action);
        result.put("userId", userId);
        result.put("accessGranted", !"anonymous".equals(userId));
        result.put("checkedAt", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/resource/cleanup")
    public ResponseEntity<Map<String, Object>> testResourceCleanup() {
        int cleanedCount = activeResourceMap.size();
        int cleanedMetadata = resourceMetadataMap.size();
        
        if (cleanedCount > 0) {
            logger.warn("资源清理: 清理{}个未释放资源, {}个元数据记录", cleanedCount, cleanedMetadata);
        }
        
        activeResourceMap.clear();
        resourceMetadataMap.clear();
        activeResources.set(0);
        
        int activeAsyncCount = asyncTaskCounter.get() - asyncTaskCompletedCounter.get();
        if (activeAsyncCount > 0) {
            logger.warn("资源清理: 还有{}个异步任务未完成", activeAsyncCount);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleanedCount", cleanedCount);
        result.put("cleanedMetadataCount", cleanedMetadata);
        result.put("remainingCount", activeResources.get());
        result.put("activeAsyncTasks", activeAsyncCount);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/resource/leak-check")
    public ResponseEntity<Map<String, Object>> checkResourceLeaks() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> leaks = new ArrayList<>();
        
        activeResourceMap.forEach((id, createTime) -> {
            long holdTime = now - createTime;
            if (holdTime > LEAK_THRESHOLD_MS) {
                Map<String, Object> leak = new LinkedHashMap<>();
                leak.put("resourceId", id);
                leak.put("createTime", createTime);
                leak.put("holdTimeMs", holdTime);
                
                ResourceMetadata metadata = resourceMetadataMap.get(id);
                if (metadata != null) {
                    leak.put("threadName", metadata.threadName());
                    leak.put("callerInfo", metadata.callerInfo());
                }
                leaks.add(leak);
            }
        });
        
        if (!leaks.isEmpty()) {
            logger.warn("资源泄漏检测: 发现{}个潜在泄漏资源", leaks.size());
            for (Map<String, Object> leak : leaks) {
                logger.warn("资源泄漏详情: resourceId={}, holdTime={}ms, caller={}", 
                        leak.get("resourceId"), leak.get("holdTimeMs"), leak.get("callerInfo"));
            }
        } else {
            logger.debug("资源泄漏检测: 未发现泄漏资源");
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkTime", now);
        result.put("thresholdMs", LEAK_THRESHOLD_MS);
        result.put("totalActiveResources", activeResources.get());
        result.put("potentialLeakCount", leaks.size());
        result.put("potentialLeaks", leaks);
        return ResponseEntity.ok(result);
    }
    
    @Scheduled(fixedRate = 30000)
    public void scheduledLeakDetection() {
        long now = System.currentTimeMillis();
        int leakCount = 0;
        
        for (Map.Entry<Long, Long> entry : activeResourceMap.entrySet()) {
            long holdTime = now - entry.getValue();
            if (holdTime > LEAK_THRESHOLD_MS) {
                leakCount++;
                ResourceMetadata metadata = resourceMetadataMap.get(entry.getKey());
                logger.warn("定时资源泄漏检测: 发现潜在泄漏 - resourceId={}, holdTime={}ms, caller={}", 
                        entry.getKey(), holdTime, metadata != null ? metadata.callerInfo() : "unknown");
            }
        }
        
        int activeAsyncCount = asyncTaskCounter.get() - asyncTaskCompletedCounter.get();
        if (activeAsyncCount > 10) {
            logger.warn("定时资源泄漏检测: 存在{}个活跃异步任务", activeAsyncCount);
        }
        
        if (leakCount == 0 && activeAsyncCount == 0) {
            logger.debug("定时资源泄漏检测: 系统状态正常, 活跃资源={}, 活跃异步任务=0", activeResources.get());
        }
    }
    
    @PreDestroy
    public void cleanup() {
        int resourceCount = activeResourceMap.size();
        int asyncCount = asyncTaskCounter.get() - asyncTaskCompletedCounter.get();
        
        if (resourceCount > 0 || asyncCount > 0) {
            logger.warn("应用关闭: 检测到未清理资源 - 活跃资源={}, 活跃异步任务={}", resourceCount, asyncCount);
        }
        
        activeResourceMap.clear();
        resourceMetadataMap.clear();
        asyncTaskRegistry.clear();
        
        logger.info("资源管理控制器已清理完成");
    }
    
    private String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 3) {
            StackTraceElement caller = stackTrace[3];
            return caller.getClassName() + "." + caller.getMethodName() + ":" + caller.getLineNumber();
        }
        return "unknown";
    }
    
    record ResourceMetadata(long resourceId, String threadName, String callerInfo, long createTime) {}
    
    record AsyncTaskInfo(String taskId, String type, long startTime, String threadName) {}
}
