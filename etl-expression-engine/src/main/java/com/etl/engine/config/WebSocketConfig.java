package com.etl.engine.config;

import com.etl.engine.handler.ExpressionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExpressionWebSocketHandler expressionWebSocketHandler;

    public WebSocketConfig(ExpressionWebSocketHandler expressionWebSocketHandler) {
        this.expressionWebSocketHandler = expressionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(expressionWebSocketHandler, "/ws/expression")
                .setAllowedOrigins("*");
    }
}
