package com.etl.engine.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public class ConvertUtilsTest {
    // =========================================================================
    // 自动化断言工具
    // =========================================================================
    private static int passedCount = 0;
    private static int failedCount = 0;
    private static void assertEquals(String testName, Object expected, Object actual) {
        boolean isPassed = Objects.equals(expected, actual);
        if (isPassed) {
            passedCount++;
            System.out.printf("✅ 通过: %-60s (预期: %-12s, 实际: %-12s)%n", testName, expected, actual);
        } else {
            failedCount++;
            System.err.printf("❌ 不通过: %-60s (预期: %-12s, 实际: %-12s)%n", testName, expected, actual);
        }
    }
    private static void assertThrows(String testName, Class<? extends Exception> expectedType, Runnable action) {
        try {
            action.run();
            failedCount++;
            System.err.printf("❌ 不通过: %-60s (预期抛出: %s, 实际: 未抛出异常)%n", testName, expectedType.getSimpleName());
        } catch (Exception e) {
            if (expectedType.isInstance(e)) {
                passedCount++;
                System.out.printf("✅ 通过: %-60s (成功捕获: %s)%n", testName, expectedType.getSimpleName());
            } else {
                failedCount++;
                System.err.printf("❌ 不通过: %-60s (预期抛出: %s, 实际抛出: %s)%n", testName, expectedType.getSimpleName(), e.getClass().getSimpleName());
            }
        }
    }
    // =========================================================================
    // 测试数据准备 (🔥 修复了 Unicode 转义的引号问题)
    // =========================================================================
    private static final String TEST_JSON = "{\n" +
            "  \"code\": 200,\n" +
            "  \"msg\": \"success\",\n" +
            "  \"empty_str\": \"\",\n" +
            "  \"max_int\": 2147483647,\n" +
            "  \"overflow_int\": 2147483648,\n" +
            "  \"bool_true\": true,\n" +
            "  \"bool_str\": \"true\",\n" +
            "  \"special_char\": \"Hello\\nWorld\\t\\\"Quote\\\"\",\n" +
            "  \"unicode\": \"\\u0041\\u0042\\u0043\",\n" + // 🔥 修复：Unicode转义必须在双引号内
            "  \"data\": {\n" +
            "    \"id\": 999888777666,\n" +
            "    \"name\": null,\n" +
            "    \"matrix\": [[1, 2], [3, 4]],\n" +
            "    \"users\": [\n" +
            "      {\"uid\": 1, \"uname\": \"Alice\"},\n" +
            "      {\"uid\": 2, \"uname\": \"Bob\"}\n" +
            "    ]\n" +
            "  }\n" +
            "}";
    private static final JsonNode ROOT = ConvertUtils.toJsonNode(TEST_JSON);
    // 用于测试 POJO 转换的内部类
    public static class User { public int uid; public String uname; }
    // =========================================================================
    // 核心测试入口
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("        启动 ConvertUtils 生产级严苛单元测试 (JDK21)      ");
        System.out.println("========================================================\n");
        testBasicAndBoundaryTypes();
        testDeepNestingAndArrays();
        testNullAndMissingExtremeSemantics();
        testStrictMatchingAndTypeConfusion();
        testMalformedAndAttackPaths();
        testObjectOverloadAndPojoConversion();
        testExceptionAndEdgeCases();
        System.out.println("\n========================================================");
        System.out.printf("测试完成: ✅ 通过 %d 项 | ❌ 不通过 %d 项%n", passedCount, failedCount);
        System.out.println("========================================================");
    }
    // -------------------------------------------------------------------------
    // 1. 基础与类型边界测试
    // -------------------------------------------------------------------------
    private static void testBasicAndBoundaryTypes() {
        System.out.println("\n>>> 1. 基础与类型边界测试");
                assertEquals("获取顶层整数", 200, ConvertUtils.getInt(ROOT, "code"));
        assertEquals("获取空字符串", "", ConvertUtils.getStr(ROOT, "empty_str"));
        assertEquals("获取边界最大Int", Integer.MAX_VALUE, ConvertUtils.getInt(ROOT, "max_int"));
        assertEquals("获取溢出Int(截断行为)", Integer.MIN_VALUE, ConvertUtils.getInt(ROOT, "overflow_int"));
        assertEquals("获取安全Long", 999888777666L, ConvertUtils.getLong(ROOT, "data.id"));
        assertEquals("获取布尔值", true, ConvertUtils.getBool(ROOT, "bool_true"));
        // Jackson 对字符串 "true" 有非标准兼容，asBoolean() 会返回 true
        assertEquals("字符串true转Boolean(兼容)", true, ConvertUtils.getBool(ROOT, "bool_str"));
        assertEquals("获取转义特殊字符", "Hello\nWorld\t\"Quote\"", ConvertUtils.getStr(ROOT, "special_char"));
        assertEquals("获取Unicode解析", "ABC", ConvertUtils.getStr(ROOT, "unicode"));
    }
    // -------------------------------------------------------------------------
    // 2. 深度嵌套与多维数组测试
    // -------------------------------------------------------------------------
    private static void testDeepNestingAndArrays() {
        System.out.println("\n>>> 2. 深度嵌套与多维数组测试");
        assertEquals("获取一维数组对象属性", "Alice", ConvertUtils.getStr(ROOT, "data.users[0].uname"));
        assertEquals("获取数组最后一个元素", 2, ConvertUtils.getInt(ROOT, "data.users[1].uid"));
        // 二维数组路径不支持，安全降级
        assertEquals("二维数组路径不支持(降级默认)", 0, ConvertUtils.getInt(ROOT, "data.matrix[0][1]", 0));
    }
    // -------------------------------------------------------------------------
    // 3. Null 与 Missing 极限语义区分
    // -------------------------------------------------------------------------
    private static void testNullAndMissingExtremeSemantics() {
        System.out.println("\n>>> 3. Null 与 Missing 极限语义区分");
        assertEquals("显式Null-hasKey", true, ConvertUtils.hasKey(ROOT, "data.name"));
        assertEquals("显式Null-hasValue", false, ConvertUtils.hasValue(ROOT, "data.name"));
        assertEquals("显式Null-getStr默认", "N/A", ConvertUtils.getStr(ROOT, "data.name", "N/A"));
        assertEquals("中间节点Null-hasKey", false, ConvertUtils.hasKey(ROOT, "data.name.sub"));
        assertEquals("中间节点Null-hasValue", false, ConvertUtils.hasValue(ROOT, "data.name.sub"));
        assertEquals("路径不存在-hasKey", false, ConvertUtils.hasKey(ROOT, "data.not.exist"));
        assertEquals("空字符串-hasValue(true)", true, ConvertUtils.hasValue(ROOT, "empty_str"));
    }
    // -------------------------------------------------------------------------
    // 4. 类型严格匹配与混淆防御
    // -------------------------------------------------------------------------
    private static void testStrictMatchingAndTypeConfusion() {
        System.out.println("\n>>> 4. 类型严格匹配与混淆防御");
        assertEquals("整数200==200", true, ConvertUtils.hasKeyAndEquals(ROOT, "code", 200));
        assertEquals("整数200!=\"200\"", false, ConvertUtils.hasKeyAndEquals(ROOT, "code", "200"));
        assertEquals("布尔true!=字符串true", false, ConvertUtils.hasKeyAndEquals(ROOT, "bool_true", "true"));
        assertEquals("布尔true==布尔true", true, ConvertUtils.hasKeyAndEquals(ROOT, "bool_true", true));
        assertEquals("空字符串==\"\"", true, ConvertUtils.hasKeyAndEquals(ROOT, "empty_str", ""));
        assertEquals("空字符串!=null", false, ConvertUtils.hasKeyAndEquals(ROOT, "empty_str", null));
        assertEquals("显式Null==null", true, ConvertUtils.hasKeyAndEquals(ROOT, "data.name", null));
        assertEquals("Int与Long数值相等(200==200L)", true, ConvertUtils.hasKeyAndEquals(ROOT, "code", 200L));
    }
    // -------------------------------------------------------------------------
    // 5. 畸形路径与注入攻击防御
    // -------------------------------------------------------------------------
    private static void testMalformedAndAttackPaths() {
        System.out.println("\n>>> 5. 畸形路径与注入攻击防御");
        assertEquals("数组越界", -1, ConvertUtils.getInt(ROOT, "data.users[5].uid", -1));
        assertEquals("负数下标", "ERR", ConvertUtils.getStr(ROOT, "data.users[-1].uname", "ERR"));
        assertEquals("非数字下标(防XSS)", "ERR", ConvertUtils.getStr(ROOT, "data.users[abc].uname", "ERR"));
        assertEquals("双点号路径(防降级)", 0, ConvertUtils.getInt(ROOT, "data..id", 0));
        assertEquals("前缀点号路径", 0, ConvertUtils.getInt(ROOT, ".code", 0));
        assertEquals("后缀点号路径", 0, ConvertUtils.getInt(ROOT, "code.", 0));
        assertEquals("空路径-hasKey", false, ConvertUtils.hasKey(ROOT, ""));
        assertEquals("Null路径-hasValue", false, ConvertUtils.hasValue(ROOT, null));
    }
    // -------------------------------------------------------------------------
    // 6. Object 重载方法与 POJO 互转
    // -------------------------------------------------------------------------
    private static void testObjectOverloadAndPojoConversion() {
        System.out.println("\n>>> 6. Object 重载方法与 POJO 互转");
        Map<String, Object> rootMap = ConvertUtils.toMap(ROOT);
        assertEquals("Map-hasKey", true, ConvertUtils.hasKey(rootMap, "code"));
        assertEquals("Map-getInt", 200, ConvertUtils.getInt(rootMap, "code"));
        User user1 = ConvertUtils.toBean(ROOT.get("data").get("users").get(0), User.class);
        assertEquals("Bean转换单个对象-uid", 1, user1.uid);
        assertEquals("Bean转换单个对象-uname", "Alice", user1.uname);
        List<User> userList = ConvertUtils.toList(ROOT.get("data").get("users"), User.class);
        assertEquals("Bean转换List大小", 2, userList.size());
        assertEquals("Bean转换List-第二个用户", "Bob", userList.get(1).uname);
    }
    // -------------------------------------------------------------------------
    // 7. 极端边界与异常熔断测试
    // -------------------------------------------------------------------------
    private static void testExceptionAndEdgeCases() {
        System.out.println("\n>>> 7. 极端边界与异常熔断测试");
        assertEquals("null转JsonNode类型", true, ConvertUtils.toJsonNode(null).isNull());
        assertEquals("Map转JsonNode", true, ConvertUtils.isObject(ConvertUtils.toJsonNode(new HashMap<>())));
        assertEquals("List转JsonNode", true, ConvertUtils.isArray(ConvertUtils.toJsonNode(new ArrayList<>())));
        assertEquals("null转Map返回空集合", Collections.EMPTY_MAP, ConvertUtils.toMap(null));
        assertEquals("null转List返回空集合", Collections.EMPTY_LIST, ConvertUtils.toList(null, User.class));
        assertThrows("非法JSON字符串解析抛异常", ConvertUtils.ConvertException.class, () -> {
            ConvertUtils.toJsonNode("{invalid json}");
        });
        assertThrows("不可转类型抛异常(String转User)", ConvertUtils.ConvertException.class, () -> {
            ConvertUtils.convert("just_a_string", User.class);
        });
    }
}