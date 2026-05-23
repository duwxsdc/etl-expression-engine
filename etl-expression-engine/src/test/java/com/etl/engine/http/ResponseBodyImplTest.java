package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("响应体实现测试")
class ResponseBodyImplTest {
    
    private ResponseBodyImpl jsonBody;
    private ResponseBodyImpl arrayBody;
    private ResponseBodyImpl nestedBody;
    
    @BeforeEach
    void setUp() {
        String jsonData = "{\"name\":\"张三\",\"age\":30,\"active\":true}";
        jsonBody = new ResponseBodyImpl(jsonData.getBytes(StandardCharsets.UTF_8), "application/json");
        
        String arrayData = "[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]";
        arrayBody = new ResponseBodyImpl(arrayData.getBytes(StandardCharsets.UTF_8), "application/json");
        
        String nestedData = "{\"user\":{\"name\":\"李四\",\"address\":{\"city\":\"北京\",\"zip\":\"100000\"}},\"scores\":[85,90,78]}";
        nestedBody = new ResponseBodyImpl(nestedData.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    
    @Test
    @DisplayName("asString返回原始字符串")
    void testAsString() {
        String result = jsonBody.asString();
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("30"));
    }
    
    @Test
    @DisplayName("asJson返回JsonNode")
    void testAsJson() {
        JsonNode node = jsonBody.asJson();
        assertNotNull(node);
        assertEquals("张三", node.get("name").asText());
        assertEquals(30, node.get("age").asInt());
        assertTrue(node.get("active").asBoolean());
    }
    
    @Test
    @DisplayName("asMap返回Map对象")
    void testAsMap() {
        Map<String, Object> map = jsonBody.asMap();
        assertNotNull(map);
        assertEquals("张三", map.get("name"));
        assertEquals(30, map.get("age"));
        assertEquals(true, map.get("active"));
    }
    
    @Test
    @DisplayName("asJava通过类名转换简单对象")
    void testAsJavaWithClassName() {
        Object result = jsonBody.asJava("java.util.Map");
        assertNotNull(result);
        assertTrue(result instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("张三", map.get("name"));
    }
    
    @Test
    @DisplayName("asJava通过Class转换")
    void testAsJavaWithClass() {
        Map<String, Object> result = jsonBody.asJava(Map.class);
        assertNotNull(result);
        assertEquals("张三", result.get("name"));
    }
    
    @Test
    @DisplayName("asJava转换List泛型")
    void testAsJavaListGeneric() {
        Object result = arrayBody.asJava("java.util.List<java.util.Map>");
        assertNotNull(result);
        assertTrue(result instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).get("id"));
        assertEquals("A", list.get(0).get("name"));
    }
    
    @Test
    @DisplayName("extract提取简单字段")
    void testExtractSimpleField() {
        String name = jsonBody.extract("name");
        assertEquals("张三", name);
        
        Integer age = jsonBody.extract("age");
        assertEquals(30, age);
    }
    
    @Test
    @DisplayName("extract提取嵌套字段")
    void testExtractNestedField() {
        String userName = nestedBody.extract("user.name");
        assertEquals("李四", userName);
        
        String city = nestedBody.extract("user.address.city");
        assertEquals("北京", city);
        
        String zip = nestedBody.extract("user.address.zip");
        assertEquals("100000", zip);
    }
    
    @Test
    @DisplayName("extract提取数组元素")
    void testExtractArrayElement() {
        Integer firstScore = nestedBody.extract("scores[0]");
        assertEquals(85, firstScore);
        
        Integer secondScore = nestedBody.extract("scores[1]");
        assertEquals(90, secondScore);
    }
    
    @Test
    @DisplayName("extract提取数组对象的字段")
    void testExtractArrayObjectField() {
        String firstName = arrayBody.extract("[0].name");
        assertEquals("A", firstName);
        
        Integer secondId = arrayBody.extract("[1].id");
        assertEquals(2, secondId);
    }
    
    @Test
    @DisplayName("extract指定类型转换")
    void testExtractWithType() {
        Integer age = jsonBody.extract("age", Integer.class);
        assertEquals(30, age);
        
        Boolean active = jsonBody.extract("active", Boolean.class);
        assertTrue(active);
    }
    
    @Test
    @DisplayName("extract不存在的路径返回null")
    void testExtractNonExistentPath() {
        Object result = jsonBody.extract("nonexistent");
        assertNull(result);
        
        result = nestedBody.extract("user.nonexistent.field");
        assertNull(result);
    }
    
    @Test
    @DisplayName("extract空路径抛出异常")
    void testExtractEmptyPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            jsonBody.extract("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            jsonBody.extract(null);
        });
    }
    
    @Test
    @DisplayName("custom自定义解析器")
    void testCustomParser() throws Exception {
        String upperName = jsonBody.custom(str -> {
            try {
                JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(str);
                return node.get("name").asText().toUpperCase();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("张三".toUpperCase(), upperName);
    }
    
    @Test
    @DisplayName("asBytes返回字节数组副本")
    void testAsBytes() {
        byte[] bytes = jsonBody.asBytes();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        
        bytes[0] = 0;
        byte[] bytes2 = jsonBody.asBytes();
        assertNotEquals(0, bytes2[0]);
    }
}
