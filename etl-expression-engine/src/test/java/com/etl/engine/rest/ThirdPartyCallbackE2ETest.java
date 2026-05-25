package com.etl.engine.rest;

import com.etl.engine.callback.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 第三方回调端到端测试
 * 
 * <p>模拟完整的异步请求和集群转发流程，验证：</p>
 * <ul>
 *   <li>MVEL通过RestClient发起异步请求</li>
 *   <li>自动注入本机IP、端口、eventId</li>
 *   <li>第三方服务回调到公共接口</li>
 *   <li>集群内HTTP转发到原始发起节点</li>
 *   <li>事件唤醒并返回结果</li>
 * </ul>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-25
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThirdPartyCallbackE2ETest {
    
    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyCallbackE2ETest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private RestClient restClient;
    
    @Autowired
    private LocalNodeInfo localNodeInfo;
    
    @Autowired
    private LocalEventManager eventManager;
    
    @Autowired
    private RestClientProperties properties;
    
    private MockMvc mockMvc;
    
    @BeforeAll
    void setupAll() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        MvelRestClient.init(restClient, localNodeInfo, eventManager, properties);
        logger.info("=== 第三方回调端到端测试启动 ===");
        logger.info("本地节点: {}:{}", localNodeInfo.getNodeIp(), localNodeInfo.getServerPort());
    }
    
    /**
     * 场景1: 完整的异步请求→回调→唤醒流程
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>通过RestClient发起异步请求并绑定回调</li>
     *   <li>模拟第三方服务处理延迟</li>
     *   <li>第三方服务回调到公共接口</li>
     *   <li>验证事件被正确唤醒</li>
     *   <li>验证返回结果正确</li>
     * </ol>
     */
    @Test
    @Order(1)
    @DisplayName("场景1: 完整异步请求→回调→唤醒流程")
    void testScenario1_FullAsyncCallbackFlow() throws Exception {
        logger.info("=== 场景1: 完整异步请求→回调→唤醒流程 ===");
        
        String eventId = eventManager.generateEventId();
        long startTime = System.currentTimeMillis();
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 30000);
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                
                Map<String, Object> callbackData = new LinkedHashMap<>();
                callbackData.put("eventId", eventId);
                callbackData.put("targetIp", localNodeInfo.getNodeIp());
                callbackData.put("targetPort", localNodeInfo.getServerPort());
                callbackData.put("payload", Map.of(
                    "status", "SUCCESS",
                    "data", Map.of("userId", 123, "userName", "张三"),
                    "processedAt", Instant.now().toString()
                ));
                callbackData.put("status", "SUCCESS");
                callbackData.put("message", "处理完成");
                callbackData.put("timestamp", Instant.now().toString());
                
                logger.info("模拟第三方回调: eventId={}, url={}", eventId, localNodeInfo.getPublicCallbackUrl());
                
                MvcResult result = mockMvc.perform(post("/api/public/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
                    .andExpect(status().isOk())
                    .andReturn();
                
                logger.info("回调响应: {}", result.getResponse().getContentAsString());
                
            } catch (Exception e) {
                logger.error("模拟回调失败: {}", e.getMessage(), e);
                eventManager.completeEventExceptionally(eventId, e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
        
        Object result = event.future().get(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(result, "回调结果不应为空");
        assertTrue(result instanceof Map, "结果应为Map类型");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("SUCCESS", resultMap.get("status"), "状态应为SUCCESS");
        assertNotNull(resultMap.get("payload"), "payload不应为空");
        
        logger.info("场景1验证通过 - 耗时: {}ms, 结果: {}", duration, resultMap);
    }
    
    /**
     * 场景2: 公共接口收到回调后转发到本机
     */
    @Test
    @Order(2)
    @DisplayName("场景2: 公共接口转发到本机")
    void testScenario2_PublicCallbackToLocalNode() throws Exception {
        logger.info("=== 场景2: 公共接口转发到本机 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        Map<String, Object> callbackData = new LinkedHashMap<>();
        callbackData.put("eventId", eventId);
        callbackData.put("targetIp", localNodeInfo.getNodeIp());
        callbackData.put("targetPort", localNodeInfo.getServerPort());
        callbackData.put("payload", Map.of("result", "forwarded"));
        callbackData.put("status", "SUCCESS");
        
        MvcResult callbackResult = mockMvc.perform(post("/api/public/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
            .andExpect(status().isOk())
            .andReturn();
        
        String responseBody = callbackResult.getResponse().getContentAsString();
        logger.info("公共接口响应: {}", responseBody);
        assertTrue(responseBody.contains("\"success\":true") || responseBody.contains("\"processed\":true"));
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        
        logger.info("场景2验证通过");
    }
    
    /**
     * 场景3: 内部接口直接唤醒事件
     */
    @Test
    @Order(3)
    @DisplayName("场景3: 内部接口直接唤醒")
    void testScenario3_InternalCallbackDirect() throws Exception {
        logger.info("=== 场景3: 内部接口直接唤醒 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        Map<String, Object> callbackData = new LinkedHashMap<>();
        callbackData.put("eventId", eventId);
        callbackData.put("payload", Map.of("data", "internal-direct"));
        callbackData.put("status", "SUCCESS");
        
        mockMvc.perform(post("/api/internal/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
            .andExpect(status().isOk());
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("internal-direct", ((Map<?, ?>) resultMap.get("payload")).get("data"));
        
        logger.info("场景3验证通过");
    }
    
    /**
     * 场景4: 模拟第三方服务延迟回调
     */
    @Test
    @Order(4)
    @DisplayName("场景4: 第三方延迟5秒后回调")
    void testScenario4_DelayedCallback() throws Exception {
        logger.info("=== 场景4: 第三方延迟5秒后回调 ===");
        
        String eventId = eventManager.generateEventId();
        long startTime = System.currentTimeMillis();
        
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 30000);
        
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
        );
        
        scheduler.schedule(() -> {
            try {
                Map<String, Object> callbackData = new LinkedHashMap<>();
                callbackData.put("eventId", eventId);
                callbackData.put("targetIp", localNodeInfo.getNodeIp());
                callbackData.put("targetPort", localNodeInfo.getServerPort());
                callbackData.put("payload", Map.of("delayed", true, "delayMs", 5000));
                callbackData.put("status", "SUCCESS");
                
                mockMvc.perform(post("/api/public/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
                    .andExpect(status().isOk());
                
            } catch (Exception e) {
                logger.error("延迟回调失败: {}", e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
        
        Object result = event.future().get(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration >= 5000, "应该等待至少5秒");
        assertNotNull(result);
        
        scheduler.shutdown();
        logger.info("场景4验证通过 - 实际耗时: {}ms", duration);
    }
    
    /**
     * 场景5: 并发多个异步请求同时处理
     */
    @Test
    @Order(5)
    @DisplayName("场景5: 并发10个异步请求")
    void testScenario5_ConcurrentAsyncRequests() throws Exception {
        logger.info("=== 场景5: 并发10个异步请求 ===");
        
        int requestCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(requestCount);
        List<String> eventIds = new CopyOnWriteArrayList<>();
        Map<String, Object> results = new ConcurrentHashMap<>();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    String eventId = eventManager.generateEventId();
                    eventIds.add(eventId);
                    
                    LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 30000);
                    
                    Thread.sleep(100 + index * 50);
                    
                    Map<String, Object> callbackData = new LinkedHashMap<>();
                    callbackData.put("eventId", eventId);
                    callbackData.put("targetIp", localNodeInfo.getNodeIp());
                    callbackData.put("targetPort", localNodeInfo.getServerPort());
                    callbackData.put("payload", Map.of("index", index, "processed", true));
                    callbackData.put("status", "SUCCESS");
                    
                    mockMvc.perform(post("/api/public/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
                        .andExpect(status().isOk());
                    
                    Object result = event.future().get(10, TimeUnit.SECONDS);
                    results.put(eventId, result);
                    
                } catch (Exception e) {
                    logger.error("并发请求失败: index={}, error={}", index, e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean allCompleted = completeLatch.await(60, TimeUnit.SECONDS);
        
        assertTrue(allCompleted, "所有请求应在60秒内完成");
        assertEquals(requestCount, results.size(), "应收到所有结果");
        
        executor.shutdown();
        logger.info("场景5验证通过 - 成功处理: {}/{} 个请求", results.size(), requestCount);
    }
    
    /**
     * 场景6: 回调携带错误状态
     */
    @Test
    @Order(6)
    @DisplayName("场景6: 回调携带错误状态")
    void testScenario6_ErrorCallback() throws Exception {
        logger.info("=== 场景6: 回调携带错误状态 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        Map<String, Object> callbackData = new LinkedHashMap<>();
        callbackData.put("eventId", eventId);
        callbackData.put("targetIp", localNodeInfo.getNodeIp());
        callbackData.put("targetPort", localNodeInfo.getServerPort());
        callbackData.put("status", "ERROR");
        callbackData.put("message", "处理失败：数据格式错误");
        callbackData.put("errorCode", "INVALID_DATA");
        
        mockMvc.perform(post("/api/public/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(callbackData)))
            .andExpect(status().isOk());
        
        Object result = event.future().get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("ERROR", resultMap.get("status"));
        assertEquals("处理失败：数据格式错误", resultMap.get("message"));
        
        logger.info("场景6验证通过");
    }
    
    /**
     * 场景7: 重复回调被忽略
     */
    @Test
    @Order(7)
    @DisplayName("场景7: 重复回调被忽略")
    void testScenario7_DuplicateCallbackIgnored() throws Exception {
        logger.info("=== 场景7: 重复回调被忽略 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 10000);
        
        Map<String, Object> callbackData1 = new LinkedHashMap<>();
        callbackData1.put("eventId", eventId);
        callbackData1.put("targetIp", localNodeInfo.getNodeIp());
        callbackData1.put("targetPort", localNodeInfo.getServerPort());
        callbackData1.put("payload", Map.of("attempt", 1));
        callbackData1.put("status", "SUCCESS");
        
        mockMvc.perform(post("/api/public/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(callbackData1)))
            .andExpect(status().isOk());
        
        Object result1 = event.future().get(5, TimeUnit.SECONDS);
        assertNotNull(result1);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result1;
        assertEquals(1, ((Map<?, ?>) resultMap.get("payload")).get("attempt"));
        
        Map<String, Object> callbackData2 = new LinkedHashMap<>();
        callbackData2.put("eventId", eventId);
        callbackData2.put("targetIp", localNodeInfo.getNodeIp());
        callbackData2.put("targetPort", localNodeInfo.getServerPort());
        callbackData2.put("payload", Map.of("attempt", 2));
        callbackData2.put("status", "SUCCESS");
        
        mockMvc.perform(post("/api/public/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(callbackData2)))
            .andExpect(status().isOk());
        
        assertTrue(eventManager.isCompleted(eventId), "事件应标记为已完成");
        
        logger.info("场景7验证通过 - 重复回调已被忽略");
    }
    
    /**
     * 场景8: 超时未收到回调
     */
    @Test
    @Order(8)
    @DisplayName("场景8: 超时未收到回调")
    void testScenario8_TimeoutNoCallback() throws Exception {
        logger.info("=== 场景8: 超时未收到回调 ===");
        
        String eventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, 3000);
        
        assertThrows(ExecutionException.class, () -> {
            event.future().get(10, TimeUnit.SECONDS);
        }, "应抛出执行异常（包装了超时）");
        
        assertTrue(eventManager.isCompleted(eventId), "事件应标记为已完成");
        
        logger.info("场景8验证通过 - 超时正确处理");
    }
    
    /**
     * 场景9: RestClient bindCallback自动注入验证
     */
    @Test
    @Order(9)
    @DisplayName("场景9: RestClient bindCallback自动注入")
    void testScenario9_BindCallbackAutoInjection() {
        logger.info("=== 场景9: RestClient bindCallback自动注入 ===");
        
        String preEventId = eventManager.generateEventId();
        LocalEventManager.PendingEvent preEvent = eventManager.registerEvent(preEventId, 30000);
        
        MvelRestClientBuilder builder = MvelRestClient.post("https://example.com/api")
            .bodyJson(Map.of("data", "test"))
            .bindCallback(preEventId, 30000);
        
        String eventId = builder.getEventId();
        assertNotNull(eventId, "eventId应自动生成");
        assertEquals(preEventId, eventId, "eventId应与指定的一致");
        
        assertTrue(eventManager.hasEvent(eventId), "事件应在管理器中注册");
        
        eventManager.completeEvent(eventId, "test-result");
        
        logger.info("场景9验证通过 - eventId: {}", eventId);
    }
    
    /**
     * 场景10: 事件状态查询
     */
    @Test
    @Order(10)
    @DisplayName("场景10: 事件状态查询")
    void testScenario10_EventStatusQuery() throws Exception {
        logger.info("=== 场景10: 事件状态查询 ===");
        
        String eventId = eventManager.generateEventId();
        
        MvcResult statusBeforeReg = mockMvc.perform(get("/api/internal/event/" + eventId + "/status"))
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(statusBeforeReg.getResponse().getContentAsString().contains("\"exists\":false"));
        
        eventManager.registerEvent(eventId, 10000);
        
        MvcResult statusAfterReg = mockMvc.perform(get("/api/internal/event/" + eventId + "/status"))
            .andExpect(status().isOk())
            .andReturn();
        String statusBody = statusAfterReg.getResponse().getContentAsString();
        assertTrue(statusBody.contains("\"exists\":true"));
        assertTrue(statusBody.contains("\"completed\":false"));
        
        eventManager.completeEvent(eventId, "done");
        
        MvcResult statusAfterComplete = mockMvc.perform(get("/api/internal/event/" + eventId + "/status"))
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(statusAfterComplete.getResponse().getContentAsString().contains("\"completed\":true"));
        
        logger.info("场景10验证通过");
    }
    
    @AfterAll
    void cleanupAll() {
        logger.info("=== 第三方回调端到端测试完成 ===");
        logger.info("当前等待事件数: {}", eventManager.getPendingCount());
    }
}
