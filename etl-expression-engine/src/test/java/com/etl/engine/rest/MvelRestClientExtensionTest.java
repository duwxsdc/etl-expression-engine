package com.etl.engine.rest;

import com.etl.engine.callback.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MvelRestClientExtensionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelRestClientExtensionTest.class);
    
    @Autowired
    private RestClient restClient;
    
    @Autowired
    private LocalNodeInfo localNodeInfo;
    
    @Autowired
    private LocalEventManager eventManager;
    
    @Autowired
    private RestClientProperties properties;
    
    @BeforeEach
    void setUp() {
        MvelRestClient.init(restClient, localNodeInfo, eventManager, properties);
    }
    
    @Test
    @Order(1)
    @DisplayName("扩展1: 自动Token注入 - 配置启用")
    void testAutoTokenInjection() {
        properties.setAutoTokenEnabled(true);
        properties.setAutoTokenValue("test-token-123");
        
        MvelRestClientBuilder builder = MvelRestClient.post("https://example.com/api")
            .bodyJson(Map.of("data", "test"));
        
        assertTrue(properties.shouldAddToken("POST"));
        assertTrue(properties.shouldAddToken("GET"));
        assertTrue(properties.shouldAddToken("PUT"));
        assertTrue(properties.shouldAddToken("DELETE"));
        
        String resolvedToken = properties.resolveToken();
        assertNotNull(resolvedToken);
        assertEquals("Bearer test-token-123", resolvedToken);
        
        properties.setAutoTokenEnabled(false);
        
        logger.info("✅ 自动Token注入配置测试通过");
    }
    
    @Test
    @Order(2)
    @DisplayName("扩展2: skipAutoToken - 跳过自动Token")
    void testSkipAutoToken() {
        properties.setAutoTokenEnabled(true);
        properties.setAutoTokenValue("auto-token");
        
        MvelRestClientBuilder builder = MvelRestClient.get("https://example.com/api")
            .skipAutoToken();
        
        assertNotNull(builder);
        
        properties.setAutoTokenEnabled(false);
        
        logger.info("✅ skipAutoToken测试通过");
    }
    
    @Test
    @Order(3)
    @DisplayName("扩展3: bearerAuth - 手动Bearer认证")
    void testBearerAuth() {
        MvelRestClientBuilder builder = MvelRestClient.get("https://example.com/api")
            .bearerAuth("my-token");
        
        assertNotNull(builder);
        
        logger.info("✅ bearerAuth测试通过");
    }
    
    @Test
    @Order(4)
    @DisplayName("扩展4: basicAuth - 基础认证")
    void testBasicAuth() {
        MvelRestClientBuilder builder = MvelRestClient.get("https://example.com/api")
            .basicAuth("username", "password");
        
        assertNotNull(builder);
        
        logger.info("✅ basicAuth测试通过");
    }
    
    @Test
    @Order(5)
    @DisplayName("扩展5: 请求拦截器 - 添加自定义Header")
    void testRequestInterceptor() {
        MvelRestClient.addGlobalRequestInterceptor(request -> 
            request.withHeader("X-Custom-Header", "interceptor-value")
                  .withHeader("X-Timestamp", String.valueOf(System.currentTimeMillis()))
        );
        
        assertEquals(1, MvelRestClient.getGlobalRequestInterceptors().size());
        
        MvelRestClient.clearGlobalInterceptors();
        
        assertEquals(0, MvelRestClient.getGlobalRequestInterceptors().size());
        
        logger.info("✅ 请求拦截器测试通过");
    }
    
    @Test
    @Order(6)
    @DisplayName("扩展6: 响应拦截器 - 日志记录")
    void testResponseInterceptor() {
        List<String> logCollector = new ArrayList<>();
        
        MvelRestClient.addGlobalResponseInterceptor(response -> {
            logCollector.add("Response: " + response.statusCode() + ", duration: " + response.durationMs() + "ms");
            return response;
        });
        
        assertEquals(1, MvelRestClient.getGlobalResponseInterceptors().size());
        
        MvelRestClient.clearGlobalInterceptors();
        
        logger.info("✅ 响应拦截器测试通过");
    }
    
    @Test
    @Order(7)
    @DisplayName("扩展7: 端点级别配置")
    void testEndpointConfig() {
        Map<String, RestClientProperties.EndpointConfig> endpoints = new LinkedHashMap<>();
        
        RestClientProperties.EndpointConfig config = new RestClientProperties.EndpointConfig();
        config.setBaseUrl("https://api.example.com");
        config.setTimeout(java.time.Duration.ofSeconds(60));
        config.setHeaders(Map.of("X-Api-Key", "endpoint-key"));
        config.setMaxRetries(3);
        
        endpoints.put("api.example.com", config);
        properties.setEndpoints(endpoints);
        
        RestClientProperties.EndpointConfig resolved = properties.getEndpointConfig("https://api.example.com/users");
        assertNotNull(resolved);
        assertEquals("https://api.example.com", resolved.getBaseUrl());
        assertEquals(3, resolved.getMaxRetries());
        
        properties.setEndpoints(new LinkedHashMap<>());
        
        logger.info("✅ 端点级别配置测试通过");
    }
    
    @Test
    @Order(8)
    @DisplayName("扩展8: 自定义TokenProvider")
    void testCustomTokenProvider() {
        properties.setAutoTokenEnabled(true);
        
        MvelRestClient.setTokenProvider(envName -> "dynamic-token-for-" + envName);
        
        String token = properties.resolveToken();
        assertNotNull(token);
        assertEquals("Bearer dynamic-token-for-ETL_API_TOKEN", token);
        
        properties.setTokenProvider(null);
        properties.setAutoTokenEnabled(false);
        
        logger.info("✅ 自定义TokenProvider测试通过");
    }
    
    @Test
    @Order(9)
    @DisplayName("扩展9: 默认请求头配置")
    void testDefaultHeaders() {
        Map<String, String> defaultHeaders = new LinkedHashMap<>();
        defaultHeaders.put("X-App-Name", "ETL-Engine");
        defaultHeaders.put("X-App-Version", "1.0.0");
        
        properties.setDefaultHeaders(defaultHeaders);
        
        MvelRestClientBuilder builder = MvelRestClient.get("https://example.com/api");
        
        assertNotNull(builder);
        
        properties.setDefaultHeaders(new LinkedHashMap<>());
        
        logger.info("✅ 默认请求头配置测试通过");
    }
    
    @Test
    @Order(10)
    @DisplayName("扩展10: withRetry重试配置")
    void testRetryConfig() {
        MvelRestClientBuilder builder = MvelRestClient.post("https://example.com/api")
            .bodyJson(Map.of("data", "test"))
            .withRetry(3);
        
        assertNotNull(builder);
        
        properties.setMaxRetries(2);
        properties.setRetryDelayMs(500);
        properties.setRetryBackoffFactor(1.5);
        
        assertEquals(2, properties.getMaxRetries());
        assertEquals(500, properties.getRetryDelayMs());
        assertEquals(1.5, properties.getRetryBackoffFactor());
        
        properties.setMaxRetries(0);
        properties.setRetryDelayMs(1000);
        properties.setRetryBackoffFactor(2.0);
        
        logger.info("✅ 重试配置测试通过");
    }
    
    @Test
    @Order(11)
    @DisplayName("扩展11: 多种Content-Type支持")
    void testContentTypes() {
        MvelRestClientBuilder jsonBuilder = MvelRestClient.post("https://example.com/api")
            .bodyJson(Map.of("key", "value"));
        assertNotNull(jsonBuilder);
        
        MvelRestClientBuilder formBuilder = MvelRestClient.post("https://example.com/api")
            .bodyForm(Map.of("key", "value"));
        assertNotNull(formBuilder);
        
        MvelRestClientBuilder textBuilder = MvelRestClient.post("https://example.com/api")
            .bodyText("plain text body");
        assertNotNull(textBuilder);
        
        MvelRestClientBuilder xmlBuilder = MvelRestClient.post("https://example.com/api")
            .bodyXml("<root><item>value</item></root>");
        assertNotNull(xmlBuilder);
        
        logger.info("✅ 多种Content-Type测试通过");
    }
    
    @Test
    @Order(12)
    @DisplayName("扩展12: Token来源优先级")
    void testTokenPriority() {
        properties.setAutoTokenEnabled(true);
        properties.setAutoTokenValue("config-token");
        properties.setAutoTokenFromEnv(false);
        
        String token1 = properties.resolveToken();
        assertEquals("Bearer config-token", token1);
        
        MvelRestClient.setTokenProvider(envName -> "provider-token");
        String token2 = properties.resolveToken();
        assertEquals("Bearer provider-token", token2);
        
        MvelRestClient.setTokenProvider(null);
        properties.setAutoTokenEnabled(false);
        
        logger.info("✅ Token来源优先级测试通过");
    }
    
    @Test
    @Order(13)
    @DisplayName("扩展13: 日志配置验证")
    void testLoggingConfig() {
        assertTrue(properties.isLogRequestEnabled());
        assertTrue(properties.isLogResponseEnabled());
        assertFalse(properties.isLogHeadersEnabled());
        assertEquals(1000, properties.getMaxLogBodyLength());
        
        properties.setLogHeadersEnabled(true);
        properties.setMaxLogBodyLength(500);
        
        assertTrue(properties.isLogHeadersEnabled());
        assertEquals(500, properties.getMaxLogBodyLength());
        
        properties.setLogHeadersEnabled(false);
        properties.setMaxLogBodyLength(1000);
        
        logger.info("✅ 日志配置验证通过");
    }
    
    @Test
    @Order(14)
    @DisplayName("扩展14: 链式构建完整性")
    void testFullChainBuilder() {
        properties.setAutoTokenEnabled(true);
        properties.setAutoTokenValue("chain-token");
        
        MvelRestClientBuilder builder = MvelRestClient.post("https://api.example.com/{version}/users")
            .pathVariable("version", "v1")
            .queryParam("page", 1)
            .queryParam("size", 20)
            .header("X-Request-Id", "req-chain-001")
            .accept(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyJson(Map.of("name", "test", "value", 42))
            .timeoutMs(5000)
            .bindCallback(60000);
        
        String eventId = builder.getEventId();
        assertNotNull(eventId);
        assertEquals("https://api.example.com/{version}/users", builder.getUrl());
        assertEquals("POST", builder.getMethod());
        
        eventManager.completeEvent(eventId, "chain-test-result");
        
        properties.setAutoTokenEnabled(false);
        
        logger.info("✅ 链式构建完整性测试通过");
    }
    
    @Test
    @Order(15)
    @DisplayName("扩展15: 拦截器链执行顺序")
    void testInterceptorChainOrder() {
        List<String> executionOrder = new ArrayList<>();
        
        MvelRestClient.addGlobalRequestInterceptor(request -> {
            executionOrder.add("interceptor-1");
            return request;
        });
        
        MvelRestClient.addGlobalRequestInterceptor(request -> {
            executionOrder.add("interceptor-2");
            return request;
        });
        
        var interceptors = MvelRestClient.getGlobalRequestInterceptors();
        assertEquals(2, interceptors.size());
        
        RequestInterceptor.InterceptedRequest testRequest = RequestInterceptor.InterceptedRequest.of(
            "https://example.com", "GET", Map.of(), null
        );
        
        for (RequestInterceptor interceptor : interceptors) {
            interceptor.intercept(testRequest);
        }
        
        assertEquals("interceptor-1", executionOrder.get(0));
        assertEquals("interceptor-2", executionOrder.get(1));
        
        MvelRestClient.clearGlobalInterceptors();
        
        logger.info("✅ 拦截器链执行顺序测试通过");
    }
}
