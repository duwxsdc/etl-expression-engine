package com.etl.engine.mq;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.model.BroadcastMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ消息生产者
 * 广播表达式执行请求
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class BroadcastMessageProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(BroadcastMessageProducer.class);
    
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final EngineProperties properties;
    
    public BroadcastMessageProducer(RocketMQTemplate rocketMQTemplate, 
                                    ObjectMapper objectMapper,
                                    EngineProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }
    
    /**
     * 异步广播消息
     * @param message 广播消息
     */
    public void broadcastAsync(BroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            Message<String> mqMessage = MessageBuilder.withPayload(json).build();
            
            String destination = properties.getBroadcastTopic();
            
            rocketMQTemplate.asyncSend(destination, mqMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    logger.info("广播消息发送成功: requestId={}, msgId={}", 
                            message.requestId(), sendResult.getMsgId());
                }
                
                @Override
                public void onException(Throwable e) {
                    logger.error("广播消息发送失败: requestId={}", message.requestId(), e);
                }
            });
            
        } catch (JsonProcessingException e) {
            logger.error("序列化广播消息失败: requestId={}", message.requestId(), e);
        }
    }
    
    /**
     * 同步广播消息
     * @param message 广播消息
     * @return 是否发送成功
     */
    public boolean broadcastSync(BroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            Message<String> mqMessage = MessageBuilder.withPayload(json).build();
            
            String destination = properties.getBroadcastTopic();
            
            SendResult sendResult = rocketMQTemplate.syncSend(destination, mqMessage);
            logger.info("广播消息发送成功: requestId={}, msgId={}", 
                    message.requestId(), sendResult.getMsgId());
            return true;
            
        } catch (Exception e) {
            logger.error("广播消息发送失败: requestId={}", message.requestId(), e);
            return false;
        }
    }
}
