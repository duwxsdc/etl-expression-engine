package com.etl.engine.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("资源管理和异步操作集成测试")
class ResourceAsyncIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setup() throws Exception {
        mockMvc.perform(delete("/etl/expression/test/resource/cleanup"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("场景1: 资源创建 - 单资源创建与跟踪")
    void testScenario1_ResourceCreation() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/resource/create")
                .param("count", "1")
                .param("autoRelease", "false"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Integer createdCount = response.extract("createdCount", Integer.class);
        assertEquals(1, createdCount);
        
        Integer activeCount = response.extract("activeResourceCount", Integer.class);
        assertTrue(activeCount >= 1);
        
        List<Number> resourceIds = response.extract("resourceIds");
        assertNotNull(resourceIds);
        assertEquals(1, resourceIds.size());
    }
    
    @Test
    @DisplayName("场景2: 资源释放 - 批量释放资源")
    void testScenario2_ResourceRelease() throws Exception {
        MvcResult createResult = mockMvc.perform(get("/etl/expression/test/resource/create")
                .param("count", "3")
                .param("autoRelease", "false"))
                .andExpect(status().isOk())
                .andReturn();
        
        String createBody = createResult.getResponse().getContentAsString();
        HttpResponseImpl createResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                createBody.getBytes(StandardCharsets.UTF_8), "application/json");
        
        List<Number> resourceIds = createResponse.extract("resourceIds");
        assertEquals(3, resourceIds.size());
        
        MvcResult releaseResult = mockMvc.perform(post("/etl/expression/test/resource/release")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resourceIds)))
                .andExpect(status().isOk())
                .andReturn();
        
        String releaseBody = releaseResult.getResponse().getContentAsString();
        HttpResponseImpl releaseResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                releaseBody.getBytes(StandardCharsets.UTF_8), "application/json");
        
        Integer releasedCount = releaseResponse.extract("releasedCount", Integer.class);
        assertEquals(3, releasedCount);
        
        Integer remainingCount = releaseResponse.extract("remainingResourceCount", Integer.class);
        assertEquals(0, remainingCount);
    }
    
    @Test
    @DisplayName("场景3: 异步即时响应 - 无延迟异步操作")
    void testScenario3_AsyncImmediate() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/async/immediate")
                .param("message", "test-message"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        String type = response.extract("type");
        assertEquals("immediate", type);
        
        String message = response.extract("message");
        assertEquals("test-message", message);
        
        Integer processingTime = response.extract("processingTimeMs", Integer.class);
        assertEquals(0, processingTime);
    }
    
    @Test
    @DisplayName("场景4: 异步延迟响应 - 指定延迟时间")
    void testScenario4_AsyncDelayed() throws Exception {
        int requestedDelay = 100;
        
        long start = System.currentTimeMillis();
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/async/delayed")
                .param("delayMs", String.valueOf(requestedDelay))
                .param("taskName", "delayed-task"))
                .andExpect(status().isOk())
                .andReturn();
        long actualDuration = System.currentTimeMillis() - start;
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        String type = response.extract("type");
        assertEquals("delayed", type);
        
        String taskName = response.extract("taskName");
        assertEquals("delayed-task", taskName);
        
        Integer actualDelay = response.extract("actualDelayMs", Integer.class);
        assertTrue(actualDelay >= requestedDelay);
        
        assertTrue(actualDuration >= requestedDelay);
    }
    
    @Test
    @DisplayName("场景5: 并发控制 - 多任务并发执行")
    void testScenario5_ConcurrentExecution() throws Exception {
        int taskCount = 3;
        int delayMs = 100;
        
        long start = System.currentTimeMillis();
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/async/concurrent")
                .param("taskCount", String.valueOf(taskCount))
                .param("delayMs", String.valueOf(delayMs)))
                .andExpect(status().isOk())
                .andReturn();
        long totalDuration = System.currentTimeMillis() - start;
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        String type = response.extract("type");
        assertEquals("concurrent", type);
        
        Integer returnedTaskCount = response.extract("taskCount", Integer.class);
        assertEquals(taskCount, returnedTaskCount);
        
        Long totalTimeMs = response.extract("totalTimeMs", Long.class);
        assertTrue(totalTimeMs < taskCount * delayMs + 100);
        
        List<Map<String, Object>> taskResults = response.extract("taskResults");
        assertEquals(taskCount, taskResults.size());
        
        for (int i = 0; i < taskCount; i++) {
            Integer taskIndex = (Integer) taskResults.get(i).get("taskIndex");
            assertEquals(i, taskIndex);
        }
        
        assertTrue(totalDuration < taskCount * delayMs + 200);
    }
    
    @Test
    @DisplayName("场景6: 安全权限检查 - 不同角色权限验证")
    void testScenario6_SecurityPermissionCheck() throws Exception {
        MvcResult adminResult = mockMvc.perform(get("/etl/expression/test/security/check")
                .param("permission", "delete")
                .param("role", "admin"))
                .andExpect(status().isOk())
                .andReturn();
        
        HttpResponseImpl adminResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                adminResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8), "application/json");
        assertTrue(adminResponse.extract("allowed", Boolean.class));
        
        MvcResult userResult = mockMvc.perform(get("/etl/expression/test/security/check")
                .param("permission", "write")
                .param("role", "user"))
                .andExpect(status().isOk())
                .andReturn();
        
        HttpResponseImpl userResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                userResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8), "application/json");
        assertFalse(userResponse.extract("allowed", Boolean.class));
        
        MvcResult userReadResult = mockMvc.perform(get("/etl/expression/test/security/check")
                .param("permission", "read")
                .param("role", "user"))
                .andExpect(status().isOk())
                .andReturn();
        
        HttpResponseImpl userReadResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                userReadResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8), "application/json");
        assertTrue(userReadResponse.extract("allowed", Boolean.class));
        
        MvcResult guestResult = mockMvc.perform(get("/etl/expression/test/security/check")
                .param("permission", "read")
                .param("role", "guest"))
                .andExpect(status().isOk())
                .andReturn();
        
        HttpResponseImpl guestResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                guestResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8), "application/json");
        assertFalse(guestResponse.extract("allowed", Boolean.class));
    }
    
    @Test
    @DisplayName("场景7: 资源状态监控 - 活跃资源统计")
    void testScenario7_ResourceStatusMonitoring() throws Exception {
        mockMvc.perform(get("/etl/expression/test/resource/create")
                .param("count", "2")
                .param("autoRelease", "false"))
                .andExpect(status().isOk());
        
        MvcResult statusResult = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk())
                .andReturn();
        
        String statusBody = statusResult.getResponse().getContentAsString();
        HttpResponseImpl statusResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                statusBody.getBytes(StandardCharsets.UTF_8), "application/json");
        
        Integer activeCount = statusResponse.extract("activeResourceCount", Integer.class);
        assertTrue(activeCount >= 2);
        
        Integer trackedCount = statusResponse.extract("trackedResourceCount", Integer.class);
        assertEquals(activeCount, trackedCount);
        
        List<Map<String, Object>> resources = statusResponse.extract("resources");
        assertTrue(resources.size() >= 2);
        
        for (Map<String, Object> resource : resources) {
            assertTrue(resource.containsKey("resourceId"));
            assertTrue(resource.containsKey("createTime"));
            assertTrue(resource.containsKey("holdTimeMs"));
        }
    }
    
    @Test
    @DisplayName("场景8: 可取消任务 - 任务状态管理")
    void testScenario8_CancellableTask() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/async/cancellable")
                .param("totalDelayMs", "10000")
                .param("taskId", "test-task-001"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        String type = response.extract("type");
        assertEquals("cancellable", type);
        
        String taskId = response.extract("taskId");
        assertTrue(taskId.contains("test-task-001"), "taskId should contain 'test-task-001'");
        assertTrue(taskId.startsWith("cancellable-"), "taskId should start with 'cancellable-'");
        
        String status = response.extract("status");
        assertEquals("started", status);
        
        Long startTime = response.extract("startTime", Long.class);
        assertNotNull(startTime);
        assertTrue(startTime <= System.currentTimeMillis());
    }
    
    @Test
    @DisplayName("场景9: 资源清理 - 全量清理验证")
    void testScenario9_ResourceCleanup() throws Exception {
        mockMvc.perform(get("/etl/expression/test/resource/create")
                .param("count", "5")
                .param("autoRelease", "false"))
                .andExpect(status().isOk());
        
        MvcResult cleanupResult = mockMvc.perform(delete("/etl/expression/test/resource/cleanup"))
                .andExpect(status().isOk())
                .andReturn();
        
        String cleanupBody = cleanupResult.getResponse().getContentAsString();
        HttpResponseImpl cleanupResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                cleanupBody.getBytes(StandardCharsets.UTF_8), "application/json");
        
        Integer cleanedCount = cleanupResponse.extract("cleanedCount", Integer.class);
        assertEquals(5, cleanedCount);
        
        Integer remainingCount = cleanupResponse.extract("remainingCount", Integer.class);
        assertEquals(0, remainingCount);
        
        MvcResult statusResult = mockMvc.perform(get("/etl/expression/test/resource/status"))
                .andExpect(status().isOk())
                .andReturn();
        
        HttpResponseImpl statusResponse = new HttpResponseImpl(200, "OK", Map.of(), 
                statusResult.getResponse().getContentAsString().getBytes(StandardCharsets.UTF_8), "application/json");
        
        Integer finalCount = statusResponse.extract("activeResourceCount", Integer.class);
        assertEquals(0, finalCount);
    }
}
