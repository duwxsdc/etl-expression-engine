package com.etl.engine.rest.ext.support;

import com.etl.engine.callback.LocalEventManager;
import com.etl.engine.callback.LocalNodeInfo;
import com.etl.engine.rest.ext.ExtRestClient;
import com.etl.engine.rest.ext.callback.CallbackRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * ExtRestClient自动配置
 */
@Slf4j
@AutoConfiguration
public class ExtAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public ExtRestClient extRestClient(RestClient restClient) {
        log.info("创建ExtRestClient - 装饰器模式扩展RestClient");
        return ExtRestClient.of(restClient);
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
