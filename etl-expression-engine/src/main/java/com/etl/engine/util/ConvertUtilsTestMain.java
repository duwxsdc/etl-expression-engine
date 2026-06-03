package com.etl.engine.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * ConvertUtils 测试主类
 */
public class ConvertUtilsTestMain {
    public static void main(String[] args) {
        // 测试 toJsonNode
        String jsonStr = "{\"name\": \"test\", \"value\": 123}";
        JsonNode node = ConvertUtils.toJsonNode(jsonStr);
        System.out.println("toJsonNode: " + node);

        // 测试 toMap
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 456);
        Map<String, Object> result = ConvertUtils.toMap(map);
        System.out.println("toMap: " + result);

        // 测试 hasKey (支持嵌套路径)
        Map<String, Object> nested = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("innerKey", "innerValue");
        nested.put("outer", inner);
        boolean hasKey = ConvertUtils.hasKey(nested, "outer.innerKey");
        System.out.println("hasKey(outer.innerKey): " + hasKey);

        // 测试 getStr
        String strValue = ConvertUtils.getStr(nested, "outer.innerKey");
        System.out.println("getStr(outer.innerKey): " + strValue);

        System.out.println("\nConvertUtils 测试完成!");
    }
}