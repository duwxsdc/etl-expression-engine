package com.etl.engine.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/mock/third")
public class MockThirdPartyController {
    
    private static final Logger logger = LoggerFactory.getLogger(MockThirdPartyController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final RestClient restClient;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
        10, Thread.ofVirtual().factory()
    );
    
    private final ConcurrentHashMap<String, MockTask> pendingTasks = new ConcurrentHashMap<>();
    
    public MockThirdPartyController(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }
    
    @PostMapping("/async")
    public ResponseEntity<?> handleAsyncRequest(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Callback-EventId", required = false) String headerEventId,
            @RequestHeader(value = "X-Callback-TargetIp", required = false) String headerTargetIp,
            @RequestHeader(value = "X-Callback-TargetPort", required = false) Integer headerTargetPort) {
        String eventId = headerEventId != null ? headerEventId : extractString(request, "eventId", null);
        String targetIp = headerTargetIp != null ? headerTargetIp : extractString(request, "targetIp", null);
        Integer targetPort = headerTargetPort != null ? headerTargetPort : extractInt(request, "targetPort", null);
        Integer delayMs = extractInt(request, "delayMs", null);
        if (delayMs == null) delayMs = 3000;
        
        logger.info("收到异步请求: eventId={}, targetIp={}, targetPort={}, delayMs={}", 
            eventId, targetIp, targetPort, delayMs);
        
        String taskId = "task-" + System.currentTimeMillis();
        MockTask task = new MockTask(taskId, eventId, targetIp, targetPort, delayMs);
        pendingTasks.put(taskId, task);
        
        scheduleCallback(task, request);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "taskId", taskId,
            "eventId", eventId,
            "message", "请求已接收，将在" + delayMs + "ms后回调",
            "timestamp", Instant.now()
        ));
    }
    
    @PostMapping("/async/config")
    public ResponseEntity<?> handleConfigRequest(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Callback-EventId", required = false) String headerEventId,
            @RequestHeader(value = "X-Callback-TargetIp", required = false) String headerTargetIp,
            @RequestHeader(value = "X-Callback-TargetPort", required = false) Integer headerTargetPort,
            @RequestParam(required = false, defaultValue = "3000") Integer delayMs,
            @RequestParam(required = false, defaultValue = "true") Boolean success,
            @RequestParam(required = false, defaultValue = "false") Boolean timeout,
            @RequestParam(required = false, defaultValue = "false") Boolean duplicate,
            @RequestParam(required = false, defaultValue = "false") Boolean fail) {
        
        String eventId = headerEventId != null ? headerEventId : extractString(request, "eventId", null);
        String targetIp = headerTargetIp != null ? headerTargetIp : extractString(request, "targetIp", null);
        Integer targetPort = headerTargetPort != null ? headerTargetPort : extractInt(request, "targetPort", null);
        
        logger.info("收到可配置异步请求: eventId={}, targetIp={}, targetPort={}, delayMs={}, success={}, timeout={}, duplicate={}, fail={}",
            eventId, targetIp, targetPort, delayMs, success, timeout, duplicate, fail);
        
        String taskId = "config-task-" + System.currentTimeMillis();
        
        if (!timeout) {
            MockTask task = new MockTask(taskId, eventId, targetIp, targetPort, delayMs);
            task.setSuccess(success);
            task.setDuplicate(duplicate);
            task.setFail(fail);
            pendingTasks.put(taskId, task);
            
            scheduleCallbackWithConfig(task, request);
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "taskId", taskId,
            "eventId", eventId,
            "config", Map.of(
                "delayMs", delayMs,
                "willSuccess", success,
                "willTimeout", timeout,
                "willDuplicate", duplicate,
                "willFail", fail
            ),
            "timestamp", Instant.now()
        ));
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<?> getPendingTasks() {
        return ResponseEntity.ok(Map.of(
            "count", pendingTasks.size(),
            "tasks", pendingTasks.keySet()
        ));
    }
    
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        MockTask removed = pendingTasks.remove(taskId);
        return ResponseEntity.ok(Map.of(
            "success", removed != null,
            "taskId", taskId
        ));
    }
    
    @PostMapping("/immediate-callback")
    public ResponseEntity<?> immediateCallback(@RequestBody Map<String, Object> request) {
        String eventId = extractString(request, "eventId", null);
        String targetIp = extractString(request, "targetIp", null);
        Integer targetPort = extractInt(request, "targetPort", null);
        
        if (eventId == null || targetIp == null || targetPort == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "缺少必要参数: eventId, targetIp, targetPort"
            ));
        }
        
        logger.info("立即回调: eventId={}, target={}:{}", eventId, targetIp, targetPort);
        
        try {
            String callbackUrl = String.format("http://%s:%d/api/public/callback", targetIp, targetPort);
            
            Map<String, Object> callbackData = Map.of(
                "eventId", eventId,
                "targetIp", targetIp,
                "targetPort", targetPort,
                "payload", request.getOrDefault("payload", Map.of("result", "immediate")),
                "status", "SUCCESS",
                "timestamp", Instant.now()
            );
            
            String response = restClient.post()
                .uri(callbackUrl)
                .header("Content-Type", "application/json")
                .body(callbackData)
                .retrieve()
                .body(String.class);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", eventId,
                "callbackResponse", response
            ));
            
        } catch (Exception e) {
            logger.error("立即回调失败: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    private void scheduleCallback(MockTask task, Map<String, Object> originalRequest) {
        executor.schedule(() -> {
            try {
                String callbackUrl = String.format("http://%s:%d/api/public/callback", 
                    task.targetIp(), task.targetPort());
                
                Map<String, Object> callbackData = Map.of(
                    "eventId", task.eventId(),
                    "targetIp", task.targetIp(),
                    "targetPort", task.targetPort(),
                    "payload", Map.of(
                        "result", "processed",
                        "originalRequest", originalRequest,
                        "processedAt", Instant.now()
                    ),
                    "status", "SUCCESS",
                    "message", "处理完成",
                    "timestamp", Instant.now()
                );
                
                logger.info("开始回调: eventId={}, url={}", task.eventId(), callbackUrl);
                
                String response = restClient.post()
                    .uri(callbackUrl)
                    .header("Content-Type", "application/json")
                    .body(callbackData)
                    .retrieve()
                    .body(String.class);
                
                logger.info("回调成功: eventId={}, response={}", task.eventId(), response);
                pendingTasks.remove(task.taskId());
                
            } catch (Exception e) {
                logger.error("回调失败: eventId={}, error={}", task.eventId(), e.getMessage());
            }
        }, task.delayMs(), TimeUnit.MILLISECONDS);
    }
    
    private void scheduleCallbackWithConfig(MockTask task, Map<String, Object> originalRequest) {
        executor.schedule(() -> {
            try {
                if (task.fail()) {
                    logger.info("模拟回调失败: eventId={}", task.eventId());
                    pendingTasks.remove(task.taskId());
                    return;
                }
                
                String callbackUrl = String.format("http://%s:%d/api/public/callback", 
                    task.targetIp(), task.targetPort());
                
                String status = task.success() ? "SUCCESS" : "ERROR";
                
                Map<String, Object> callbackData = new ConcurrentHashMap<>();
                callbackData.put("eventId", task.eventId());
                callbackData.put("targetIp", task.targetIp());
                callbackData.put("targetPort", task.targetPort());
                callbackData.put("status", status);
                callbackData.put("timestamp", Instant.now());
                
                if (task.success()) {
                    callbackData.put("payload", Map.of(
                        "result", "processed_with_config",
                        "originalRequest", originalRequest
                    ));
                    callbackData.put("message", "处理完成");
                } else {
                    callbackData.put("message", "模拟处理失败");
                }
                
                logger.info("开始回调(配置模式): eventId={}, url={}", task.eventId(), callbackUrl);
                
                String response = restClient.post()
                    .uri(callbackUrl)
                    .header("Content-Type", "application/json")
                    .body(callbackData)
                    .retrieve()
                    .body(String.class);
                
                logger.info("回调成功: eventId={}, response={}", task.eventId(), response);
                
                if (task.duplicate()) {
                    executor.schedule(() -> {
                        try {
                            logger.info("发送重复回调: eventId={}", task.eventId());
                            restClient.post()
                                .uri(callbackUrl)
                                .header("Content-Type", "application/json")
                                .body(callbackData)
                                .retrieve()
                                .body(String.class);
                        } catch (Exception e) {
                            logger.error("重复回调失败: {}", e.getMessage());
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                }
                
                pendingTasks.remove(task.taskId());
                
            } catch (Exception e) {
                logger.error("回调失败: eventId={}, error={}", task.eventId(), e.getMessage());
            }
        }, task.delayMs(), TimeUnit.MILLISECONDS);
    }
    
    private String extractString(Map<String, Object> data, String key, String headerKey) {
        Object value = data.get(key);
        if (value == null && headerKey != null) {
            value = data.get(headerKey);
        }
        return value != null ? String.valueOf(value) : null;
    }
    
    private Integer extractInt(Map<String, Object> data, String key, String headerKey) {
        Object value = data.get(key);
        if (value == null && headerKey != null) {
            value = data.get(headerKey);
        }
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public static class MockTask {
        private final String taskId;
        private final String eventId;
        private final String targetIp;
        private final int targetPort;
        private final int delayMs;
        private boolean success = true;
        private boolean duplicate = false;
        private boolean fail = false;
        
        public MockTask(String taskId, String eventId, String targetIp, int targetPort, int delayMs) {
            this.taskId = taskId;
            this.eventId = eventId;
            this.targetIp = targetIp;
            this.targetPort = targetPort;
            this.delayMs = delayMs;
        }
        
        public String taskId() { return taskId; }
        public String eventId() { return eventId; }
        public String targetIp() { return targetIp; }
        public int targetPort() { return targetPort; }
        public int delayMs() { return delayMs; }
        public boolean success() { return success; }
        public boolean duplicate() { return duplicate; }
        public boolean fail() { return fail; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
        public void setFail(boolean fail) { this.fail = fail; }
    }
}
