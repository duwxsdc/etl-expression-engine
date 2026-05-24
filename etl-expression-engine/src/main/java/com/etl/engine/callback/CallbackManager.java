package com.etl.engine.callback;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class CallbackManager {
    
    private static final Logger logger = LoggerFactory.getLogger(CallbackManager.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_DELAY_MS = 500L;
    private static final int LOG_RETENTION_DAYS = 30;
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService executor;
    private final ConcurrentLinkedDeque<CallbackLog> callbackLogs = new ConcurrentLinkedDeque<>();
    private final Map<String, CallbackStatus> callbackStatusMap = new ConcurrentHashMap<>();
    private final Set<String> processedCallbacks = ConcurrentHashMap.newKeySet();
    
    public CallbackManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executor = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "callback-executor");
            t.setDaemon(true);
            return t;
        });
        
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "callback-log-cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::cleanupOldLogs, 1, 1, TimeUnit.HOURS);
    }
    
    public String registerCallback(CallbackRequest request) {
        String callbackId = "cb-" + UUID.randomUUID().toString().substring(0, 8);
        
        CallbackStatus status = new CallbackStatus(
                callbackId, request.getCallbackUrl(), CallbackState.PENDING,
                Instant.now().getEpochSecond(), 0, null, null
        );
        callbackStatusMap.put(callbackId, status);
        executor.submit(() -> executeCallback(callbackId, request));
        
        logger.info("注册回调: id={}, url={}, trigger={}", 
                callbackId, request.getCallbackUrl(), request.getTriggerEvent());
        return callbackId;
    }
    
    public String registerCallback(String callbackUrl, String triggerEvent, Object data) {
        return registerCallback(new CallbackRequest(callbackUrl, triggerEvent, "POST", 
                Map.of("Content-Type", "application/json"), data, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_INTERVAL_MS));
    }
    
    private void executeCallback(String callbackId, CallbackRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            updateStatus(callbackId, CallbackState.PROCESSING);
            
            String requestId = UUID.randomUUID().toString();
            if (!processedCallbacks.add(requestId)) {
                logger.info("回调已处理(幂等): callbackId={}", callbackId);
                updateStatus(callbackId, CallbackState.COMPLETED);
                return;
            }
            
            CallbackPayload payload = buildPayload(request);
            String payloadJson = JSON_MAPPER.writeValueAsString(payload);
            
            long delay = Math.min(System.currentTimeMillis() - startTime, MAX_DELAY_MS);
            if (delay < MAX_DELAY_MS) Thread.sleep(MAX_DELAY_MS - delay);
            
            CallbackResult result = sendCallback(request, payloadJson, callbackId, 0);
            
            if (result.success()) {
                updateStatus(callbackId, CallbackState.COMPLETED, result.statusCode(), result.responseBody());
                logCallback(callbackId, request, payloadJson, result, null, 0);
                logger.info("回调成功: id={}, statusCode={}", callbackId, result.statusCode());
            } else {
                handleCallbackFailure(callbackId, request, payloadJson, result);
            }
        } catch (Exception e) {
            logger.error("回调执行异常: id={}, error={}", callbackId, e.getMessage());
            updateStatus(callbackId, CallbackState.FAILED, -1, e.getMessage());
            logCallback(callbackId, request, null, new CallbackResult(false, -1, null, e.getMessage()), e, 0);
        }
    }
    
    private void handleCallbackFailure(String callbackId, CallbackRequest request, 
                                        String payloadJson, CallbackResult result) {
        int maxRetries = request.getMaxRetries() != null ? request.getMaxRetries() : DEFAULT_MAX_RETRIES;
        long retryInterval = request.getRetryIntervalMs() != null ? request.getRetryIntervalMs() : DEFAULT_RETRY_INTERVAL_MS;
        CallbackStatus status = callbackStatusMap.get(callbackId);
        int retryCount = status != null ? status.retryCount() : 0;
        
        if (retryCount < maxRetries) {
            int newRetryCount = retryCount + 1;
            long backoffDelay = retryInterval * (long) Math.pow(2, retryCount);
            updateStatus(callbackId, CallbackState.RETRYING, result.statusCode(), result.errorMessage());
            logger.warn("回调失败，准备重试: id={}, retry={}/{}, delay={}ms", 
                    callbackId, newRetryCount, maxRetries, backoffDelay);
            executor.schedule(() -> retryCallback(callbackId, request, payloadJson, newRetryCount), 
                    backoffDelay, TimeUnit.MILLISECONDS);
        } else {
            updateStatus(callbackId, CallbackState.FAILED, result.statusCode(), result.errorMessage());
            logCallback(callbackId, request, payloadJson, result, null, retryCount);
            logger.error("回调最终失败: id={}, retries={}", callbackId, maxRetries);
        }
    }
    
    private void retryCallback(String callbackId, CallbackRequest request, 
                                String payloadJson, int retryCount) {
        try {
            CallbackResult result = sendCallback(request, payloadJson, callbackId, retryCount);
            if (result.success()) {
                updateStatus(callbackId, CallbackState.COMPLETED, result.statusCode(), result.responseBody());
                logCallback(callbackId, request, payloadJson, result, null, retryCount);
                logger.info("回调重试成功: id={}, retryCount={}", callbackId, retryCount);
            } else {
                handleCallbackFailure(callbackId, request, payloadJson, result);
            }
        } catch (Exception e) {
            handleCallbackFailure(callbackId, request, payloadJson, 
                    new CallbackResult(false, -1, null, e.getMessage()));
        }
    }
    
    private CallbackResult sendCallback(CallbackRequest request, String payloadJson, 
                                         String callbackId, int retryCount) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.getCallbackUrl()))
                    .timeout(Duration.ofSeconds(30));
            
            String method = request.getMethod() != null ? request.getMethod() : "POST";
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if ("POST".equalsIgnoreCase(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));
            } else if ("PUT".equalsIgnoreCase(method)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));
            }
            
            if (request.getHeaders() != null) request.getHeaders().forEach(builder::header);
            builder.header("X-Callback-Id", callbackId);
            builder.header("X-Callback-Retry", String.valueOf(retryCount));
            
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new CallbackResult(success, response.statusCode(), response.body(), 
                    success ? null : "HTTP " + response.statusCode());
        } catch (Exception e) {
            return new CallbackResult(false, -1, null, e.getMessage());
        }
    }
    
    private CallbackPayload buildPayload(CallbackRequest request) {
        return new CallbackPayload(
                UUID.randomUUID().toString(),
                request.getTriggerEvent(),
                Instant.now().toString(),
                request.getTriggerEvent().contains("SUCCESS") ? "SUCCESS" : 
                        request.getTriggerEvent().contains("FAIL") ? "FAILURE" : "UNKNOWN",
                Map.of("source", "etl-expression-engine", "version", "1.0.0"),
                request.getData()
        );
    }
    
    private void updateStatus(String callbackId, CallbackState state) {
        updateStatus(callbackId, state, null, null);
    }
    
    private void updateStatus(String callbackId, CallbackState state, 
                               Integer statusCode, String errorMessage) {
        CallbackStatus current = callbackStatusMap.get(callbackId);
        if (current != null) {
            callbackStatusMap.put(callbackId, new CallbackStatus(
                    callbackId, current.callbackUrl(), state, current.createdAt(),
                    state == CallbackState.RETRYING ? current.retryCount() + 1 : current.retryCount(),
                    statusCode, errorMessage
            ));
        }
    }
    
    private void logCallback(String callbackId, CallbackRequest request, String requestPayload,
                             CallbackResult result, Exception exception, int retryCount) {
        CallbackLog log = new CallbackLog(
                UUID.randomUUID().toString(), callbackId, request.getCallbackUrl(),
                request.getTriggerEvent(), requestPayload, result.statusCode(),
                result.responseBody(), result.success() ? "SUCCESS" : "FAILURE",
                exception != null ? exception.getMessage() : result.errorMessage(),
                retryCount, Instant.now().getEpochSecond()
        );
        callbackLogs.addFirst(log);
        while (callbackLogs.size() > 10000) callbackLogs.removeLast();
    }
    
    private void cleanupOldLogs() {
        long cutoffTime = Instant.now().minusSeconds(LOG_RETENTION_DAYS * 24 * 3600L).getEpochSecond();
        int removed = 0;
        Iterator<CallbackLog> iterator = callbackLogs.descendingIterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp() < cutoffTime) { iterator.remove(); removed++; }
            else break;
        }
        if (removed > 0) logger.info("清理过期回调日志: count={}", removed);
    }
    
    public Optional<CallbackStatus> getCallbackStatus(String callbackId) {
        return Optional.ofNullable(callbackStatusMap.get(callbackId));
    }
    
    public List<CallbackLog> getCallbackLogs(String callbackId) {
        return callbackLogs.stream().filter(log -> log.callbackId().equals(callbackId)).toList();
    }
    
    public List<CallbackLog> getRecentLogs(int limit) {
        return callbackLogs.stream().limit(Math.min(limit, 100)).toList();
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Long> stateCounts = new LinkedHashMap<>();
        for (CallbackState state : CallbackState.values()) stateCounts.put(state.name(), 0L);
        callbackStatusMap.values().forEach(s -> stateCounts.merge(s.state().name(), 1L, Long::sum));
        return Map.of("totalCallbacks", callbackStatusMap.size(), 
                "totalLogs", callbackLogs.size(), "stateDistribution", stateCounts);
    }
    
    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
        logger.info("CallbackManager已关闭");
    }
}
