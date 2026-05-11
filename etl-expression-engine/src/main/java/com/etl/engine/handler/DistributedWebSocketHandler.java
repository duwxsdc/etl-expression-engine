package com.etl.engine.handler;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.SessionContext;
import com.etl.engine.context.SessionContextManager;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.idempotent.IdempotentController;
import com.etl.engine.model.BroadcastMessage;
import com.etl.engine.model.ExpressionResult;
import com.etl.engine.model.WebSocketMessage;
import com.etl.engine.mq.BroadcastMessageProducer;
import com.etl.engine.ws.WebSocketSessionManager;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分布式WebSocket消息处理器
 * 实现"本地优先 + MQ广播兜底"策略
 * 
 * 核心逻辑：
 * 1. WebSocket连接建立时，创建Session并保存在当前节点
 * 2. 请求到达时，检查sessionId对应的Session是否在本地
 * 3. 在本地 → 直接执行并通过WebSocket返回结果
 * 4. 不在本地 → 通过RocketMQ广播，由拥有Session的节点执行并返回
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class DistributedWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DistributedWebSocketHandler.class);

    private final SessionContextManager sessionContextManager;
    private final WebSocketSessionManager wsSessionManager;
    private final MvelSandboxEngine mvelSandboxEngine;
    private final BroadcastMessageProducer broadcastProducer;
    private final IdempotentController idempotentController;
    private final ObjectMapper objectMapper;
    private final EngineProperties properties;
    private final ExecutorService executorService;

    public DistributedWebSocketHandler(
            SessionContextManager sessionContextManager,
            WebSocketSessionManager wsSessionManager,
            MvelSandboxEngine mvelSandboxEngine,
            BroadcastMessageProducer broadcastProducer,
            IdempotentController idempotentController,
            ObjectMapper objectMapper,
            EngineProperties properties) {
        this.sessionContextManager = sessionContextManager;
        this.wsSessionManager = wsSessionManager;
        this.mvelSandboxEngine = mvelSandboxEngine;
        this.broadcastProducer = broadcastProducer;
        this.idempotentController = idempotentController;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-executor");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionContext context = sessionContextManager.createSession();
        String sessionId = context.getSessionId();
        
        wsSessionManager.registerSession(sessionId, session);

        WebSocketMessage message = WebSocketMessage.session(sessionId);
        sendMessageToSession(sessionId, message);

        logger.info("WebSocket连接建立: wsSessionId={}, sessionId={}, nodeId={}", 
                session.getId(), sessionId, properties.getNodeId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload.trim().startsWith("{")) {
            handleJsonMessage(payload);
        } else {
            logger.warn("收到非JSON格式的消息，已忽略。请使用JSON格式并携带sessionId");
        }
    }

    private void handleJsonMessage(String payload) {
        try {
            WebSocketMessage message = objectMapper.readValue(payload, WebSocketMessage.class);
            String sessionId = message.sessionId();
            String requestId = message.requestId() != null ? message.requestId() : UUID.randomUUID().toString();

            if (message.type() == WebSocketMessage.MessageType.HEARTBEAT) {
                handleHeartbeat(sessionId);
                return;
            }

            if (message.type() == WebSocketMessage.MessageType.EXECUTE) {
                String expression = message.content();
                handleExecuteRequest(sessionId, requestId, expression);
            }
        } catch (Exception e) {
            logger.error("JSON消息解析失败: {}", payload, e);
        }
    }

    private void handleHeartbeat(String sessionId) {
        if (sessionId != null && wsSessionManager.hasSession(sessionId)) {
            WebSocketMessage response = WebSocketMessage.session(sessionId);
            sendMessageToSession(sessionId, response);
        }
    }

    private void handleExecuteRequest(String sessionId, String requestId, String expression) {
        if (sessionId == null || sessionId.isBlank()) {
            logger.warn("请求缺少sessionId，无法处理: requestId={}", requestId);
            return;
        }

        if (expression == null || expression.isBlank()) {
            logger.warn("请求缺少表达式内容: sessionId={}, requestId={}", sessionId, requestId);
            return;
        }

        if (sessionContextManager.hasSession(sessionId)) {
            SessionContext context = sessionContextManager.getSession(sessionId);
            if (context != null) {
                logger.debug("Session在本地，直接执行: sessionId={}, nodeId={}", 
                        sessionId, properties.getNodeId());
                executeLocal(sessionId, context, requestId, expression);
            }
        } else {
            logger.info("Session不在本节点，广播请求: sessionId={}, requestId={}, currentNode={}", 
                    sessionId, requestId, properties.getNodeId());
            broadcastRequest(sessionId, requestId, expression, null);
        }
    }

    private void broadcastRequest(String sessionId, String requestId, 
                                  String expression, Map<String, Object> vars) {
        BroadcastMessage message = BroadcastMessage.create(
            requestId, sessionId, expression, vars, 
            properties.getExpressionTimeout(), 
            properties.getNodeId()
        );
        broadcastProducer.broadcastAsync(message);
    }

    private void executeLocal(String sessionId, SessionContext context, 
                              String requestId, String expression) {
        executorService.submit(() -> {
            try {
                if (!idempotentController.tryProcess(requestId)) {
                    logger.debug("重复请求，忽略: requestId={}", requestId);
                    return;
                }
                
                ExpressionResult result = mvelSandboxEngine.execute(expression, context);

                WebSocketMessage response = WebSocketMessage.result(
                    sessionId,
                    requestId,
                    toResultMap(result)
                );
                sendMessageToSession(sessionId, response);
                
            } catch (Exception e) {
                logger.error("表达式执行异常: sessionId={}, requestId={}", sessionId, requestId, e);
                WebSocketMessage errorResponse = WebSocketMessage.error(
                    sessionId, 
                    requestId,
                    "执行异常: " + e.getMessage()
                );
                sendMessageToSession(sessionId, errorResponse);
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

    private void sendMessageToSession(String sessionId, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            boolean sent = wsSessionManager.sendMessage(sessionId, json);
            if (!sent) {
                logger.warn("发送消息失败，Session可能已关闭: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            logger.error("发送WebSocket消息失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = wsSessionManager.unregisterByWsSessionId(session.getId());
        if (sessionId != null) {
            sessionContextManager.destroySession(sessionId);
            logger.info("WebSocket连接关闭: wsSessionId={}, sessionId={}, status={}, nodeId={}", 
                    session.getId(), sessionId, status, properties.getNodeId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误: wsSessionId={}", session.getId(), exception);

        String sessionId = wsSessionManager.getSessionId(session.getId());
        if (sessionId != null) {
            WebSocketMessage errorResponse = WebSocketMessage.error(
                sessionId,
                "传输错误: " + exception.getMessage()
            );
            sendMessageToSession(sessionId, errorResponse);
        }
    }

    public int getActiveConnectionCount() {
        return wsSessionManager.getActiveSessionCount();
    }
}
