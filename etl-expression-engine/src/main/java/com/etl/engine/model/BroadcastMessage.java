package com.etl.engine.model;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

/**
 * 广播消息
 * 用于RocketMQ广播的表达式执行请求
 * 
 * @param requestId 请求唯一ID（幂等控制）
 * @param sessionId WebSocket会话ID
 * @param expression 表达式内容
 * @param vars 变量上下文
 * @param timeout 超时时间（毫秒）
 * @param sourceNodeId 发送节点ID
 */
public record BroadcastMessage(
    String requestId,
    String sessionId,
    String expression,
    Map<String, Object> vars,
    long timeout,
    String sourceNodeId
) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public static BroadcastMessage create(
            String sessionId, 
            String expression, 
            Map<String, Object> vars, 
            long timeout,
            String sourceNodeId) {
        return new BroadcastMessage(
            UUID.randomUUID().toString(),
            sessionId,
            expression,
            vars,
            timeout,
            sourceNodeId
        );
    }
    
    public static BroadcastMessage create(
            String requestId,
            String sessionId, 
            String expression, 
            Map<String, Object> vars, 
            long timeout,
            String sourceNodeId) {
        return new BroadcastMessage(requestId, sessionId, expression, vars, timeout, sourceNodeId);
    }
}
