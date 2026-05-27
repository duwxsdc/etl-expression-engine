package com.etl.engine.rest.enhanced;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(EnhancedRestClientProperties.class)
public class EnhancedRestClientAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public EnhancedRestClient enhancedRestClient(RestClient.Builder restClientBuilder,
                                                  EnhancedRestClientProperties properties) {
        RestClient restClient = restClientBuilder.build();
        EnhancedRestClient enhancedClient = EnhancedRestClient.create(restClient);
        
        ExtensionRegistry registry = enhancedClient.getExtensionRegistry();
        
        if (properties.isDefaultTokenEnabled()) {
            String staticToken = properties.getDefaultToken();
            if (staticToken != null && !staticToken.isEmpty()) {
                registry.register(DefaultTokenExtension.withBearerToken(staticToken));
            } else {
                registry.register(new DefaultTokenExtension());
            }
        }
        
        if (properties.isCallbackEnabled()) {
            registry.register(new CallbackExtension(
                properties.getCallbackTargetIp(),
                properties.getCallbackTargetPort()
            ));
        }
        
        return enhancedClient;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CallbackController callbackController() {
        return new CallbackController();
    }
}