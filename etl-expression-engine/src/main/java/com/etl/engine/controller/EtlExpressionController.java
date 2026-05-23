package com.etl.engine.controller;

import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.context.EtlContextScope;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * ETL表达式控制器
 * HTTP POST统一入口接口
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
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
    
    /**
     * 执行表达式
     * POST /etl/expression/execute
     * 请求头: X-Session-Id
     * 请求体: 纯文本多行表达式
     * 
     * @param expression 表达式字符串
     * @param sessionId 会话ID（从请求头获取）
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<ExecuteResult> executeExpression(
            @RequestBody String expression,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        
        try {
            // 获取或创建会话上下文
            EtlContext context;
            if (sessionId == null || sessionId.trim().isEmpty()) {
                context = etlContextManager.createSession();
                sessionId = context.getSessionId();
                logger.info("创建新会话: {}", sessionId);
            } else {
                context = etlContextManager.getSession(sessionId);
                if (context == null) {
                    logger.warn("会话不存在，创建新会话: {}", sessionId);
                    context = etlContextManager.createSession();
                    sessionId = context.getSessionId();
                }
            }
            
            // 执行表达式
            ExecuteResult result = mvelExpressionEngine.execute(expression, context);
            
            // 更新会话上下文到Redis
            if (result.success()) {
                etlContextManager.updateSession(context);
            }
            
            logger.debug("表达式执行完成: sessionId={}, success={}", sessionId, result.success());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("表达式执行异常", e);
            ExecuteResult errorResult = ExecuteResult.failure(
                sessionId != null ? sessionId : "unknown",
                expression,
                "服务器内部错误: " + e.getMessage(),
                new java.util.HashMap<>()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<java.util.Map<String, Object>> destroySession(@PathVariable String sessionId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Session ID cannot be empty"
                ));
            }

            if (!etlContextManager.hasSession(sessionId)) {
                return ResponseEntity.ok(java.util.Map.of(
                    "success", false,
                    "message", "Session not found: " + sessionId
                ));
            }

            etlContextManager.destroySession(sessionId);
            logger.info("会话已销毁: {}", sessionId);

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Session destroyed successfully",
                "sessionId", sessionId
            ));

        } catch (Exception e) {
            logger.error("销毁会话异常: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to destroy session: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<java.util.Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Session ID cannot be empty"
                ));
            }

            EtlContext context = etlContextManager.getSession(sessionId);
            if (context == null) {
                return ResponseEntity.ok(java.util.Map.of(
                    "success", false,
                    "message", "Session not found",
                    "sessionId", sessionId
                ));
            }

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "sessionId", context.getSessionId(),
                "variableCount", context.getVariableCount(),
                "createTime", context.getCreateTime(),
                "lastAccessTime", context.getLastAccessTime(),
                "variables", context.getAllVariables()
            ));

        } catch (Exception e) {
            logger.error("获取会话信息异常: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to get session: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/session/new")
    public ResponseEntity<java.util.Map<String, Object>> createNewSession() {
        try {
            EtlContext context = etlContextManager.createSession();
            logger.info("创建新会话: {}", context.getSessionId());

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "sessionId", context.getSessionId(),
                "message", "New session created successfully"
            ));

        } catch (Exception e) {
            logger.error("创建会话异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to create session: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/redis/sessions")
    public ResponseEntity<java.util.Map<String, Object>> listRedisSessions() {
        try {
            if (redisTemplate == null) {
                return ResponseEntity.ok(java.util.Map.of(
                    "success", false,
                    "message", "Redis not connected - running in local memory mode",
                    "mode", "local"
                ));
            }

            java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            java.util.List<java.util.Map<String, Object>> sessions = new java.util.ArrayList<>();

            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    try {
                        Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
                        Object value = redisTemplate.opsForValue().get(key);
                        String sessionId = key.replace(REDIS_KEY_PREFIX, "");

                        java.util.Map<String, Object> sessionInfo = new java.util.LinkedHashMap<>();
                        sessionInfo.put("key", key);
                        sessionInfo.put("sessionId", sessionId);
                        sessionInfo.put("ttlSeconds", ttl);
                        sessionInfo.put("data", value);
                        sessions.add(sessionInfo);
                    } catch (Exception e) {
                        logger.warn("Failed to read Redis key: {}", key, e);
                    }
                }
            }

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "mode", "redis",
                "totalSessions", sessions.size(),
                "sessions", sessions
            ));

        } catch (Exception e) {
            logger.error("查询Redis会话列表异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to query Redis: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/redis/sessions")
    public ResponseEntity<java.util.Map<String, Object>> clearAllRedisSessions() {
        try {
            if (redisTemplate == null) {
                return ResponseEntity.ok(java.util.Map.of(
                    "success", false,
                    "message", "Redis not connected",
                    "mode", "local"
                ));
            }

            java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            int deletedCount = 0;
            if (keys != null && !keys.isEmpty()) {
                deletedCount = keys.size();
                redisTemplate.delete(keys);
            }

            logger.info("清理Redis会话数据: 删除 {} 个Key", deletedCount);
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Deleted " + deletedCount + " session keys",
                "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            logger.error("清理Redis会话异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to clear Redis: " + e.getMessage()
            ));
        }
    }
}
