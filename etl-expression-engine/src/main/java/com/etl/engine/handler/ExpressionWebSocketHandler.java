package com.etl.engine.handler;

import com.etl.engine.context.SessionContext;
import com.etl.engine.context.SessionContextManager;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.model.ExpressionResult;
import com.etl.engine.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket消息处理器
 * 处理表达式执行请求和会话管理
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class ExpressionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionWebSocketHandler.class);

    private final SessionContextManager sessionContextManager;
    private final MvelSandboxEngine mvelSandboxEngine;
    private final ObjectMapper objectMapper;

    private final Map<String, SessionContext> webSocketSessionMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;

    public ExpressionWebSocketHandler(
            SessionContextManager sessionContextManager,
            MvelSandboxEngine mvelSandboxEngine,
            ObjectMapper objectMapper) {
        this.sessionContextManager = sessionContextManager;
        this.mvelSandboxEngine = mvelSandboxEngine;
        this.objectMapper = objectMapper;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-executor");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionContext context = sessionContextManager.createSession();
        webSocketSessionMap.put(session.getId(), context);

        WebSocketMessage message = WebSocketMessage.session(context.getSessionId());
        sendMessage(session, message);

        logger.info("WebSocket连接建立: wsSessionId={}, sessionId={}", 
                session.getId(), context.getSessionId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        SessionContext context = webSocketSessionMap.get(session.getId());
        if (context == null) {
            sendMessage(session, WebSocketMessage.error(null, "会话不存在"));
            return;
        }

        if (payload.trim().startsWith("{")) {
            handleJsonMessage(session, context, payload);
        } else {
            handlePlainExpression(session, context, payload);
        }
    }

    private void handleJsonMessage(WebSocketSession session, SessionContext context, String payload) {
        try {
            WebSocketMessage message = objectMapper.readValue(payload, WebSocketMessage.class);

            if (message.type() == WebSocketMessage.MessageType.HEARTBEAT) {
                sendMessage(session, WebSocketMessage.session(context.getSessionId()));
                return;
            }

            if (message.type() == WebSocketMessage.MessageType.EXECUTE) {
                String expression = message.content();
                executeAndSendResult(session, context, expression);
            }
        } catch (Exception e) {
            logger.error("JSON消息解析失败", e);
            sendMessage(session, WebSocketMessage.error(context.getSessionId(), 
                    "消息解析失败: " + e.getMessage()));
        }
    }

    private void handlePlainExpression(WebSocketSession session, SessionContext context, String expression) {
        executeAndSendResult(session, context, expression);
    }

    private void executeAndSendResult(WebSocketSession session, SessionContext context, String expression) {
        executorService.submit(() -> {
            try {
                ExpressionResult result = mvelSandboxEngine.execute(expression, context);

                WebSocketMessage response = WebSocketMessage.result(
                    context.getSessionId(),
                    toResultMap(result)
                );
                sendMessage(session, response);
            } catch (Exception e) {
                logger.error("表达式执行异常: sessionId={}", context.getSessionId(), e);
                sendMessage(session, WebSocketMessage.error(
                    context.getSessionId(), 
                    "执行异常: " + e.getMessage()
                ));
            }
        });
    }

    private Map<String, Object> toResultMap(ExpressionResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", result.success());
        map.put("sessionId", result.sessionId());
        map.put("expression", result.expression() != null ? result.expression() : "");
        map.put("result", result.result() != null ? result.result() : "");
        map.put("errorMessage", result.errorMessage() != null ? result.errorMessage() : "");
        map.put("assignedVariables", result.assignedVariables());
        map.put("timestamp", result.timestamp().toString());
        return map;
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            logger.error("发送WebSocket消息失败: sessionId={}", 
                    webSocketSessionMap.get(session.getId()) != null ? 
                    webSocketSessionMap.get(session.getId()).getSessionId() : "unknown", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionContext context = webSocketSessionMap.remove(session.getId());
        if (context != null) {
            sessionContextManager.destroySession(context.getSessionId());
            logger.info("WebSocket连接关闭: wsSessionId={}, sessionId={}, status={}", 
                    session.getId(), context.getSessionId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误: wsSessionId={}", session.getId(), exception);

        SessionContext context = webSocketSessionMap.get(session.getId());
        if (context != null) {
            sendMessage(session, WebSocketMessage.error(
                context.getSessionId(),
                "传输错误: " + exception.getMessage()
            ));
        }
    }

    public int getActiveConnectionCount() {
        return webSocketSessionMap.size();
    }
}
