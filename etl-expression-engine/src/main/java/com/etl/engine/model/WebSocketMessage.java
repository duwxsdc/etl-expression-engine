package com.etl.engine.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Collections;

/**
 * WebSocket消息
 * 
 * @param type 消息类型: EXECUTE/RESULT/ERROR/SESSION
 * @param requestId 请求ID（用于匹配请求和响应）
 * @param sessionId 会话ID
 * @param content 消息内容
 * @param data 数据负载
 * @param timestamp 时间戳
 */
public record WebSocketMessage(
    MessageType type,
    String requestId,
    String sessionId,
    String content,
    Map<String, Object> data,
    LocalDateTime timestamp
) {
    public enum MessageType {
        EXECUTE,
        RESULT,
        ERROR,
        SESSION,
        HEARTBEAT
    }

    public static WebSocketMessage execute(String content) {
        return new WebSocketMessage(
            MessageType.EXECUTE, null, null, content, 
            Collections.emptyMap(), LocalDateTime.now()
        );
    }

    public static WebSocketMessage execute(String requestId, String content) {
        return new WebSocketMessage(
            MessageType.EXECUTE, requestId, null, content, 
            Collections.emptyMap(), LocalDateTime.now()
        );
    }

    public static WebSocketMessage result(String sessionId, Map<String, Object> data) {
        return new WebSocketMessage(
            MessageType.RESULT, null, sessionId, null, data, LocalDateTime.now()
        );
    }

    public static WebSocketMessage result(String sessionId, String requestId, Map<String, Object> data) {
        return new WebSocketMessage(
            MessageType.RESULT, requestId, sessionId, null, data, LocalDateTime.now()
        );
    }

    public static WebSocketMessage error(String sessionId, String errorMessage) {
        return new WebSocketMessage(
            MessageType.ERROR, null, sessionId, errorMessage,
            Collections.emptyMap(), LocalDateTime.now()
        );
    }

    public static WebSocketMessage error(String sessionId, String requestId, String errorMessage) {
        return new WebSocketMessage(
            MessageType.ERROR, requestId, sessionId, errorMessage,
            Collections.emptyMap(), LocalDateTime.now()
        );
    }

    public static WebSocketMessage session(String sessionId) {
        return new WebSocketMessage(
            MessageType.SESSION, null, sessionId, "Session established",
            Map.of("sessionId", sessionId), LocalDateTime.now()
        );
    }
}
