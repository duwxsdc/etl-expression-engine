package com.etl.engine.rest;

import com.etl.engine.callback.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MvelRestClientTest {
    
    @Autowired
    private RestClient restClient;
    
    @Autowired
    private LocalNodeInfo localNodeInfo;
    
    @Autowired
    private LocalEventManager eventManager;
    
    @BeforeEach
    void setUp() {
        MvelRestClient.init(restClient, localNodeInfo, eventManager);
    }
    
    @Test
    @DisplayName("测试本地节点信息获取")
    void testLocalNodeInfo() {
        assertNotNull(localNodeInfo.getNodeIp());
        assertTrue(localNodeInfo.getServerPort() > 0);
        assertNotNull(localNodeInfo.getNodeId());
        
        System.out.println("本地节点IP: " + localNodeInfo.getNodeIp());
        System.out.println("本地节点端口: " + localNodeInfo.getServerPort());
        System.out.println("本地节点ID: " + localNodeInfo.getNodeId());
        System.out.println("内部回调URL: " + localNodeInfo.getInternalCallbackUrl());
    }
    
    @Test
    @DisplayName("测试事件管理器基本功能")
    void testEventManager() {
        String eventId = eventManager.generateEventId();
        assertNotNull(eventId);
        assertEquals(16, eventId.length());
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 5000);
        assertNotNull(event);
        assertEquals(eventId, event.eventId());
        
        assertTrue(eventManager.hasEvent(eventId));
        
        boolean completed = eventManager.completeEvent(eventId, "test-result");
        assertTrue(completed);
        
        assertFalse(eventManager.hasEvent(eventId));
    }
    
    @Test
    @DisplayName("测试RestClient GET请求")
    void testRestClientGet() {
        try {
            String result = MvelRestClient.get("https://httpbin.org/get")
                .queryParam("test", "value")
                .execute()
                .asString();
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
            System.out.println("GET请求结果: " + result.substring(0, Math.min(200, result.length())));
        } catch (Exception e) {
            System.out.println("网络请求失败（可能是网络问题）: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("测试RestClient POST请求")
    void testRestClientPost() {
        try {
            Map<String, Object> body = Map.of("name", "test", "value", 123);
            
            String result = MvelRestClient.post("https://httpbin.org/post")
                .bodyJson(body)
                .header("X-Custom-Header", "test-value")
                .execute()
                .asString();
            
            assertNotNull(result);
            System.out.println("POST请求结果: " + result.substring(0, Math.min(200, result.length())));
        } catch (Exception e) {
            System.out.println("网络请求失败（可能是网络问题）: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("测试bindCallback自动注入")
    void testBindCallback() {
        MvelRestClientBuilder builder = MvelRestClient.post("https://httpbin.org/post")
            .bodyJson(Map.of("data", "test"))
            .bindCallback(30000);
        
        String eventId = builder.getEventId();
        assertNotNull(eventId);
        assertEquals(16, eventId.length());
        
        System.out.println("自动生成的eventId: " + eventId);
        System.out.println("当前等待事件数: " + eventManager.getPendingCount());
    }
    
    @Test
    @DisplayName("测试事件超时处理")
    void testEventTimeout() throws Exception {
        String eventId = eventManager.generateEventId();
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 1000);
        
        CompletableFuture<Object> future = event.future();
        
        Thread.sleep(1500);
        
        assertTrue(future.isDone());
        assertTrue(eventManager.isCompleted(eventId));
        
        System.out.println("事件超时测试通过");
    }
    
    @Test
    @DisplayName("测试重复回调忽略")
    void testDuplicateCallback() {
        String eventId = eventManager.generateEventId();
        eventManager.registerEvent(eventId, 5000);
        
        boolean first = eventManager.completeEvent(eventId, "result1");
        assertTrue(first);
        
        boolean second = eventManager.completeEvent(eventId, "result2");
        assertFalse(second);
        
        System.out.println("重复回调忽略测试通过");
    }
}
