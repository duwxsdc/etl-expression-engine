package com.etl.engine.controller;

import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.context.EtlContextScope;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    private final EtlContextManager etlContextManager;
    private final MvelExpressionEngine mvelExpressionEngine;
    
    @Autowired
    public EtlExpressionController(EtlContextManager etlContextManager, 
                                  MvelExpressionEngine mvelExpressionEngine) {
        this.etlContextManager = etlContextManager;
        this.mvelExpressionEngine = mvelExpressionEngine;
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
}
