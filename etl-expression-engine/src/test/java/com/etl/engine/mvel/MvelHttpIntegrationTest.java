package com.etl.engine.mvel;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.sql.SqlExecuteEngine;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MvelHttpIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelHttpIntegrationTest.class);
    
    private static MvelExpressionEngine engine;
    private static EtlContextManager contextManager;
    
    @BeforeAll
    static void setup() {
        EngineProperties properties = new EngineProperties();
        properties.setMaxExpressionLength(10000);
        properties.setExpressionTimeout(30000);
        
        MvelSecuritySandbox sandbox = new MvelSecuritySandbox();
        SqlExecuteEngine sqlEngine = mock(SqlExecuteEngine.class);
        
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        
        contextManager = new EtlContextManager(redisTemplate);
        
        engine = new MvelExpressionEngine(properties, sandbox, sqlEngine);
        
        logger.info("MVEL HTTP集成测试环境初始化完成");
    }
    
    @Test
    @Order(1)
    @DisplayName("MVEL中使用httpRequest函数测试")
    void testHttpRequestInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .get()
                .statusCode()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertNotNull(result.finalResult());
        assertEquals(200, ((Number) result.finalResult()).intValue());
        
        logger.info("MVEL httpRequest测试成功: {}", result.finalResult());
    }
    
    @Test
    @Order(2)
    @DisplayName("MVEL中使用http函数别名测试")
    void testHttpAliasInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            http("https://httpbin.org/get")
                .header("X-Test", "value")
                .timeout(10000)
                .get()
                .asString()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertNotNull(result.finalResult());
        String body = (String) result.finalResult();
        assertTrue(body.length() > 0);
        
        logger.info("MVEL http别名测试成功: body长度={}", body.length());
    }
    
    @Test
    @Order(3)
    @DisplayName("MVEL中POST JSON请求测试")
    void testPostJsonInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            import java.util.Map;
            httpRequest("https://httpbin.org/post")
                .bodyJson(Map.of("name", "test", "value", 123))
                .timeout(10000)
                .post()
                .asMap()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertNotNull(result.finalResult());
        
        logger.info("MVEL POST JSON测试成功");
    }
    
    @Test
    @Order(4)
    @DisplayName("MVEL中结果解析测试")
    void testResponseParsingInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            response = httpRequest("https://httpbin.org/json")
                .accept("application/json")
                .timeout(10000)
                .get();
            response.asJson()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertNotNull(result.finalResult());
        
        logger.info("MVEL响应解析测试成功");
    }
    
    @Test
    @Order(5)
    @DisplayName("MVEL中变量赋值与使用测试")
    void testVariableAssignmentInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            status = httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .get()
                .statusCode();
            body = httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .get()
                .asString();
            status + " - " + body.length()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertNotNull(result.finalResult());
        assertTrue(result.finalResult().toString().startsWith("200"));
        
        Map<String, Object> variables = context.getAllVariables();
        assertTrue(variables.containsKey("status"));
        assertTrue(variables.containsKey("body"));
        
        logger.info("MVEL变量赋值测试成功: {}", result.finalResult());
    }
    
    @Test
    @Order(6)
    @DisplayName("MVEL中异步请求测试")
    void testAsyncRequestInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            asyncReq = httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .asyncGet();
            asyncReq.get().statusCode()
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertEquals(200, ((Number) result.finalResult()).intValue());
        
        logger.info("MVEL异步请求测试成功");
    }
    
    @Test
    @Order(7)
    @DisplayName("MVEL中链式配置测试")
    void testChainedConfigInMvel() {
        EtlContext context = contextManager.createSession();
        
        String expression = """
            httpRequest("https://httpbin.org/headers")
                .header("X-Auth-Token", "secret-token")
                .header("X-Request-Id", "req-12345")
                .timeout(10000)
                .get()
                .asJson()
                .get("headers")
                .get("X-Auth-Token")
            """;
        
        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success());
        assertEquals("secret-token", result.finalResult().toString());
        
        logger.info("MVEL链式配置测试成功: {}", result.finalResult());
    }
}
