package com.etl.engine.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("asJava类型转换集成测试")
class AsJavaIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl = "http://localhost:8080";
    
    @Test
    @DisplayName("场景1: 基础类型转换 - 简单对象")
    void testScenario1_SimpleObject() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/simple"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mapResult = (Map<String, Object>) response.asJava("java.util.Map");
        assertNotNull(mapResult);
        assertEquals("张三", mapResult.get("name"));
        assertEquals(30, mapResult.get("age"));
        assertEquals(true, mapResult.get("active"));
        
        String name = response.extract("name");
        assertEquals("张三", name);
        
        Integer age = response.extract("age", Integer.class);
        assertEquals(30, age);
    }
    
    @Test
    @DisplayName("场景2: 复杂对象 - 嵌套结构")
    void testScenario2_NestedObject() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/nested"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        String province = response.extract("address.province");
        assertEquals("北京", province);
        
        String city = response.extract("address.city");
        assertEquals("北京市", city);
        
        String education = response.extract("profile.education");
        assertEquals("本科", education);
        
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) response.extract("skills");
        assertNotNull(skills);
        assertEquals(3, skills.size());
        assertTrue(skills.contains("Java"));
    }
    
    @Test
    @DisplayName("场景3: 集合类型 - List转换")
    void testScenario3_ListConversion() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/list"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Object listResult = response.asJava("java.util.List");
        assertNotNull(listResult);
        assertTrue(listResult instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) listResult;
        assertEquals(5, list.size());
        
        Map<String, Object> firstItem = list.get(0);
        assertEquals(1, firstItem.get("id"));
        assertEquals("Item-1", firstItem.get("name"));
        
        Integer firstId = response.extract("[0].id", Integer.class);
        assertEquals(1, firstId);
        
        String firstName = response.extract("[1].name");
        assertEquals("Item-2", firstName);
    }
    
    @Test
    @DisplayName("场景4: 泛型类型 - Map泛型")
    void testScenario4_GenericMap() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/map"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Object scoresObj = response.extract("scores");
        assertNotNull(scoresObj);
        assertTrue(scoresObj instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) scoresObj;
        assertEquals(90, scores.get("math"));
        assertEquals(85, scores.get("english"));
        
        Integer mathScore = response.extract("scores.math", Integer.class);
        assertEquals(90, mathScore);
        
        String email = response.extract("contacts.email");
        assertEquals("test@example.com", email);
    }
    
    @Test
    @DisplayName("场景5: 边界条件 - 空值和特殊值")
    void testScenario5_BoundaryConditions() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/boundary"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Object nullValue = response.extract("nullValue");
        assertNull(nullValue);
        
        String emptyString = response.extract("emptyString");
        assertEquals("", emptyString);
        
        Object emptyList = response.extract("emptyList");
        assertNotNull(emptyList);
        assertTrue(emptyList instanceof List);
        assertEquals(0, ((List<?>) emptyList).size());
        
        Integer maxInt = response.extract("maxInt", Integer.class);
        assertEquals(Integer.MAX_VALUE, maxInt);
        
        Integer minInt = response.extract("minInt", Integer.class);
        assertEquals(Integer.MIN_VALUE, minInt);
        
        Integer zero = response.extract("zero", Integer.class);
        assertEquals(0, zero);
        
        Integer negative = response.extract("negative", Integer.class);
        assertEquals(-100, negative);
        
        String specialChars = response.extract("specialChars");
        assertTrue(specialChars.contains("特殊字符"));
        
        String unicode = response.extract("unicode");
        assertTrue(unicode.contains("中文"));
    }
    
    @Test
    @DisplayName("场景6: 泛型类型 - 嵌套泛型")
    void testScenario6_NestedGenerics() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/etl/expression/test/asjava/generic"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = mvcResult.getResponse().getContentAsString();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        HttpResponseImpl response = new HttpResponseImpl(200, "OK", Map.of(), bytes, "application/json");
        
        Object intListObj = response.extract("intList");
        assertNotNull(intListObj);
        assertTrue(intListObj instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Integer> intList = (List<Integer>) intListObj;
        assertEquals(5, intList.size());
        assertEquals(1, intList.get(0));
        assertEquals(5, intList.get(4));
        
        Object strListObj = response.extract("strList");
        assertTrue(strListObj instanceof List);
        
        @SuppressWarnings("unchecked")
        List<String> strList = (List<String>) strListObj;
        assertEquals("a", strList.get(0));
        assertEquals("c", strList.get(2));
        
        Object nestedListObj = response.extract("nestedList");
        assertTrue(nestedListObj instanceof List);
        
        @SuppressWarnings("unchecked")
        List<List<Integer>> nestedList = (List<List<Integer>>) nestedListObj;
        assertEquals(2, nestedList.size());
        assertEquals(2, nestedList.get(0).size());
    }
}
