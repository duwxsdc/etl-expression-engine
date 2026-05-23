package com.etl.engine.controller;

import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.util.PerformanceTracker;
import com.etl.engine.util.RequestLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/etl/expression")
public class EtlExpressionController {
    
    private static final Logger logger = LoggerFactory.getLogger(EtlExpressionController.class);
    private static final String REDIS_KEY_PREFIX = "etl:context:";
    
    private final EtlContextManager etlContextManager;
    private final MvelExpressionEngine mvelExpressionEngine;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    public EtlExpressionController(EtlContextManager etlContextManager, 
                                  MvelExpressionEngine mvelExpressionEngine,
                                  @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.etlContextManager = etlContextManager;
        this.mvelExpressionEngine = mvelExpressionEngine;
        this.redisTemplate = redisTemplate;
    }
    
    @PostMapping("/execute")
    public ResponseEntity<ExecuteResult> executeExpression(
            @RequestBody String expression,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        
        String requestId = RequestLogger.generateRequestId();
        PerformanceTracker.startTracking(requestId, "executeExpression");
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/execute", "POST",
                Map.of("sessionId", sessionId != null ? sessionId : "NEW",
                       "expressionLength", expression != null ? expression.length() : 0));
        
        try {
            PerformanceTracker.recordPhase("REQUEST_RECEIVED");
            
            EtlContext context;
            if (sessionId == null || sessionId.trim().isEmpty()) {
                PerformanceTracker.recordPhase("SESSION_RESOLVE");
                context = etlContextManager.createSession();
                sessionId = context.getSessionId();
                PerformanceTracker.recordPhase("SESSION_CREATED", "New session created");
                RequestLogger.logStage(logger, requestId, "SESSION_CREATED",
                        "Created new session", Map.of("sessionId", sessionId,
                                "sessionResolveMs", PerformanceTracker.getPhaseDuration("SESSION_RESOLVE")));
            } else {
                PerformanceTracker.recordPhase("SESSION_RESOLVE");
                context = etlContextManager.getSession(sessionId);
                if (context == null) {
                    RequestLogger.logWarn(logger, requestId, "SESSION_NOT_FOUND",
                            "Session expired or invalid, creating new session",
                            Map.of("requestedSessionId", sessionId));
                    context = etlContextManager.createSession();
                    sessionId = context.getSessionId();
                } else {
                    RequestLogger.logDebug(logger, requestId, "SESSION_RESOLVED",
                            "Session found", Map.of("sessionId", sessionId,
                                    "variableCount", context.getVariableCount(),
                                    "sessionResolveMs", PerformanceTracker.getPhaseDuration("SESSION_RESOLVE")));
                }
                PerformanceTracker.recordPhase("SESSION_RESOLVED", "Session resolved from ID");
            }
            
            PerformanceTracker.recordPhase("PARAMS_PARSING");
            RequestLogger.logStage(logger, requestId, "PARAMS_PARSED",
                    "Expression parsed", Map.of("sessionId", sessionId,
                            "expressionPreview", expression != null && expression.length() > 100 
                                    ? expression.substring(0, 100) + "..." 
                                    : expression,
                            "parsingMs", PerformanceTracker.getPhaseDuration("PARAMS_PARSING")));
            
            PerformanceTracker.recordPhase("EXPRESSION_COMPILE");
            RequestLogger.logStage(logger, requestId, "EXECUTION_START",
                    "Starting expression execution", Map.of("sessionId", sessionId,
                            "compileMs", PerformanceTracker.getPhaseDuration("EXPRESSION_COMPILE")));
            
            PerformanceTracker.recordPhase("EXPRESSION_EXECUTE");
            ExecuteResult result = mvelExpressionEngine.execute(expression, context);
            long executionMs = PerformanceTracker.getPhaseDuration("EXPRESSION_EXECUTE");
            
            RequestLogger.logStage(logger, requestId, "EXECUTION_COMPLETE",
                    "Expression execution finished", Map.of("sessionId", sessionId,
                            "success", result.success(),
                            "executionMs", executionMs));
            
            if (result.success()) {
                PerformanceTracker.recordPhase("SESSION_UPDATE");
                etlContextManager.updateSession(context);
                RequestLogger.logDebug(logger, requestId, "SESSION_UPDATED",
                        "Session context updated", Map.of("sessionId", sessionId,
                                "variableCount", context.getVariableCount(),
                                "sessionUpdateMs", PerformanceTracker.getPhaseDuration("SESSION_UPDATE")));
            }
            
            PerformanceTracker.recordPhase("RESPONSE_BUILD");
            
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.PerformanceSummary summary = PerformanceTracker.endTracking();
            if (summary != null) {
                summary.logBottleneckAnalysis(logger);
            }
            
            long durationMs = summary != null ? summary.getTotalDuration() : 0;
            RequestLogger.logSuccess(logger, requestId, "/etl/expression/execute", 
                    durationMs,
                    Map.of("sessionId", sessionId,
                           "success", result.success(),
                           "hasResult", result.finalResult() != null));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            PerformanceTracker.recordPhase("ERROR_HANDLING");
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.PerformanceSummary summary = PerformanceTracker.endTracking();
            if (summary != null) {
                summary.logBottleneckAnalysis(logger);
            }
            
            RequestLogger.logError(logger, requestId, "EXECUTION_ERROR",
                    "Expression execution failed: " + e.getMessage(), e);
            
            ExecuteResult errorResult = ExecuteResult.failure(
                sessionId != null ? sessionId : "unknown",
                expression,
                "服务器内部错误: " + e.getMessage(),
                new HashMap<>()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> destroySession(@PathVariable String sessionId) {
        String requestId = RequestLogger.generateRequestId();
        PerformanceTracker.startTracking(requestId, "destroySession");
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/session/" + sessionId, "DELETE",
                Map.of("sessionId", sessionId));
        
        try {
            PerformanceTracker.recordPhase("REQUEST_RECEIVED");
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                RequestLogger.logWarn(logger, requestId, "PARAMS_INVALID",
                        "Session ID is empty", null);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Session ID cannot be empty"));
            }

            PerformanceTracker.recordPhase("SESSION_LOOKUP");
            if (!etlContextManager.hasSession(sessionId)) {
                RequestLogger.logWarn(logger, requestId, "SESSION_NOT_FOUND",
                        "Session not found for deletion", Map.of("sessionId", sessionId));
                return ResponseEntity.ok(Map.of(
                    "success", false, "message", "Session not found: " + sessionId));
            }

            PerformanceTracker.recordPhase("SESSION_DESTROY");
            etlContextManager.destroySession(sessionId);
            
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.PerformanceSummary summary = PerformanceTracker.endTracking();
            if (summary != null) {
                summary.logBottleneckAnalysis(logger);
            }
            
            RequestLogger.logSuccess(logger, requestId, "/etl/expression/session/" + sessionId, 
                    summary != null ? summary.getTotalDuration() : 0,
                    Map.of("action", "SESSION_DESTROYED", "sessionId", sessionId));

            return ResponseEntity.ok(Map.of(
                "success", true, "message", "Session destroyed successfully", "sessionId", sessionId));

        } catch (Exception e) {
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.endTracking();
            RequestLogger.logError(logger, requestId, "SESSION_DESTROY_ERROR",
                    "Failed to destroy session: " + sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "message", "Failed to destroy session: " + e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        String requestId = RequestLogger.generateRequestId();
        PerformanceTracker.startTracking(requestId, "getSessionInfo");
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/session/" + sessionId, "GET",
                Map.of("sessionId", sessionId));
        
        try {
            PerformanceTracker.recordPhase("REQUEST_RECEIVED");
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Session ID cannot be empty"));
            }

            PerformanceTracker.recordPhase("SESSION_LOOKUP");
            EtlContext context = etlContextManager.getSession(sessionId);
            long lookupMs = PerformanceTracker.getPhaseDuration("SESSION_LOOKUP");
            
            if (context == null) {
                RequestLogger.logDebug(logger, requestId, "SESSION_NOT_FOUND",
                        "Session not found", Map.of("sessionId", sessionId, "lookupMs", lookupMs));
                return ResponseEntity.ok(Map.of(
                    "success", false, "message", "Session not found", "sessionId", sessionId));
            }

            PerformanceTracker.recordPhase("DATA_SERIALIZE");
            RequestLogger.logDebug(logger, requestId, "SESSION_INFO_RETURNED",
                    "Session info returned", Map.of("sessionId", sessionId,
                            "variableCount", context.getVariableCount(),
                            "lookupMs", lookupMs));

            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.PerformanceSummary summary = PerformanceTracker.endTracking();
            if (summary != null) {
                summary.logBottleneckAnalysis(logger);
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", context.getSessionId(),
                "variableCount", context.getVariableCount(),
                "createTime", context.getCreateTime(),
                "lastAccessTime", context.getLastAccessTime(),
                "variables", context.getAllVariables()
            ));

        } catch (Exception e) {
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.endTracking();
            RequestLogger.logError(logger, requestId, "SESSION_GET_ERROR",
                    "Failed to get session info: " + sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "message", "Failed to get session: " + e.getMessage()));
        }
    }

    @PostMapping("/session/new")
    public ResponseEntity<Map<String, Object>> createNewSession() {
        String requestId = RequestLogger.generateRequestId();
        PerformanceTracker.startTracking(requestId, "createNewSession");
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/session/new", "POST", Map.of());
        
        try {
            PerformanceTracker.recordPhase("REQUEST_RECEIVED");
            PerformanceTracker.recordPhase("SESSION_CREATE");
            
            EtlContext context = etlContextManager.createSession();
            
            long createMs = PerformanceTracker.getPhaseDuration("SESSION_CREATE");
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.PerformanceSummary summary = PerformanceTracker.endTracking();
            if (summary != null) {
                summary.logBottleneckAnalysis(logger);
            }
            
            RequestLogger.logSuccess(logger, requestId, "/etl/expression/session/new",
                    summary != null ? summary.getTotalDuration() : 0,
                    Map.of("action", "SESSION_CREATED", "sessionId", context.getSessionId(),
                           "createMs", createMs));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", context.getSessionId(),
                "message", "New session created successfully"
            ));

        } catch (Exception e) {
            PerformanceTracker.logPerformance(logger);
            PerformanceTracker.endTracking();
            RequestLogger.logError(logger, requestId, "SESSION_CREATE_ERROR",
                    "Failed to create session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "message", "Failed to create session: " + e.getMessage()));
        }
    }

    @GetMapping("/redis/sessions")
    public ResponseEntity<Map<String, Object>> listRedisSessions() {
        String requestId = RequestLogger.generateRequestId();
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/redis/sessions", "GET", Map.of());
        
        try {
            if (redisTemplate == null) {
                RequestLogger.logDebug(logger, requestId, "REDIS_NOT_AVAILABLE",
                        "Redis not connected, running in local mode", null);
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Redis not connected - running in local memory mode",
                    "mode", "local"
                ));
            }

            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            List<Map<String, Object>> sessions = new ArrayList<>();

            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    try {
                        Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
                        Object value = redisTemplate.opsForValue().get(key);
                        String sid = key.replace(REDIS_KEY_PREFIX, "");

                        Map<String, Object> sessionInfo = new LinkedHashMap<>();
                        sessionInfo.put("key", key);
                        sessionInfo.put("sessionId", sid);
                        sessionInfo.put("ttlSeconds", ttl);
                        sessionInfo.put("data", value);
                        sessions.add(sessionInfo);
                    } catch (Exception e) {
                        RequestLogger.logWarn(logger, requestId, "REDIS_KEY_READ_ERROR",
                                "Failed to read Redis key: " + key, null);
                    }
                }
            }

            RequestLogger.logSuccess(logger, requestId, "/etl/expression/redis/sessions", 0,
                    Map.of("totalSessions", sessions.size()));

            return ResponseEntity.ok(Map.of(
                "success", true, "mode", "redis",
                "totalSessions", sessions.size(), "sessions", sessions
            ));

        } catch (Exception e) {
            RequestLogger.logError(logger, requestId, "REDIS_LIST_ERROR",
                    "Failed to query Redis sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "message", "Failed to query Redis: " + e.getMessage()));
        }
    }

    @DeleteMapping("/redis/sessions")
    public ResponseEntity<Map<String, Object>> clearAllRedisSessions() {
        String requestId = RequestLogger.generateRequestId();
        
        RequestLogger.logRequest(logger, requestId, "/etl/expression/redis/sessions", "DELETE", Map.of());
        
        try {
            if (redisTemplate == null) {
                return ResponseEntity.ok(Map.of(
                    "success", false, "message", "Redis not connected", "mode", "local"));
            }

            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            int deletedCount = 0;
            if (keys != null && !keys.isEmpty()) {
                deletedCount = keys.size();
                redisTemplate.delete(keys);
            }

            RequestLogger.logSuccess(logger, requestId, "/etl/expression/redis/sessions", 0,
                    Map.of("action", "REDIS_CLEARED", "deletedCount", deletedCount));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Deleted " + deletedCount + " session keys",
                "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            RequestLogger.logError(logger, requestId, "REDIS_CLEAR_ERROR",
                    "Failed to clear Redis sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "message", "Failed to clear Redis: " + e.getMessage()));
        }
    }
}
