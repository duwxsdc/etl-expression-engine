package com.etl.engine.rest.ext.support;

import com.etl.engine.callback.LocalEventManager;
import com.etl.engine.callback.LocalNodeInfo;
import com.etl.engine.config.EngineProperties;
import com.etl.engine.rest.ext.ExtRestClient;
import com.etl.engine.rest.ext.callback.CallbackRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;

/**
 * ExtRestClient自动配置
 */
@Slf4j
@AutoConfiguration
public class ExtAutoConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${etl.engine.node.ip:}")
    private String nodeIp;

    @Bean
    @ConditionalOnMissingBean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtRestClient extRestClient(RestClient restClient) {
        log.info("创建ExtRestClient - 装饰器模式扩展RestClient");
        return ExtRestClient.of(restClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalEventManager localEventManager() {
        return new LocalEventManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalNodeInfo localNodeInfo() throws Exception {
        String ip = (nodeIp != null && !nodeIp.isEmpty()) ? nodeIp : InetAddress.getLocalHost().getHostAddress();
        log.info("LocalNodeInfo初始化 - IP: {}, Port: {}", ip, serverPort);
        return new LocalNodeInfo("node-" + ip, ip, serverPort);
    }

    @Bean
    public ExtRestClientInitializer extRestClientInitializer(
            LocalEventManager eventManager, LocalNodeInfo nodeInfo) {
        CallbackRegistry.getInstance().init(eventManager, nodeInfo);
        log.info("CallbackRegistry已初始化 - 节点: {}", nodeInfo.getNodeId());
        return new ExtRestClientInitializer();
    }

    public static class ExtRestClientInitializer {}
}
