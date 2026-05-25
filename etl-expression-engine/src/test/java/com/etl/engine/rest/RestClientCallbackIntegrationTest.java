package com.etl.engine.rest;

import com.etl.engine.callback.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RestClientCallbackIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(RestClientCallbackIntegrationTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
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
    @Order(1)
    @DisplayName("场景1: 正常流程 - 请求→阻塞→回调→唤醒→成功")
    void testScenario1_NormalFlow() throws Exception {
        logger.info("=== 场景1: 正常流程测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                
                String callbackUrl = localNodeInfo.getInternalCallbackUrl();
                
                Map<String, Object> callbackData = Map.of(
                    "eventId", eventId,
                    "payload", Map.of("result", "success", "data", "test-data"),
                    "status", "SUCCESS",
                    "timestamp", Instant.now()
                );
                
                logger.info("模拟回调: eventId={}", eventId);
                
                restClient.post()
                    .uri(callbackUrl)
                    .header("Content-Type", "application/json")
                    .body(callbackData)
                    .retrieve()
                    .body(String.class);
                
            } catch (Exception e) {
                logger.error("回调失败: {}", e.getMessage());
            }
        });
        
        Object result = event.future().get(15, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("SUCCESS", resultMap.get("status"));
        
        logger.info("=== 场景1: 正常流程测试通过 ===");
    }
    
    @Test
    @Order(2)
    @DisplayName("场景2: 超时场景 - 第三方不回调→超时异常")
    void testScenario2_Timeout() throws Exception {
        logger.info("=== 场景2: 超时场景测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 2000);
        
        assertThrows(TimeoutException.class, () -> {
            event.future().get(5, TimeUnit.SECONDS);
        });
        
        assertTrue(eventManager.isCompleted(eventId));
        assertFalse(eventManager.hasEvent(eventId));
        
        logger.info("=== 场景2: 超时场景测试通过 ===");
    }
    
    @Test
    @Order(3)
    @DisplayName("场景3: 重复回调 - 第三方多次回调→只处理一次")
    void testScenario3_DuplicateCallback() throws Exception {
        logger.info("=== 场景3: 重复回调测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        String callbackUrl = localNodeInfo.getInternalCallbackUrl();
        
        Map<String, Object> callbackData = Map.of(
            "eventId", eventId,
            "payload", Map.of("result", "first"),
            "status", "SUCCESS"
        );
        
        String response1 = restClient.post()
            .uri(callbackUrl)
            .header("Content-Type", "application/json")
            .body(callbackData)
            .retrieve()
            .body(String.class);
        
        logger.info("第一次回调响应: {}", response1);
        
        Thread.sleep(100);
        
        String response2 = restClient.post()
            .uri(callbackUrl)
            .header("Content-Type", "application/json")
            .body(callbackData)
            .retrieve()
            .body(String.class);
        
        logger.info("第二次回调响应: {}", response2);
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("first", ((Map<?, ?>) resultMap.get("payload")).get("result"));
        
        logger.info("=== 场景3: 重复回调测试通过 ===");
    }
    
    @Test
    @Order(4)
    @DisplayName("场景4: 回调乱序 - 第三方回调慢→依然正常唤醒")
    void testScenario4_DelayedCallback() throws Exception {
        logger.info("=== 场景4: 回调乱序测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
                
                String callbackUrl = localNodeInfo.getInternalCallbackUrl();
                Map<String, Object> callbackData = Map.of(
                    "eventId", eventId,
                    "payload", Map.of("result", "delayed-success"),
                    "status", "SUCCESS"
                );
                
                restClient.post()
                    .uri(callbackUrl)
                    .header("Content-Type", "application/json")
                    .body(callbackData)
                    .retrieve()
                    .body(String.class);
                
            } catch (Exception e) {
                logger.error("延迟回调失败: {}", e.getMessage());
            }
        });
        
        Object result = event.future().get(15, TimeUnit.SECONDS);
        
        assertNotNull(result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("delayed-success", ((Map<?, ?>) resultMap.get("payload")).get("result"));
        
        logger.info("=== 场景4: 回调乱序测试通过 ===");
    }
    
    @Test
    @Order(5)
    @DisplayName("场景5: 快速回调 - 第三方立刻回调→正常处理")
    void testScenario5_ImmediateCallback() throws Exception {
        logger.info("=== 场景5: 快速回调测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        String callbackUrl = localNodeInfo.getInternalCallbackUrl();
        Map<String, Object> callbackData = Map.of(
            "eventId", eventId,
            "payload", Map.of("result", "immediate"),
            "status", "SUCCESS"
        );
        
        restClient.post()
            .uri(callbackUrl)
            .header("Content-Type", "application/json")
            .body(callbackData)
            .retrieve()
            .body(String.class);
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("immediate", ((Map<?, ?>) resultMap.get("payload")).get("result"));
        
        logger.info("=== 场景5: 快速回调测试通过 ===");
    }
    
    @Test
    @Order(6)
    @DisplayName("场景6: 并发请求 - 多个并发请求同时处理")
    void testScenario6_ConcurrentRequests() throws Exception {
        logger.info("=== 场景6: 并发请求测试开始 ===");
        
        int concurrentCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(concurrentCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    String eventId = eventManager.generateEventId();
                    LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
                    
                    Thread.sleep(100);
                    
                    String callbackUrl = localNodeInfo.getInternalCallbackUrl();
                    Map<String, Object> callbackData = Map.of(
                        "eventId", eventId,
                        "payload", Map.of("index", index),
                        "status", "SUCCESS"
                    );
                    
                    restClient.post()
                        .uri(callbackUrl)
                        .header("Content-Type", "application/json")
                        .body(callbackData)
                        .retrieve()
                        .body(String.class);
                    
                    Object result = event.future().get(5, TimeUnit.SECONDS);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    logger.error("并发测试失败: {}", e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
        
        assertTrue(completed);
        assertEquals(concurrentCount, successCount.get());
        
        executor.shutdown();
        
        logger.info("=== 场景6: 并发请求测试通过 ===");
    }
    
    @Test
    @Order(7)
    @DisplayName("场景7: 公共回调接口 - 转发到目标节点")
    void testScenario7_PublicCallbackForward() throws Exception {
        logger.info("=== 场景7: 公共回调接口测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        String publicCallbackUrl = localNodeInfo.getPublicCallbackUrl();
        
        Map<String, Object> callbackData = Map.of(
            "eventId", eventId,
            "targetIp", localNodeInfo.getNodeIp(),
            "targetPort", localNodeInfo.getServerPort(),
            "payload", Map.of("result", "forwarded"),
            "status", "SUCCESS"
        );
        
        String response = restClient.post()
            .uri(publicCallbackUrl)
            .header("Content-Type", "application/json")
            .body(callbackData)
            .retrieve()
            .body(String.class);
        
        logger.info("公共回调响应: {}", response);
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        
        logger.info("=== 场景7: 公共回调接口测试通过 ===");
    }
    
    @Test
    @Order(8)
    @DisplayName("场景8: 异常回调 - 回调携带错误状态")
    void testScenario8_ErrorCallback() throws Exception {
        logger.info("=== 场景8: 异常回调测试开始 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        String callbackUrl = localNodeInfo.getInternalCallbackUrl();
        Map<String, Object> callbackData = Map.of(
            "eventId", eventId,
            "status", "ERROR",
            "message", "处理失败"
        );
        
        restClient.post()
            .uri(callbackUrl)
            .header("Content-Type", "application/json")
            .body(callbackData)
            .retrieve()
            .body(String.class);
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("ERROR", resultMap.get("status"));
        
        logger.info("=== 场景8: 异常回调测试通过 ===");
    }
    
    @Test
    @Order(9)
    @DisplayName("场景9: RestClient链式构建")
    void testScenario9_RestClientBuilder() {
        logger.info("=== 场景9: RestClient链式构建测试开始 ===");
        
        MvelRestClientBuilder builder = MvelRestClient.post("https://example.com/api")
            .header("Authorization", "Bearer token")
            .header("X-Request-Id", "req-123")
            .bodyJson(Map.of("key", "value"))
            .queryParam("page", 1)
            .pathVariable("id", 100)
            .bindCallback(30000);
        
        String eventId = builder.getEventId();
        assertNotNull(eventId);
        assertEquals(16, eventId.length());
        
        logger.info("=== 场景9: RestClient链式构建测试通过 ===");
    }
    
    @Test
    @Order(10)
    @DisplayName("场景10: 事件管理器状态查询")
    void testScenario10_EventManagerStatus() {
        logger.info("=== 场景10: 事件管理器状态查询测试开始 ===");
        
        int initialCount = eventManager.getPendingCount();
        
        String eventId = eventManager.registerEvent(eventManager.generateEventId(), 10000).eventId();
        
        assertTrue(eventManager.hasEvent(eventId));
        assertFalse(eventManager.isCompleted(eventId));
        assertEquals(initialCount + 1, eventManager.getPendingCount());
        
        eventManager.completeEvent(eventId, "result");
        
        assertFalse(eventManager.hasEvent(eventId));
        assertTrue(eventManager.isCompleted(eventId));
        
        logger.info("=== 场景10: 事件管理器状态查询测试通过 ===");
    }
}
