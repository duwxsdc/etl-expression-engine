package com.etl.engine.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话管理器
 * 管理本节点的WebSocket会话映射
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class WebSocketSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionManager.class);
    
    private final Map<String, WebSocketSession> sessionIdToWsSession = new ConcurrentHashMap<>();
    private final Map<String, String> wsSessionIdToSessionId = new ConcurrentHashMap<>();
    
    /**
     * 注册会话
     * @param sessionId 业务会话ID
     * @param wsSession WebSocket会话
     */
    public void registerSession(String sessionId, WebSocketSession wsSession) {
        sessionIdToWsSession.put(sessionId, wsSession);
        wsSessionIdToSessionId.put(wsSession.getId(), sessionId);
        logger.info("注册WebSocket会话: sessionId={}, wsSessionId={}", sessionId, wsSession.getId());
    }
    
    /**
     * 注销会话
     * @param sessionId 业务会话ID
     */
    public void unregisterSession(String sessionId) {
        WebSocketSession wsSession = sessionIdToWsSession.remove(sessionId);
        if (wsSession != null) {
            wsSessionIdToSessionId.remove(wsSession.getId());
            logger.info("注销WebSocket会话: sessionId={}, wsSessionId={}", sessionId, wsSession.getId());
        }
    }
    
    /**
     * 通过wsSessionId注销会话
     * @param wsSessionId WebSocket会话ID
     * @return 业务会话ID
     */
    public String unregisterByWsSessionId(String wsSessionId) {
        String sessionId = wsSessionIdToSessionId.remove(wsSessionId);
        if (sessionId != null) {
            sessionIdToWsSession.remove(sessionId);
            logger.info("通过wsSessionId注销会话: wsSessionId={}, sessionId={}", wsSessionId, sessionId);
        }
        return sessionId;
    }
    
    /**
     * 检查会话是否存在（本地）
     * @param sessionId 业务会话ID
     * @return 是否存在
     */
    public boolean hasSession(String sessionId) {
        WebSocketSession session = sessionIdToWsSession.get(sessionId);
        return session != null && session.isOpen();
    }
    
    /**
     * 获取WebSocket会话
     * @param sessionId 业务会话ID
     * @return WebSocket会话
     */
    public WebSocketSession getWebSocketSession(String sessionId) {
        return sessionIdToWsSession.get(sessionId);
    }
    
    /**
     * 发送消息到指定会话
     * @param sessionId 业务会话ID
     * @param message 消息内容（JSON字符串）
     * @return 是否发送成功
     */
    public boolean sendMessage(String sessionId, String message) {
        WebSocketSession session = sessionIdToWsSession.get(sessionId);
        if (session == null || !session.isOpen()) {
            logger.warn("WebSocket会话不存在或已关闭: sessionId={}", sessionId);
            return false;
        }
        
        try {
            session.sendMessage(new TextMessage(message));
            logger.debug("发送WebSocket消息: sessionId={}, length={}", sessionId, message.length());
            return true;
        } catch (IOException e) {
            logger.error("发送WebSocket消息失败: sessionId={}", sessionId, e);
            return false;
        }
    }
    
    /**
     * 获取活跃会话数量
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return (int) sessionIdToWsSession.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
    }
    
    /**
     * 获取业务会话ID
     * @param wsSessionId WebSocket会话ID
     * @return 业务会话ID
     */
    public String getSessionId(String wsSessionId) {
        return wsSessionIdToSessionId.get(wsSessionId);
    }
}
