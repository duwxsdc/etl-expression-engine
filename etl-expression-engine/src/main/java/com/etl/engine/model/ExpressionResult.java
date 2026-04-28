package com.etl.engine.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Collections;

/**
 * 表达式执行结果
 * 
 * @param success 是否执行成功
 * @param sessionId 会话ID
 * @param expression 原始表达式
 * @param result 执行结果
 * @param errorMessage 错误信息
 * @param assignedVariables 赋值的变量
 * @param timestamp 时间戳
 */
public record ExpressionResult(
    boolean success,
    String sessionId,
    String expression,
    Object result,
    String errorMessage,
    Map<String, Object> assignedVariables,
    LocalDateTime timestamp
) {
    public static ExpressionResult success(String sessionId, String expression, 
                                           Object result, Map<String, Object> assignedVariables) {
        return new ExpressionResult(
            true, sessionId, expression, result, null, 
            assignedVariables != null ? assignedVariables : Collections.emptyMap(),
            LocalDateTime.now()
        );
    }

    public static ExpressionResult failure(String sessionId, String expression, String errorMessage) {
        return new ExpressionResult(
            false, sessionId, expression, null, errorMessage,
            Collections.emptyMap(), LocalDateTime.now()
        );
    }
}
