package com.etl.engine.http;

import com.etl.engine.mvel.HttpFunction;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpFunctionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpFunctionTest.class);
    
    @BeforeAll
    static void setup() {
        HttpFunction.init();
    }
    
    @AfterAll
    static void teardown() {
        HttpFunction.shutdown();
    }
    
    @Test
    @Order(1)
    @DisplayName("基础GET请求测试")
    void testBasicGetRequest() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        assertTrue(response.isSuccess());
        assertNotNull(response.asString());
        assertTrue(response.asString().length() > 0);
        
        logger.info("GET请求成功: statusCode={}, bodyLength={}", 
                response.statusCode(), response.asString().length());
    }
    
    @Test
    @Order(2)
    @DisplayName("带查询参数的GET请求测试")
    void testGetWithQueryParams() {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("name", "test");
        queryParams.put("value", 123);
        
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/get")
                .queryVariables(queryParams)
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        String body = response.asString();
        assertTrue(body.contains("test"));
        assertTrue(body.contains("123"));
        
        logger.info("带查询参数的GET请求成功");
    }
    
    @Test
    @Order(3)
    @DisplayName("带请求头的GET请求测试")
    void testGetWithHeaders() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/headers")
                .header("X-Custom-Header", "TestValue")
                .header("Accept", "application/json")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        String body = response.asString();
        assertTrue(body.contains("X-Custom-Header"));
        
        logger.info("带请求头的GET请求成功");
    }
    
    @Test
    @Order(4)
    @DisplayName("POST JSON请求测试")
    void testPostJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "test");
        data.put("age", 25);
        
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/post")
                .bodyJson(data)
                .timeout(10000)
                .post();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        Map<String, Object> result = response.asMap();
        assertNotNull(result);
        assertTrue(result.containsKey("json"));
        
        logger.info("POST JSON请求成功: {}", result.get("json"));
    }
    
    @Test
    @Order(5)
    @DisplayName("Bearer Token认证测试")
    void testBearerAuth() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/bearer")
                .bearerAuth("test-token-123")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("Bearer认证请求成功");
    }
    
    @Test
    @Order(6)
    @DisplayName("Basic认证测试")
    void testBasicAuth() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/basic-auth/user/pass")
                .basicAuth("user", "pass")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("Basic认证请求成功");
    }
    
    @Test
    @Order(7)
    @DisplayName("PUT请求测试")
    void testPutRequest() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/put")
                .bodyJson(Map.of("key", "value"))
                .timeout(10000)
                .put();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("PUT请求成功");
    }
    
    @Test
    @Order(8)
    @DisplayName("DELETE请求测试")
    void testDeleteRequest() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/delete")
                .timeout(10000)
                .delete();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("DELETE请求成功");
    }
    
    @Test
    @Order(9)
    @DisplayName("异步GET请求测试")
    void testAsyncGetRequest() throws Exception {
        CompletableFuture<HttpResponse> future = HttpFunction.httpRequest("https://httpbin.org/get")
                .timeout(10000)
                .asyncGet()
                .future();
        
        HttpResponse response = future.get(15, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("异步GET请求成功");
    }
    
    @Test
    @Order(10)
    @DisplayName("异步POST请求回调测试")
    void testAsyncPostWithCallback() throws Exception {
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        
        HttpFunction.httpRequest("https://httpbin.org/post")
                .bodyJson(Map.of("async", true))
                .timeout(10000)
                .asyncPost()
                .thenAccept(response -> {
                    logger.info("异步回调执行: statusCode={}", response.statusCode());
                    resultFuture.complete(response.asString());
                });
        
        String result = resultFuture.get(15, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.length() > 0);
        
        logger.info("异步POST回调测试成功");
    }
    
    @Test
    @Order(11)
    @DisplayName("JSON解析测试")
    void testJsonParsing() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/json")
                .accept("application/json")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        var jsonNode = response.asJson();
        assertNotNull(jsonNode);
        
        logger.info("JSON解析成功: {}", jsonNode);
    }
    
    @Test
    @Order(12)
    @DisplayName("响应头解析测试")
    void testResponseHeaders() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/response-headers")
                .queryVariable("X-Custom", "TestValue")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        Map<String, String> headers = response.headers();
        assertNotNull(headers);
        assertFalse(headers.isEmpty());
        
        logger.info("响应头解析成功: {} 个头信息", headers.size());
    }
    
    @Test
    @Order(13)
    @DisplayName("链式配置测试")
    void testChainedConfiguration() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/get")
                .configure(builder -> {
                    builder.header("X-Config-1", "value1");
                    builder.header("X-Config-2", "value2");
                    builder.queryVariable("param1", "test");
                })
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(200, response.statusCode());
        
        logger.info("链式配置测试成功");
    }
    
    @Test
    @Order(14)
    @DisplayName("超时设置测试")
    void testTimeoutSetting() {
        long startTime = System.currentTimeMillis();
        
        assertThrows(HttpTimeoutException.class, () -> {
            HttpFunction.httpRequest("https://httpbin.org/delay/5")
                    .timeout(1000)
                    .get();
        });
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 3000, "超时应该在指定时间内触发");
        
        logger.info("超时设置测试成功, 实际耗时: {}ms", duration);
    }
    
    @Test
    @Order(15)
    @DisplayName("错误状态码处理测试")
    void testErrorStatusCode() {
        HttpResponse response = HttpFunction.httpRequest("https://httpbin.org/status/404")
                .timeout(10000)
                .get();
        
        assertNotNull(response);
        assertEquals(404, response.statusCode());
        assertTrue(response.isClientError());
        assertFalse(response.isSuccess());
        
        logger.info("错误状态码处理成功: {}", response.statusCode());
    }
}
