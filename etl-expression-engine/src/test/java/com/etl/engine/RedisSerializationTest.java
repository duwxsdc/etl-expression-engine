package com.etl.engine;

import com.etl.engine.context.EtlContext;
import com.etl.engine.model.EtlTaskVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Redis复杂类型序列化兼容性测试")
class RedisSerializationTest {

    private EtlContext context;

    @BeforeEach
    void setUp() {
        context = new EtlContext("SERIALIZATION-TEST-001");
    }

    @Test
    @DisplayName("测试基本类型变量 - Integer/Long/Double/Boolean/String")
    void testPrimitiveTypes() {
        context.setVariable("intVal", 42);
        context.setVariable("longVal", 9999999999L);
        context.setVariable("doubleVal", 3.14159);
        context.setVariable("boolVal", true);
        context.setVariable("strVal", "hello world");

        Map<String, Object> vars = context.getAllVariables();
        assertEquals(5, vars.size());
        assertEquals(42, vars.get("intVal"));
        assertEquals(9999999999L, vars.get("longVal"));
        assertEquals(3.14159, (Double) vars.get("doubleVal"), 0.00001);
        assertTrue((Boolean) vars.get("boolVal"));
        assertEquals("hello world", vars.get("strVal"));
    }

    @Test
    @DisplayName("测试Map类型变量 - 简单Map和嵌套Map")
    void testMapTypes() {
        Map<String, Object> simpleMap = new LinkedHashMap<>();
        simpleMap.put("key1", "value1");
        simpleMap.put("key2", 100);
        simpleMap.put("key3", true);

        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("level1", "data");
        nestedMap.put("inner", simpleMap);
        nestedMap.put("count", 50);

        context.setVariable("simpleMap", simpleMap);
        context.setVariable("nestedMap", nestedMap);

        Map<String, Object> vars = context.getAllVariables();

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedSimple = (Map<String, Object>) vars.get("simpleMap");
        assertEquals(3, retrievedSimple.size());
        assertEquals("value1", retrievedSimple.get("key1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedNested = (Map<String, Object>) vars.get("nestedMap");
        assertNotNull(retrievedNested.get("inner"));
    }

    @Test
    @DisplayName("测试List类型变量 - 包含多种元素类型的List")
    void testListTypes() {
        List<Object> mixedList = new ArrayList<>();
        mixedList.add("string");
        mixedList.add(123);
        mixedList.add(45.67);
        mixedList.add(true);
        mixedList.add(null);

        List<Map<String, Object>> mapList = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "taskA");
        mapList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 2);
        item2.put("name", "taskB");
        mapList.add(item2);

        context.setVariable("mixedList", mixedList);
        context.setVariable("mapList", mapList);

        Map<String, Object> vars = context.getAllVariables();

        @SuppressWarnings("unchecked")
        List<Object> retrievedMixed = (List<Object>) vars.get("mixedList");
        assertEquals(5, retrievedMixed.size());
        assertNull(retrievedMixed.get(4));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> retrievedMapList = (List<Map<String, Object>>) vars.get("mapList");
        assertEquals(2, retrievedMapList.size());
        assertEquals(1, retrievedMapList.get(0).get("id"));
    }

    @Test
    @DisplayName("测试Java VO对象序列化")
    void testVoObjectSerialization() {
        EtlTaskVo task1 = new EtlTaskVo(1, "数据抽取任务A", "EXTRACT", "COMPLETED", 1);
        EtlTaskVo task2 = new EtlTaskVo(2, "数据转换任务B", "TRANSFORM", "RUNNING", 2);

        context.setVariable("singleTask", task1);

        List<EtlTaskVo> taskList = Arrays.asList(task1, task2);
        context.setVariable("taskList", taskList);

        Map<String, Object> vars = context.getAllVariables();

        Object singleTaskObj = vars.get("singleTask");
        assertNotNull(singleTaskObj);
        if (singleTaskObj instanceof EtlTaskVo vo) {
            assertEquals(1, vo.getId());
            assertEquals("数据抽取任务A", vo.getTaskName());
            assertEquals("COMPLETED", vo.getStatus());
        } else if (singleTaskObj instanceof LinkedHashMap<?, ?> linkedHashMap) {
            assertEquals(1, linkedHashMap.get("id"));
            assertEquals("数据抽取任务A", linkedHashMap.get("taskName"));
        } else {
            fail("反序列化后类型异常: " + singleTaskObj.getClass().getName());
        }

        Object taskListObj = vars.get("taskList");
        assertNotNull(taskListObj);
        assertTrue(taskListObj instanceof List, "任务列表应为List类型");
    }

    @Test
    @DisplayName("测试嵌套复合结构 - List包含Map，Map包含List")
    void testNestedComplexStructure() {
        Map<String, Object> complexData = new LinkedHashMap<>();
        complexData.put("version", "1.0");

        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> configItem = new LinkedHashMap<>();
        configItem.put("config_key", "batch.size");
        configItem.put("config_value", "1000");
        configItem.put("tags", Arrays.asList("performance", "etl"));
        items.add(configItem);

        Map<String, Object> anotherConfig = new LinkedHashMap<>();
        anotherConfig.put("config_key", "timeout.seconds");
        anotherConfig.put("config_value", "300");
        anotherConfig.put("tags", Arrays.asList("timeout", "config"));
        items.add(anotherConfig);

        complexData.put("items", items);
        complexData.put("totalItems", 2);

        context.setVariable("complexData", complexData);

        Map<String, Object> vars = context.getAllVariables();
        @SuppressWarnings("unchecked")
        Map<String, Object> retrieved = (Map<String, Object>) vars.get("complexData");

        assertEquals("1.0", retrieved.get("version"));
        assertEquals(2, retrieved.get("totalItems"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> retrievedItems = (List<Map<String, Object>>) retrieved.get("items");
        assertEquals(2, retrievedItems.size());

        @SuppressWarnings("unchecked")
        List<?> tags = (List<?>) retrievedItems.get(0).get("tags");
        assertEquals(2, tags.size());
    }

    @Test
    @DisplayName("测试LocalDateTime类型序列化")
    void testLocalDateTimeSerialization() {
        LocalDateTime now = LocalDateTime.now();
        context.setVariable("createTime", now);

        Map<String, Object> vars = context.getAllVariables();
        Object timeObj = vars.get("createTime");
        assertNotNull(timeObj);
    }

    @Test
    @DisplayName("测试空集合和null值处理")
    void testEmptyAndNullValues() {
        context.setVariable("emptyList", new ArrayList<>());
        context.setVariable("emptyMap", new LinkedHashMap<>());
        context.setVariable("nullValue", "NULL");
        context.setVariable("emptyString", "");

        Map<String, Object> vars = context.getAllVariables();
        assertEquals(4, vars.size());

        Object emptyList = vars.get("emptyList");
        assertNotNull(emptyList);

        Object emptyMap = vars.get("emptyMap");
        assertNotNull(emptyMap);

        assertEquals("NULL", vars.get("nullValue"));
        assertEquals("", vars.get("emptyString"));
    }

    @Test
    @DisplayName("测试大量变量的完整会话场景模拟")
    void testFullSessionScenario() {
        context.setVariable("counter", 0);
        context.setVariable("name", "ETL-Engine");
        context.setVariable("enabled", true);

        Map<String, String> config = new LinkedHashMap<>();
        config.put("env", "prod");
        config.put("region", "cn-east");
        context.setVariable("config", config);

        List<Integer> ids = Arrays.asList(101, 102, 103, 104, 105);
        context.setVariable("ids", ids);

        EtlTaskVo task = new EtlTaskVo(99, "test-task", "TEST", "PENDING", 5);
        context.setVariable("currentTask", task);

        List<EtlTaskVo> tasks = Arrays.asList(
                new EtlTaskVo(1, "A", "EXTRACT", "DONE", 1),
                new EtlTaskVo(2, "B", "TRANSFORM", "RUNNING", 2)
        );
        context.setVariable("tasks", tasks);

        Map<String, Object> result = context.getAllVariables();
        assertEquals(7, result.size());

        assertEquals(0, result.get("counter"));
        assertEquals("ETL-Engine", result.get("name"));
        assertTrue((Boolean) result.get("enabled"));

        @SuppressWarnings("unchecked")
        List<Integer> retrievedIds = (List<Integer>) result.get("ids");
        assertEquals(5, retrievedIds.size());
        assertEquals(102, retrievedIds.get(1));
    }

    @Test
    @DisplayName("测试特殊字符字符串值")
    void testSpecialCharacterStrings() {
        context.setVariable("jsonStr", "{\"key\": \"value\", \"num\": 42}");
        context.setVariable("xmlStr", "<root><item>test</item></root>");
        context.setVariable("unicodeStr", "中文测试日本語한국어🚀");
        context.setVariable("escapeStr", "line1\nline2\ttab\r\n");

        Map<String, Object> vars = context.getAllVariables();
        assertEquals(4, vars.size());
        assertTrue(vars.get("jsonStr").toString().contains("\"key\""));
        assertTrue(vars.get("xmlStr").toString().contains("<root>"));
        assertTrue(vars.get("unicodeStr").toString().contains("中文"));
    }
}
