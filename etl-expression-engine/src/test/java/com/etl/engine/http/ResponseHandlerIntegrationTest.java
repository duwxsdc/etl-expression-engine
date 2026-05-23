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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Response Handler集成测试")
class ResponseHandlerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    @DisplayName("场景1: Consumer函数处理 - 简单响应消费")
    void testScenario1_ConsumerSimpleResponse() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/simple"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        AtomicReference<String> capturedMessage = new AtomicReference<>();
        AtomicReference<Integer> capturedCode = new AtomicReference<>();
        
        response.response(resp -> {
            capturedMessage.set(resp.extract("message"));
            capturedCode.set(resp.extract("code", Integer.class));
        });
        
        assertEquals("Hello World", capturedMessage.get());
        assertEquals(200, capturedCode.get());
    }
    
    @Test
    @DisplayName("场景2: Function函数处理 - 返回处理结果")
    void testScenario2_FunctionReturnResult() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/simple"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Map<String, Object> processed = response.response(resp -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("extractedMessage", resp.extract("message"));
            result.put("extractedCode", resp.extract("code"));
            result.put("processed", true);
            return result;
        });
        
        assertEquals("Hello World", processed.get("extractedMessage"));
        assertEquals(200, processed.get("extractedCode"));
        assertEquals(true, processed.get("processed"));
    }
    
    @Test
    @DisplayName("场景3: 嵌套数据提取 - 多层级路径")
    void testScenario3_NestedExtraction() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/nested"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Map<String, Object> userData = response.response(resp -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", resp.extract("user.id"));
            result.put("userName", resp.extract("user.name"));
            result.put("userAge", resp.extract("user.profile.age", Integer.class));
            result.put("userDept", resp.extract("user.profile.department"));
            result.put("version", resp.extract("metadata.version"));
            return result;
        });
        
        assertEquals(1, userData.get("userId"));
        assertEquals("张三", userData.get("userName"));
        assertEquals(30, userData.get("userAge"));
        assertEquals("研发部", userData.get("userDept"));
        assertEquals("1.0", userData.get("version"));
    }
    
    @Test
    @DisplayName("场景4: 数组数据处理 - 列表提取和遍历")
    void testScenario4_ArrayProcessing() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/array"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Map<String, Object> processed = response.response(resp -> {
            Map<String, Object> result = new LinkedHashMap<>();
            
            Object items = resp.extract("items");
            assertTrue(items instanceof List);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
            
            double totalPrice = 0;
            for (Map<String, Object> item : itemsList) {
                totalPrice += ((Number) item.get("price")).doubleValue();
            }
            
            result.put("itemCount", itemsList.size());
            result.put("totalPrice", totalPrice);
            result.put("firstItemName", resp.extract("items[0].name"));
            result.put("lastItemName", resp.extract("items[2].name"));
            
            Object scores = resp.extract("scores");
            @SuppressWarnings("unchecked")
            List<Integer> scoresList = (List<Integer>) scores;
            int sum = scoresList.stream().mapToInt(i -> i).sum();
            result.put("scoresSum", sum);
            
            return result;
        });
        
        assertEquals(3, processed.get("itemCount"));
        assertEquals(46.3, (Double) processed.get("totalPrice"), 0.01);
        assertEquals("A", processed.get("firstItemName"));
        assertEquals("C", processed.get("lastItemName"));
        assertEquals(433, processed.get("scoresSum"));
    }
    
    @Test
    @DisplayName("场景5: 错误处理 - 异常响应处理")
    void testScenario5_ErrorHandling() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/error")
                .param("triggerError", "true"))
                .andExpect(status().isBadRequest())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(400, "Bad Request", Map.of(), bytes, "application/json");
        
        Map<String, Object> errorInfo = response.response(resp -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("isError", resp.extract("error"));
            result.put("errorCode", resp.extract("code", Integer.class));
            result.put("errorMessage", resp.extract("message"));
            result.put("isClientError", resp.isClientError());
            result.put("isSuccess", resp.isSuccess());
            return result;
        });
        
        assertEquals(true, errorInfo.get("isError"));
        assertEquals(400, errorInfo.get("errorCode"));
        assertTrue(((String) errorInfo.get("errorMessage")).contains("模拟"));
        assertTrue((Boolean) errorInfo.get("isClientError"));
        assertFalse((Boolean) errorInfo.get("isSuccess"));
    }
    
    @Test
    @DisplayName("场景6: 复杂数据转换 - 链式处理")
    void testScenario6_ComplexTransformation() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "test-user");
        input.put("value", 50);
        input.put("tags", List.of("tag1", "tag2"));
        
        MvcResult mvcResult = mockMvc.perform(post("/etl/expression/test/response/transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Map<String, Object> result = response.response(resp -> {
            Map<String, Object> output = new LinkedHashMap<>();
            
            String upperName = resp.extract("transformed.upperName");
            Integer doubledValue = resp.extract("transformed.doubledValue", Integer.class);
            Boolean processed = resp.extract("transformed.processed", Boolean.class);
            
            output.put("upperName", upperName);
            output.put("doubledValue", doubledValue);
            output.put("processed", processed);
            output.put("originalName", resp.extract("original.name"));
            
            return output;
        });
        
        assertEquals("TEST-USER", result.get("upperName"));
        assertEquals(100, result.get("doubledValue"));
        assertEquals(true, result.get("processed"));
        assertEquals("test-user", result.get("originalName"));
    }
    
    @Test
    @DisplayName("场景7: 复杂嵌套响应 - 订单数据")
    void testScenario7_ComplexOrderData() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/response/complex"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Map<String, Object> analysis = response.response(resp -> {
            Map<String, Object> result = new LinkedHashMap<>();
            
            Integer totalOrders = resp.extract("summary.totalOrders", Integer.class);
            Long completedOrders = resp.extract("summary.completedOrders", Long.class);
            Long pendingOrders = resp.extract("summary.pendingOrders", Long.class);
            
            result.put("totalOrders", totalOrders);
            result.put("completedOrders", completedOrders);
            result.put("pendingOrders", pendingOrders);
            result.put("completionRate", (double) completedOrders / totalOrders);
            
            String firstOrderId = resp.extract("orders[0].orderId");
            String firstOrderStatus = resp.extract("orders[0].status");
            Integer firstOrderItemCount = resp.extract("orders[0].items", List.class).size();
            
            result.put("firstOrderId", firstOrderId);
            result.put("firstOrderStatus", firstOrderStatus);
            result.put("firstOrderItemCount", firstOrderItemCount);
            
            return result;
        });
        
        assertEquals(3, analysis.get("totalOrders"));
        assertEquals(1L, analysis.get("completedOrders"));
        assertEquals(2L, analysis.get("pendingOrders"));
        assertNotNull(analysis.get("firstOrderId"));
        assertEquals("ORD-1", analysis.get("firstOrderId"));
        assertEquals(2, analysis.get("firstOrderItemCount"));
    }
}
