package com.etl.engine.mq;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.idempotent.IdempotentController;
import com.etl.engine.model.BroadcastMessage;
import com.etl.engine.model.ExpressionResult;
import com.etl.engine.model.WebSocketMessage;
import com.etl.engine.ws.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ消息消费者
 * 接收广播消息并处理表达式执行请求
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
@RocketMQMessageListener(
    topic = "${etl.engine.broadcast-topic:mvel-broadcast}",
    consumerGroup = "etl-expression-consumer",
    messageModel = MessageModel.BROADCASTING
)
public class BroadcastMessageConsumer implements RocketMQListener<String> {
    
    private static final Logger logger = LoggerFactory.getLogger(BroadcastMessageConsumer.class);
    
    private final WebSocketSessionManager wsSessionManager;
    private final MvelSandboxEngine mvelSandboxEngine;
    private final IdempotentController idempotentController;
    private final ObjectMapper objectMapper;
    private final EngineProperties properties;
    
    public BroadcastMessageConsumer(WebSocketSessionManager wsSessionManager,
                                    MvelSandboxEngine mvelSandboxEngine,
                                    IdempotentController idempotentController,
                                    ObjectMapper objectMapper,
                                    EngineProperties properties) {
        this.wsSessionManager = wsSessionManager;
        this.mvelSandboxEngine = mvelSandboxEngine;
        this.idempotentController = idempotentController;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }
    
    @Override
    public void onMessage(String message) {
        try {
            BroadcastMessage broadcastMessage = objectMapper.readValue(message, BroadcastMessage.class);
            logger.debug("收到广播消息: requestId={}, sessionId={}, sourceNodeId={}", 
                    broadcastMessage.requestId(), 
                    broadcastMessage.sessionId(), 
                    broadcastMessage.sourceNodeId());
            
            processBroadcastMessage(broadcastMessage);
            
        } catch (Exception e) {
            logger.error("解析广播消息失败: {}", message, e);
        }
    }
    
    private void processBroadcastMessage(BroadcastMessage message) {
        if (isSelfMessage(message)) {
            logger.debug("忽略本节点发出的消息: requestId={}", message.requestId());
            return;
        }
        
        if (!idempotentController.tryProcess(message.requestId())) {
            logger.debug("重复请求，忽略: requestId={}", message.requestId());
            return;
        }
        
        if (!wsSessionManager.hasSession(message.sessionId())) {
            logger.debug("会话不在本节点: sessionId={}", message.sessionId());
            return;
        }
        
        executeAndRespond(message);
    }
    
    private boolean isSelfMessage(BroadcastMessage message) {
        return properties.getNodeId().equals(message.sourceNodeId());
    }
    
    private void executeAndRespond(BroadcastMessage message) {
        try {
            ExpressionResult result = mvelSandboxEngine.execute(
                message.expression(), 
                null,
                message.vars()
            );
            
            WebSocketMessage response = WebSocketMessage.result(
                message.sessionId(),
                message.requestId(),
                toResultMap(result)
            );
            
            String json = objectMapper.writeValueAsString(response);
            wsSessionManager.sendMessage(message.sessionId(), json);
            
            logger.info("广播消息处理完成: requestId={}, sessionId={}, success={}", 
                    message.requestId(), message.sessionId(), result.success());
            
        } catch (Exception e) {
            logger.error("处理广播消息异常: requestId={}, sessionId={}", 
                    message.requestId(), message.sessionId(), e);
            
            sendErrorResponse(message, e.getMessage());
        }
    }
    
    private void sendErrorResponse(BroadcastMessage message, String errorMsg) {
        try {
            WebSocketMessage response = WebSocketMessage.error(
                message.sessionId(),
                message.requestId(),
                "执行异常: " + errorMsg
            );
            
            String json = objectMapper.writeValueAsString(response);
            wsSessionManager.sendMessage(message.sessionId(), json);
            
        } catch (Exception e) {
            logger.error("发送错误响应失败", e);
        }
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
}
