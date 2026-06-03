package com.etl.engine.rest.ext.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseConverter 边界测试用例
 */
@DisplayName("ResponseConverter工具类测试")
class ResponseConverterTest {

    private ObjectMapper mapper;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        private String name;
        private Integer age;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class NestedObject {
        private String id;
        private TestUser user;
        private List<String> tags;
    }

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ==================== toMap 测试 ====================

    @Nested
    @DisplayName("toMap() 转换测试")
    class ToMapTests {

        @Test
        @DisplayName("null对象转Map返回空Map")
        void testNullToMap() {
            Map<String, Object> result = ResponseConverter.toMap(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Map对象直接返回")
        void testMapToMap() {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", "张三");
            source.put("age", 25);

            Map<String, Object> result = ResponseConverter.toMap(source);

            assertEquals(2, result.size());
            assertEquals("张三", result.get("name"));
            assertEquals(25, result.get("age"));
        }

        @Test
        @DisplayName("POJO对象转Map")
        void testPojoToMap() {
            TestUser user = new TestUser("李四", 30, "lisi@example.com");

            Map<String, Object> result = ResponseConverter.toMap(user);

            assertEquals(3, result.size());
            assertEquals("李四", result.get("name"));
            assertEquals(30, result.get("age"));
            assertEquals("lisi@example.com", result.get("email"));
        }

        @Test
        @DisplayName("JSON字符串转Map")
        void testJsonStringToMap() {
            String json = "{\"name\":\"王五\",\"age\":28,\"active\":true}";

            Map<String, Object> result = ResponseConverter.toMap(json);

            assertEquals(3, result.size());
            assertEquals("王五", result.get("name"));
            assertEquals(28, result.get("age"));
            assertEquals(true, result.get("active"));
        }

        @Test
        @DisplayName("无效JSON字符串返回空Map")
        void testInvalidJsonToMap() {
            String invalidJson = "not a json";

            Map<String, Object> result = ResponseConverter.toMap(invalidJson);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空字符串返回空Map")
        void testEmptyStringToMap() {
            Map<String, Object> result = ResponseConverter.toMap("");
            assertTrue(result.isEmpty());

            result = ResponseConverter.toMap("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("JsonNode转Map")
        void testJsonNodeToMap() throws Exception {
            JsonNode node = mapper.readTree("{\"key1\":\"value1\",\"key2\":123,\"nested\":{\"a\":1}}");

            Map<String, Object> result = ResponseConverter.toMap(node);

            assertEquals(3, result.size());
            assertEquals("value1", result.get("key1"));
            assertEquals(123, result.get("key2"));
            assertTrue(result.get("nested") instanceof Map);
        }

        @Test
        @DisplayName("数组JsonNode转Map返回空Map")
        void testArrayJsonNodeToMap() throws Exception {
            JsonNode arrayNode = mapper.readTree("[1,2,3]");

            Map<String, Object> result = ResponseConverter.toMap(arrayNode);

            assertTrue(result.isEmpty());
        }
    }

    // ==================== toJsonNode 测试 ====================

    @Nested
    @DisplayName("toJsonNode() 转换测试")
    class ToJsonNodeTests {

        @Test
        @DisplayName("null对象返回空ObjectNode")
        void testNullToJsonNode() {
            JsonNode result = ResponseConverter.toJsonNode(null);
            assertNotNull(result);
            assertTrue(result.isObject());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Map转JsonNode")
        void testMapToJsonNode() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "测试");
            map.put("count", 100);

            JsonNode result = ResponseConverter.toJsonNode(map);

            assertEquals("测试", result.get("name").asText());
            assertEquals(100, result.get("count").asInt());
        }

        @Test
        @DisplayName("POJO转JsonNode")
        void testPojoToJsonNode() {
            TestUser user = new TestUser("张三", 25, "zhangsan@test.com");

            JsonNode result = ResponseConverter.toJsonNode(user);

            assertEquals("张三", result.get("name").asText());
            assertEquals(25, result.get("age").asInt());
        }

        @Test
        @DisplayName("JSON字符串转JsonNode")
        void testJsonStringToJsonNode() {
            String json = "{\"status\":\"ok\",\"code\":200}";

            JsonNode result = ResponseConverter.toJsonNode(json);

            assertEquals("ok", result.get("status").asText());
            assertEquals(200, result.get("code").asInt());
        }

        @Test
        @DisplayName("无效JSON字符串返回空ObjectNode")
        void testInvalidJsonToJsonNode() {
            JsonNode result = ResponseConverter.toJsonNode("invalid");
            assertTrue(result.isObject());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("JsonNode直接返回")
        void testJsonNodeToJsonNode() throws Exception {
            JsonNode original = mapper.readTree("{\"test\":true}");

            JsonNode result = ResponseConverter.toJsonNode(original);

            assertSame(original, result);
        }
    }

    // ==================== toVO 测试 ====================

    @Nested
    @DisplayName("toVO() 转换测试")
    class ToVOTests {

        @Test
        @DisplayName("null对象返回null")
        void testNullToVO() {
            TestUser result = ResponseConverter.toVO(null, TestUser.class);
            assertNull(result);
        }

        @Test
        @DisplayName("同类型对象直接返回")
        void testSameTypeToVO() {
            TestUser original = new TestUser("张三", 25, "test@example.com");

            TestUser result = ResponseConverter.toVO(original, TestUser.class);

            assertSame(original, result);
        }

        @Test
        @DisplayName("Map转VO")
        void testMapToVO() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "李四");
            map.put("age", 30);
            map.put("email", "lisi@test.com");

            TestUser result = ResponseConverter.toVO(map, TestUser.class);

            assertNotNull(result);
            assertEquals("李四", result.getName());
            assertEquals(30, result.getAge());
            assertEquals("lisi@test.com", result.getEmail());
        }

        @Test
        @DisplayName("JsonNode转VO")
        void testJsonNodeToVO() throws Exception {
            JsonNode node = mapper.readTree("{\"name\":\"王五\",\"age\":28,\"email\":\"wangwu@test.com\"}");

            TestUser result = ResponseConverter.toVO(node, TestUser.class);

            assertNotNull(result);
            assertEquals("王五", result.getName());
            assertEquals(28, result.getAge());
        }

        @Test
        @DisplayName("JSON字符串转VO")
        void testJsonStringToVO() {
            String json = "{\"name\":\"赵六\",\"age\":35,\"email\":\"zhaoliu@test.com\"}";

            TestUser result = ResponseConverter.toVO(json, TestUser.class);

            assertNotNull(result);
            assertEquals("赵六", result.getName());
            assertEquals(35, result.getAge());
        }

        @Test
        @DisplayName("部分字段缺失转VO")
        void testPartialMapToVO() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "测试");

            TestUser result = ResponseConverter.toVO(map, TestUser.class);

            assertNotNull(result);
            assertEquals("测试", result.getName());
            assertNull(result.getAge());
            assertNull(result.getEmail());
        }

        @Test
        @DisplayName("无效类型转换返回null")
        void testInvalidConversion() {
            Object invalidObject = new ArrayList<>();

            TestUser result = ResponseConverter.toVO(invalidObject, TestUser.class);

            assertNull(result);
        }
    }

    // ==================== containsKey 测试 ====================

    @Nested
    @DisplayName("containsKey() 测试")
    class ContainsKeyTests {

        @Test
        @DisplayName("null对象返回false")
        void testNullContainsKey() {
            assertFalse(ResponseConverter.containsKey(null, "key"));
        }

        @Test
        @DisplayName("null key返回false")
        void testNullKeyContainsKey() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");

            assertFalse(ResponseConverter.containsKey(map, null));
        }

        @Test
        @DisplayName("Map包含key返回true")
        void testMapContainsKey() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");
            map.put("age", 25);

            assertTrue(ResponseConverter.containsKey(map, "name"));
            assertTrue(ResponseConverter.containsKey(map, "age"));
            assertFalse(ResponseConverter.containsKey(map, "email"));
        }

        @Test
        @DisplayName("JsonNode包含key返回true")
        void testJsonNodeContainsKey() throws Exception {
            JsonNode node = mapper.readTree("{\"key1\":\"value1\",\"key2\":\"value2\"}");

            assertTrue(ResponseConverter.containsKey(node, "key1"));
            assertTrue(ResponseConverter.containsKey(node, "key2"));
            assertFalse(ResponseConverter.containsKey(node, "key3"));
        }

        @Test
        @DisplayName("POJO包含key返回true")
        void testPojoContainsKey() {
            TestUser user = new TestUser("张三", 25, "test@example.com");

            assertTrue(ResponseConverter.containsKey(user, "name"));
            assertTrue(ResponseConverter.containsKey(user, "age"));
            assertTrue(ResponseConverter.containsKey(user, "email"));
            assertFalse(ResponseConverter.containsKey(user, "phone"));
        }

        @Test
        @DisplayName("JSON字符串包含key返回true")
        void testJsonStringContainsKey() {
            String json = "{\"status\":\"ok\",\"message\":\"success\"}";

            assertTrue(ResponseConverter.containsKey(json, "status"));
            assertTrue(ResponseConverter.containsKey(json, "message"));
            assertFalse(ResponseConverter.containsKey(json, "code"));
        }
    }

    // ==================== getValue 测试 ====================

    @Nested
    @DisplayName("getValue() 测试")
    class GetValueTests {

        @Test
        @DisplayName("null对象返回null")
        void testNullGetValue() {
            assertNull(ResponseConverter.getValue(null, "key"));
        }

        @Test
        @DisplayName("null key返回null")
        void testNullKeyGetValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");

            assertNull(ResponseConverter.getValue(map, null));
        }

        @Test
        @DisplayName("Map获取值")
        void testMapGetValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "张三");
            map.put("age", 25);

            assertEquals("张三", ResponseConverter.getValue(map, "name"));
            assertEquals(25, ResponseConverter.getValue(map, "age"));
            assertNull(ResponseConverter.getValue(map, "email"));
        }

        @Test
        @DisplayName("JsonNode获取值")
        void testJsonNodeGetValue() throws Exception {
            JsonNode node = mapper.readTree("{\"name\":\"李四\",\"age\":30,\"active\":true}");

            assertEquals("李四", ResponseConverter.getValue(node, "name"));
            assertEquals(30, ResponseConverter.getValue(node, "age"));
            assertEquals(true, ResponseConverter.getValue(node, "active"));
        }

        @Test
        @DisplayName("嵌套对象获取值")
        void testNestedGetValue() {
            Map<String, Object> nested = new HashMap<>();
            nested.put("city", "北京");
            nested.put("zip", "100000");

            Map<String, Object> map = new HashMap<>();
            map.put("name", "测试");
            map.put("address", nested);

            Object address = ResponseConverter.getValue(map, "address");
            assertTrue(address instanceof Map);
            assertEquals("北京", ((Map<?, ?>) address).get("city"));
        }

        @Test
        @DisplayName("带类型转换获取值")
        void testGetValueWithType() {
            Map<String, Object> map = new HashMap<>();
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", "王五");
            userData.put("age", 28);
            userData.put("email", "wangwu@test.com");
            map.put("user", userData);

            TestUser user = ResponseConverter.getValue(map, "user", TestUser.class);

            assertNotNull(user);
            assertEquals("王五", user.getName());
            assertEquals(28, user.getAge());
        }
    }

    // ==================== isJsonString 测试 ====================

    @Nested
    @DisplayName("isJsonString() 测试")
    class IsJsonStringTests {

        @Test
        @DisplayName("有效JSON对象字符串")
        void testValidJsonObject() {
            assertTrue(ResponseConverter.isJsonString("{\"key\":\"value\"}"));
            assertTrue(ResponseConverter.isJsonString("  {\"key\":\"value\"}  "));
        }

        @Test
        @DisplayName("有效JSON数组字符串")
        void testValidJsonArray() {
            assertTrue(ResponseConverter.isJsonString("[1,2,3]"));
            assertTrue(ResponseConverter.isJsonString("  [1,2,3]  "));
        }

        @Test
        @DisplayName("null返回false")
        void testNullIsJsonString() {
            assertFalse(ResponseConverter.isJsonString(null));
        }

        @Test
        @DisplayName("空字符串返回false")
        void testEmptyIsJsonString() {
            assertFalse(ResponseConverter.isJsonString(""));
            assertFalse(ResponseConverter.isJsonString("   "));
        }

        @Test
        @DisplayName("非JSON字符串返回false")
        void testNonJsonString() {
            assertFalse(ResponseConverter.isJsonString("hello world"));
            assertFalse(ResponseConverter.isJsonString("123"));
            assertFalse(ResponseConverter.isJsonString("true"));
        }
    }

    // ==================== 边界场景测试 ====================

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空Map处理")
        void testEmptyMap() {
            Map<String, Object> emptyMap = new HashMap<>();

            assertTrue(ResponseConverter.toMap(emptyMap).isEmpty());
            assertTrue(ResponseConverter.toJsonNode(emptyMap).isEmpty());
            assertTrue(ResponseConverter.containsKey(emptyMap, "any"));
            assertNull(ResponseConverter.getValue(emptyMap, "any"));
        }

        @Test
        @DisplayName("包含null值的Map")
        void testMapWithNullValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");
            map.put("value", null);

            assertTrue(ResponseConverter.containsKey(map, "name"));
            assertTrue(ResponseConverter.containsKey(map, "value"));
            assertNull(ResponseConverter.getValue(map, "value"));
        }

        @Test
        @DisplayName("特殊字符key处理")
        void testSpecialKeyChars() {
            Map<String, Object> map = new HashMap<>();
            map.put("key-with-dash", "value1");
            map.put("key.with.dot", "value2");
            map.put("key_with_underscore", "value3");

            assertEquals("value1", ResponseConverter.getValue(map, "key-with-dash"));
            assertEquals("value2", ResponseConverter.getValue(map, "key.with.dot"));
            assertEquals("value3", ResponseConverter.getValue(map, "key_with_underscore"));
        }

        @Test
        @DisplayName("Unicode字符处理")
        void testUnicodeChars() {
            Map<String, Object> map = new HashMap<>();
            map.put("姓名", "张三");
            map.put("年龄", 25);
            map.put("emoji", "😀🎉");

            Map<String, Object> result = ResponseConverter.toMap(map);
            assertEquals("张三", result.get("姓名"));
            assertEquals(25, result.get("年龄"));
            assertEquals("😀🎉", result.get("emoji"));
        }

        @Test
        @DisplayName("大数字处理")
        void testBigNumbers() {
            Map<String, Object> map = new HashMap<>();
            map.put("intValue", Integer.MAX_VALUE);
            map.put("longValue", Long.MAX_VALUE);
            map.put("doubleValue", Double.MAX_VALUE);

            JsonNode node = ResponseConverter.toJsonNode(map);

            assertEquals(Integer.MAX_VALUE, node.get("intValue").asInt());
            assertEquals(Long.MAX_VALUE, node.get("longValue").asLong());
            assertEquals(Double.MAX_VALUE, node.get("doubleValue").asDouble());
        }

        @Test
        @DisplayName("嵌套List处理")
        void testNestedList() throws Exception {
            String json = "{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}],\"numbers\":[1,2,3]}";

            Map<String, Object> result = ResponseConverter.toMap(json);

            assertTrue(result.get("items") instanceof List);
            assertEquals(2, ((List<?>) result.get("items")).size());
            assertTrue(result.get("numbers") instanceof List);
            assertEquals(3, ((List<?>) result.get("numbers")).size());
        }

        @Test
        @DisplayName("类型不匹配返回null或默认值")
        void testTypeMismatch() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");

            // 尝试将Map转换为不兼容的类型
            Integer result = ResponseConverter.toVO(map, Integer.class);
            assertNull(result);
        }
    }

    // ==================== 性能测试 ====================

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("大量Map转换性能")
        void testLargeMapConversion() {
            Map<String, Object> largeMap = new LinkedHashMap<>();
            for (int i = 0; i < 1000; i++) {
                largeMap.put("key" + i, "value" + i);
            }

            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                ResponseConverter.toJsonNode(largeMap);
            }
            long duration = System.currentTimeMillis() - start;

            assertTrue(duration < 2000, "100次转换应该在2秒内完成，实际耗时: " + duration + "ms");
        }

        @Test
        @DisplayName("大量JSON字符串解析性能")
        void testLargeJsonStringParsing() {
            StringBuilder sb = new StringBuilder("{\"data\":[");
            for (int i = 0; i < 100; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"id\":").append(i).append(",\"name\":\"item").append(i).append("\"}");
            }
            sb.append("]}");
            String largeJson = sb.toString();

            long start = System.currentTimeMillis();
            for (int i = 0; i < 50; i++) {
                ResponseConverter.toMap(largeJson);
            }
            long duration = System.currentTimeMillis() - start;

            assertTrue(duration < 2000, "50次解析应该在2秒内完成，实际耗时: " + duration + "ms");
        }
    }
}
