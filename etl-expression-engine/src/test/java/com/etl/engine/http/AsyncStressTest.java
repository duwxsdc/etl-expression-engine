package com.etl.engine.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("异步操作高并发压力测试")
class AsyncStressTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setup() throws Exception {
        mockMvc.perform(delete("/etl/expression/test/resource/cleanup"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("压力测试1: 高并发资源创建与释放")
    void testHighConcurrencyResourceCreateRelease() throws Exception {
        int threadCount = 50;
        int requestsPerThread = 10;
        int totalRequests = threadCount * requestsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        long startTime = System.currentTimeMillis();
                        
                        try {
                            MvcResult createResult = mockMvc.perform(
                                get("/etl/expression/test/resource/create")
                                    .param("count", "5")
                                    .param("autoRelease", "false")
                            ).andExpect(status().isOk()).andReturn();
                            
                            HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(),
                                createResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                                "application/json");
                            
                            List<Number> resourceIds = response.extract("resourceIds");
                            assertNotNull(resourceIds);
                            
                            MvcResult releaseResult = mockMvc.perform(
                                post("/etl/expression/test/resource/release")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(resourceIds))
                            ).andExpect(status().isOk()).andReturn();
                            
                            successCount.incrementAndGet();
                            totalLatency.addAndGet(System.currentTimeMillis() - startTime);
                            
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        System.out.println("\n=== 高并发资源创建与释放测试结果 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程请求数: " + requestsPerThread);
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("失败请求数: " + failureCount.get());
        System.out.println("成功率: " + String.format("%.2f%%", (double) successCount.get() / totalRequests * 100));
        System.out.println("总测试时间: " + totalTestTime + "ms");
        System.out.println("平均延迟: " + (successCount.get() > 0 ? totalLatency.get() / successCount.get() : 0) + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", (double) successCount.get() / (totalTestTime / 1000.0)) + " requests/sec");
        
        MvcResult statusResult = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk()).andReturn();
        
        HttpResponseImpl statusResponse = new HttpResponseImpl(200, "OK", Map.of(),
                statusResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                "application/json");
        
        int activeResourceCount = statusResponse.extract("activeResourceCount", Integer.class);
        System.out.println("最终活跃资源数: " + activeResourceCount);
        
        assertTrue(successCount.get() > totalRequests * 0.95, "成功率应大于95%");
        assertEquals(0, activeResourceCount, "所有资源应已释放");
    }
    
    @Test
    @DisplayName("压力测试2: 高并发异步延迟任务")
    void testHighConcurrencyAsyncDelayed() throws Exception {
        int threadCount = 30;
        int requestsPerThread = 8;
        int totalRequests = threadCount * requestsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        long startTime = System.currentTimeMillis();
                        
                        try {
                            MvcResult result = mockMvc.perform(
                                get("/etl/expression/test/async/delayed")
                                    .param("delayMs", "10")
                                    .param("taskName", "stress-test")
                            ).andExpect(status().isOk()).andReturn();
                            
                            HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(),
                                result.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                                "application/json");
                            
                            Integer actualDelay = response.extract("actualDelayMs", Integer.class);
                            assertNotNull(actualDelay);
                            assertTrue(actualDelay >= 10);
                            
                            successCount.incrementAndGet();
                            long latency = System.currentTimeMillis() - startTime;
                            totalLatency.addAndGet(latency);
                            latencies.add(latency);
                            
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        Collections.sort(latencies);
        long p50 = latencies.get(latencies.size() / 2);
        long p90 = latencies.get((int) (latencies.size() * 0.9));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        
        System.out.println("\n=== 高并发异步延迟任务测试结果 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程请求数: " + requestsPerThread);
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("失败请求数: " + failureCount.get());
        System.out.println("成功率: " + String.format("%.2f%%", (double) successCount.get() / totalRequests * 100));
        System.out.println("总测试时间: " + totalTestTime + "ms");
        System.out.println("平均延迟: " + (successCount.get() > 0 ? totalLatency.get() / successCount.get() : 0) + "ms");
        System.out.println("P50延迟: " + p50 + "ms");
        System.out.println("P90延迟: " + p90 + "ms");
        System.out.println("P99延迟: " + p99 + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", (double) successCount.get() / (totalTestTime / 1000.0)) + " requests/sec");
        
        assertTrue(successCount.get() > totalRequests * 0.95, "成功率应大于95%");
    }
    
    @Test
    @DisplayName("压力测试3: 高并发并发任务执行")
    void testHighConcurrencyConcurrentTasks() throws Exception {
        int iterations = 20;
        int taskCountPerIteration = 10;
        int totalTasks = iterations * taskCountPerIteration;
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalExecutionTime = new AtomicLong(0);
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            try {
                MvcResult result = mockMvc.perform(
                    get("/etl/expression/test/async/concurrent")
                        .param("taskCount", String.valueOf(taskCountPerIteration))
                        .param("delayMs", "20")
                ).andExpect(status().isOk()).andReturn();
                
                HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(),
                    result.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                    "application/json");
                
                Integer taskCount = response.extract("taskCount", Integer.class);
                Long totalTimeMs = response.extract("totalTimeMs", Long.class);
                List<Map<String, Object>> taskResults = response.extract("taskResults");
                
                assertEquals(taskCountPerIteration, taskCount);
                assertEquals(taskCountPerIteration, taskResults.size());
                assertTrue(totalTimeMs < taskCountPerIteration * 20 + 100, "并发执行时间应远小于串行时间");
                
                successCount.addAndGet(taskCountPerIteration);
                totalExecutionTime.addAndGet(System.currentTimeMillis() - startTime);
                
            } catch (Exception e) {
                failureCount.addAndGet(taskCountPerIteration);
            }
        }
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        System.out.println("\n=== 高并发并发任务执行测试结果 ===");
        System.out.println("迭代次数: " + iterations);
        System.out.println("每次任务数: " + taskCountPerIteration);
        System.out.println("总任务数: " + totalTasks);
        System.out.println("成功任务数: " + successCount.get());
        System.out.println("失败任务数: " + failureCount.get());
        System.out.println("成功率: " + String.format("%.2f%%", (double) successCount.get() / totalTasks * 100));
        System.out.println("总测试时间: " + totalTestTime + "ms");
        System.out.println("平均每次迭代时间: " + totalExecutionTime.get() / iterations + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", (double) successCount.get() / (totalTestTime / 1000.0)) + " tasks/sec");
        
        assertTrue(successCount.get() > totalTasks * 0.95, "成功率应大于95%");
    }
    
    @Test
    @DisplayName("压力测试4: 资源泄漏检测")
    void testResourceLeakDetection() throws Exception {
        int createIterations = 100;
        int resourcesPerCreate = 10;
        int totalCreatedResources = createIterations * resourcesPerCreate;
        
        List<Long> allResourceIds = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < createIterations; i++) {
            MvcResult createResult = mockMvc.perform(
                get("/etl/expression/test/resource/create")
                    .param("count", String.valueOf(resourcesPerCreate))
                    .param("autoRelease", "false")
            ).andExpect(status().isOk()).andReturn();
            
            HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(),
                createResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                "application/json");
            
            List<Number> resourceIds = response.extract("resourceIds");
            for (Number id : resourceIds) {
                allResourceIds.add(id.longValue());
            }
        }
        
        MvcResult statusBeforeRelease = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk()).andReturn();
        
        HttpResponseImpl statusResponse = new HttpResponseImpl(200, "OK", Map.of(),
                statusBeforeRelease.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                "application/json");
        
        int activeCountBeforeRelease = statusResponse.extract("activeResourceCount", Integer.class);
        System.out.println("\n=== 资源泄漏检测测试结果 ===");
        System.out.println("创建资源总数: " + totalCreatedResources);
        System.out.println("释放前活跃资源数: " + activeCountBeforeRelease);
        
        assertEquals(totalCreatedResources, activeCountBeforeRelease, "所有创建的资源应被跟踪");
        
        int batchSize = 50;
        List<Long> batch = new ArrayList<>();
        int releasedCount = 0;
        
        for (Long id : allResourceIds) {
            batch.add(id);
            if (batch.size() >= batchSize) {
                mockMvc.perform(
                    post("/etl/expression/test/resource/release")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batch))
                ).andExpect(status().isOk());
                releasedCount += batch.size();
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            mockMvc.perform(
                post("/etl/expression/test/resource/release")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(batch))
            ).andExpect(status().isOk());
            releasedCount += batch.size();
        }
        
        MvcResult statusAfterRelease = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk()).andReturn();
        
        HttpResponseImpl statusAfterResponse = new HttpResponseImpl(200, "OK", Map.of(),
                statusAfterRelease.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                "application/json");
        
        int activeCountAfterRelease = statusAfterResponse.extract("activeResourceCount", Integer.class);
        int potentialLeakCount = statusAfterResponse.extract("potentialLeakCount", Integer.class);
        
        System.out.println("释放资源数: " + releasedCount);
        System.out.println("释放后活跃资源数: " + activeCountAfterRelease);
        System.out.println("潜在泄漏数: " + potentialLeakCount);
        
        assertEquals(0, activeCountAfterRelease, "所有资源应已释放");
        assertEquals(0, potentialLeakCount, "不应有潜在泄漏");
    }
    
    @Test
    @DisplayName("压力测试5: 混合场景压力测试")
    void testMixedScenarioStress() throws Exception {
        int threadCount = 20;
        int operationsPerThread = 15;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        Map<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
        operationCounts.put("resource_create", new AtomicInteger(0));
        operationCounts.put("resource_release", new AtomicInteger(0));
        operationCounts.put("async_delayed", new AtomicInteger(0));
        operationCounts.put("async_concurrent", new AtomicInteger(0));
        operationCounts.put("resource_status", new AtomicInteger(0));
        
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFailure = new AtomicInteger(0);
        
        Random random = new Random();
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    List<Long> myResources = new ArrayList<>();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            int operation = random.nextInt(5);
                            
                            switch (operation) {
                                case 0:
                                    MvcResult createResult = mockMvc.perform(
                                        get("/etl/expression/test/resource/create")
                                            .param("count", "3")
                                            .param("autoRelease", "false")
                                    ).andExpect(status().isOk()).andReturn();
                                    
                                    HttpResponseImpl createResp = new HttpResponseImpl(200, "OK", Map.of(),
                                        createResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                                        "application/json");
                                    List<Number> ids = createResp.extract("resourceIds");
                                    for (Number id : ids) {
                                        myResources.add(id.longValue());
                                    }
                                    operationCounts.get("resource_create").incrementAndGet();
                                    break;
                                    
                                case 1:
                                    if (!myResources.isEmpty()) {
                                        List<Long> toRelease = myResources.subList(0, Math.min(2, myResources.size()));
                                        mockMvc.perform(
                                            post("/etl/expression/test/resource/release")
                                                .contentType("application/json")
                                                .content(objectMapper.writeValueAsString(toRelease))
                                        ).andExpect(status().isOk());
                                        toRelease.clear();
                                        operationCounts.get("resource_release").incrementAndGet();
                                    }
                                    break;
                                    
                                case 2:
                                    mockMvc.perform(
                                        get("/etl/expression/test/async/delayed")
                                            .param("delayMs", "5")
                                    ).andExpect(status().isOk());
                                    operationCounts.get("async_delayed").incrementAndGet();
                                    break;
                                    
                                case 3:
                                    mockMvc.perform(
                                        get("/etl/expression/test/async/concurrent")
                                            .param("taskCount", "3")
                                            .param("delayMs", "5")
                                    ).andExpect(status().isOk());
                                    operationCounts.get("async_concurrent").incrementAndGet();
                                    break;
                                    
                                case 4:
                                    mockMvc.perform(
                                        get("/etl/expression/test/resource/status")
                                    ).andExpect(status().isOk());
                                    operationCounts.get("resource_status").incrementAndGet();
                                    break;
                            }
                            
                            totalSuccess.incrementAndGet();
                            
                        } catch (Exception e) {
                            totalFailure.incrementAndGet();
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        int totalOperations = totalSuccess.get() + totalFailure.get();
        
        mockMvc.perform(delete("/etl/expression/test/resource/cleanup"))
                .andExpect(status().isOk());
        
        MvcResult finalStatus = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk()).andReturn();
        
        HttpResponseImpl finalStatusResp = new HttpResponseImpl(200, "OK", Map.of(),
                finalStatus.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8),
                "application/json");
        
        System.out.println("\n=== 混合场景压力测试结果 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程操作数: " + operationsPerThread);
        System.out.println("总操作数: " + totalOperations);
        System.out.println("成功操作数: " + totalSuccess.get());
        System.out.println("失败操作数: " + totalFailure.get());
        System.out.println("成功率: " + String.format("%.2f%%", (double) totalSuccess.get() / totalOperations * 100));
        System.out.println("总测试时间: " + totalTestTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", (double) totalSuccess.get() / (totalTestTime / 1000.0)) + " ops/sec");
        System.out.println("\n操作分布:");
        operationCounts.forEach((op, count) -> 
            System.out.println("  " + op + ": " + count.get()));
        
        int finalActiveResources = finalStatusResp.extract("activeResourceCount", Integer.class);
        System.out.println("\n最终活跃资源数: " + finalActiveResources);
        
        assertTrue(totalSuccess.get() > totalOperations * 0.90, "成功率应大于90%");
        assertEquals(0, finalActiveResources, "清理后应无活跃资源");
    }
}
