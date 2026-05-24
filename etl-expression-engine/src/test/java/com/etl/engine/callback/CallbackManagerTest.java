package com.etl.engine.callback;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@DisplayName("CallbackManager单元测试")
class CallbackManagerTest {
    
    private CallbackManager callbackManager;
    
    @BeforeEach
    void setUp() {
        callbackManager = new CallbackManager();
    }
    
    @AfterEach
    void tearDown() {
        callbackManager.shutdown();
    }
    
    @Test
    @DisplayName("注册回调请求")
    void testRegisterCallback() {
        CallbackRequest request = CallbackRequest.of(
                "https://httpbin.org/post",
                "TEST_EVENT",
                Map.of("key", "value")
        );
        
        String callbackId = callbackManager.registerCallback(request);
        
        assertNotNull(callbackId);
        assertTrue(callbackId.startsWith("cb-"));
    }
    
    @Test
    @DisplayName("简化回调注册")
    void testSimpleCallbackRegistration() {
        String callbackId = callbackManager.registerCallback(
                "https://httpbin.org/post",
                "DATA_PROCESSED",
                Map.of("result", "success")
        );
        
        assertNotNull(callbackId);
    }
    
    @Test
    @DisplayName("获取回调状态")
    void testGetCallbackStatus() throws InterruptedException {
        String callbackId = callbackManager.registerCallback(
                "https://httpbin.org/post",
                "TEST_EVENT",
                Map.of("test", "data")
        );
        
        TimeUnit.SECONDS.sleep(2);
        
        var status = callbackManager.getCallbackStatus(callbackId);
        assertTrue(status.isPresent());
        assertEquals(callbackId, status.get().callbackId());
    }
    
    @Test
    @DisplayName("获取回调日志")
    void testGetCallbackLogs() throws InterruptedException {
        String callbackId = callbackManager.registerCallback(
                "https://httpbin.org/post",
                "TEST_EVENT",
                Map.of("test", "logs")
        );
        
        TimeUnit.SECONDS.sleep(2);
        
        var logs = callbackManager.getCallbackLogs(callbackId);
        assertNotNull(logs);
    }
    
    @Test
    @DisplayName("获取最近日志")
    void testGetRecentLogs() {
        var logs = callbackManager.getRecentLogs(10);
        assertNotNull(logs);
        assertTrue(logs.size() <= 10);
    }
    
    @Test
    @DisplayName("获取统计信息")
    void testGetStatistics() {
        callbackManager.registerCallback(
                "https://httpbin.org/post",
                "STAT_TEST",
                Map.of("stat", "test")
        );
        
        var stats = callbackManager.getStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalCallbacks"));
        assertTrue(stats.containsKey("totalLogs"));
        assertTrue(stats.containsKey("stateDistribution"));
    }
    
    @Test
    @DisplayName("CallbackRequest构建")
    void testCallbackRequestBuilder() {
        CallbackRequest request = new CallbackRequest();
        request.setCallbackUrl("https://example.com/callback");
        request.setTriggerEvent("ORDER_CREATED");
        request.setMethod("POST");
        request.setHeaders(Map.of("Authorization", "Bearer token"));
        request.setData(Map.of("orderId", "123"));
        request.setMaxRetries(5);
        request.setRetryIntervalMs(2000L);
        
        assertEquals("https://example.com/callback", request.getCallbackUrl());
        assertEquals("ORDER_CREATED", request.getTriggerEvent());
        assertEquals("POST", request.getMethod());
        assertEquals(5, request.getMaxRetries());
        assertEquals(2000L, request.getRetryIntervalMs());
    }
    
    @Test
    @DisplayName("CallbackPayload记录")
    void testCallbackPayload() {
        CallbackPayload payload = new CallbackPayload(
                "cb-123",
                "TEST_EVENT",
                "2024-01-01T00:00:00Z",
                "SUCCESS",
                Map.of("source", "test"),
                Map.of("result", "ok")
        );
        
        assertEquals("cb-123", payload.callbackId());
        assertEquals("TEST_EVENT", payload.event());
        assertEquals("SUCCESS", payload.status());
    }
    
    @Test
    @DisplayName("CallbackStatus记录")
    void testCallbackStatus() {
        CallbackStatus status = new CallbackStatus(
                "cb-456",
                "https://example.com/callback",
                CallbackState.COMPLETED,
                1234567890L,
                0,
                200,
                null
        );
        
        assertEquals("cb-456", status.callbackId());
        assertEquals(CallbackState.COMPLETED, status.state());
        assertEquals(200, status.statusCode());
    }
    
    @Test
    @DisplayName("CallbackState枚举")
    void testCallbackState() {
        assertEquals(6, CallbackState.values().length);
        assertNotNull(CallbackState.PENDING);
        assertNotNull(CallbackState.PROCESSING);
        assertNotNull(CallbackState.COMPLETED);
        assertNotNull(CallbackState.RETRYING);
        assertNotNull(CallbackState.FAILED);
        assertNotNull(CallbackState.TIMEOUT);
    }
    
    @Test
    @DisplayName("CallbackResult记录")
    void testCallbackResult() {
        CallbackResult success = new CallbackResult(true, 200, "OK", null);
        assertTrue(success.success());
        assertEquals(200, success.statusCode());
        assertNull(success.errorMessage());
        
        CallbackResult failure = new CallbackResult(false, 500, null, "Server error");
        assertFalse(failure.success());
        assertEquals("Server error", failure.errorMessage());
    }
    
    @Test
    @DisplayName("CallbackLog记录")
    void testCallbackLog() {
        CallbackLog log = new CallbackLog(
                "log-1",
                "cb-789",
                "https://example.com/callback",
                "TEST_EVENT",
                "{\"data\":\"test\"}",
                200,
                "OK",
                "SUCCESS",
                null,
                0,
                System.currentTimeMillis() / 1000
        );
        
        assertEquals("log-1", log.logId());
        assertEquals("cb-789", log.callbackId());
        assertEquals("SUCCESS", log.status());
    }
}
