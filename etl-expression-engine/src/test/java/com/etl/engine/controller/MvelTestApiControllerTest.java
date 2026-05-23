package com.etl.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MvelTestApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static String sessionId;

    @Test
    @Order(1)
    @DisplayName("API1 GET请求 - 无参数默认值")
    void testApi1Get_DefaultParam() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.endpoint").value("/etl/expression/test/api1"))
                .andExpect(jsonPath("$.param").value("default"))
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertNotNull(response.get("timestamp"));
        assertNull(response.get("customHeader"));
        
        System.out.println("API1 GET默认参数测试通过: " + response);
    }

    @Test
    @Order(2)
    @DisplayName("API1 GET请求 - 带查询参数")
    void testApi1Get_WithParam() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api1")
                        .param("param", "test-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.param").value("test-value"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertEquals("test-value", response.get("param"));
        System.out.println("API1 GET带参数测试通过: " + response);
    }

    @Test
    @Order(3)
    @DisplayName("API1 GET请求 - 带自定义请求头")
    void testApi1Get_WithCustomHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api1")
                        .header("X-Custom-Header", "custom-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customHeader").value("custom-value"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertEquals("custom-value", response.get("customHeader"));
        System.out.println("API1 GET带自定义头测试通过: " + response);
    }

    @Test
    @Order(4)
    @DisplayName("API1 POST请求 - JSON请求体")
    void testApi1Post_JsonBody() throws Exception {
        String requestBody = "{\"name\":\"test\",\"value\":123}";
        
        MvcResult result = mockMvc.perform(post("/etl/expression/test/api1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertNotNull(response.get("receivedData"));
        System.out.println("API1 POST JSON测试通过: " + response);
    }

    @Test
    @Order(5)
    @DisplayName("API1 POST请求 - 带查询参数和请求体")
    void testApi1Post_WithParamAndBody() throws Exception {
        String requestBody = "{\"key\":\"value\"}";
        
        MvcResult result = mockMvc.perform(post("/etl/expression/test/api1")
                        .param("param", "post-param")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.param").value("post-param"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertEquals("post-param", response.get("param"));
        System.out.println("API1 POST带参数测试通过: " + response);
    }

    @Test
    @Order(6)
    @DisplayName("API2 GET请求 - 路径参数")
    void testApi2Get_PathVariables() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api2/electronics/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertEquals("electronics", response.get("category"));
        assertEquals(42, response.get("id"));
        System.out.println("API2路径参数测试通过: " + response);
    }

    @Test
    @Order(7)
    @DisplayName("API3 POST请求 - 复杂数据处理")
    void testApi3Post_ComplexData() throws Exception {
        String requestBody = "{\"items\":[{\"id\":1,\"name\":\"item1\"},{\"id\":2,\"name\":\"item2\"}]}";
        
        MvcResult result = mockMvc.perform(post("/etl/expression/test/api3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertNotNull(response.get("processed"));
        System.out.println("API3复杂数据测试通过: " + response);
    }

    @Test
    @Order(8)
    @DisplayName("API6 GET请求 - 搜索功能")
    void testApi6Get_Search() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api6/search")
                        .param("keyword", "test")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertEquals("test", response.get("keyword"));
        System.out.println("API6搜索测试通过: " + response);
    }

    @Test
    @Order(9)
    @DisplayName("API7 POST请求 - 批量处理")
    void testApi7Post_Batch() throws Exception {
        String requestBody = "[{\"id\":1},{\"id\":2},{\"id\":3}]";
        
        MvcResult result = mockMvc.perform(post("/etl/expression/test/api7/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertNotNull(response.get("batchId"));
        System.out.println("API7批量处理测试通过: " + response);
    }

    @Test
    @Order(10)
    @DisplayName("API8 GET请求 - 超时测试")
    void testApi8Get_Timeout() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api8/timeout")
                        .param("delay", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        assertTrue((Long) response.get("actualDelay") >= 100);
        System.out.println("API8超时测试通过: " + response);
    }

    @Test
    @Order(11)
    @DisplayName("API9 GET请求 - 错误处理")
    void testApi9Get_Error() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api9/error")
                        .param("code", "400"))
                .andExpect(status().isBadRequest())
                .andReturn();

        System.out.println("API9错误处理测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("API10 GET请求 - 请求头验证")
    void testApi10Get_Headers() throws Exception {
        MvcResult result = mockMvc.perform(get("/etl/expression/test/api10/headers")
                        .header("X-Request-Id", "req-123")
                        .header("X-Source", "unit-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = JSON_MAPPER.readValue(responseBody, Map.class);
        
        Map<String, String> headers = (Map<String, String>) response.get("receivedHeaders");
        assertEquals("req-123", headers.get("X-Request-Id"));
        assertEquals("unit-test", headers.get("X-Source"));
        System.out.println("API10请求头验证测试通过: " + response);
    }
}
