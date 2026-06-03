package com.etl.engine.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
/**
 * 万能转换 + 嵌套取值工具类 (基于 JDK 21 特性重构，修复底层解析与语义问题)
 */
public final class ConvertUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String EMPTY_STR = "";
    private ConvertUtils() {}
    // -------------------------------------------------------------------------
    // 核心转换 (🔥 修复 toJsonNode 致命缺陷)
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object source, Class<T> targetType) {
        if (source == null) return null;
        if (targetType.isInstance(source)) return (T) source;
        try {
            return MAPPER.convertValue(source, targetType);
        } catch (Exception e) {
            throw new ConvertException("转换失败: " + source.getClass().getName() + " -> " + targetType.getName(), e);
        }
    }
    public static <T> T convert(Object source, TypeReference<T> typeRef) {
        if (source == null) return null;
        try {
            return MAPPER.convertValue(source, typeRef);
        } catch (Exception e) {
            throw new ConvertException("泛型转换失败", e);
        }
    }
    /**
     * 🔥 修复：区分 String 和其它对象，JSON字符串必须使用 readTree 解析
     */
    public static JsonNode toJsonNode(Object source) {
        if (source == null) return NullNode.getInstance();
        if (source instanceof JsonNode) return (JsonNode) source;
        if (source instanceof String) {
            try {
                return MAPPER.readTree((String) source);
            } catch (Exception e) {
                throw new ConvertException("JSON字符串解析失败", e);
            }
        }
        return MAPPER.valueToTree(source);
    }
    public static Map<String, Object> toMap(Object source) {
        if (source == null) return Collections.emptyMap();
        try {
            return MAPPER.convertValue(source, MAP_TYPE);
        } catch (Exception e) {
            throw new ConvertException("转Map失败", e);
        }
    }
    public static <T> T toBean(Object source, Class<T> beanType) {
        return convert(source, beanType);
    }
    public static <T> List<T> toList(Object source, Class<T> beanType) {
        if (source == null) return Collections.emptyList();
        try {
            return MAPPER.convertValue(source, TypeFactory.defaultInstance().constructCollectionType(List.class, beanType));
        } catch (Exception e) {
            throw new ConvertException("转List失败", e);
        }
    }
    // -------------------------------------------------------------------------
    // 🔥 嵌套 KEY 工具方法（修复中间节点为 Null 时的语义）
    // -------------------------------------------------------------------------
    private static JsonNode getNestedNode(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) return MissingNode.getInstance();
        JsonNode node = root;
        // 🔥 修复：使用 -1 参数，保留末尾的空字符串（防止 "data." 被解析成 ["data"]）
        String[] keys = path.split("\\.", -1);
        for (String key : keys) {
            // 🔥 修复：遇到空 key（连续点号 a..b 或首尾点号 .a b.），直接视为非法路径，返回 MissingNode
            if (key.isEmpty()) return MissingNode.getInstance();
            if (node == null || node.isMissingNode() || node.isNull()) {
                return MissingNode.getInstance();
            }
            if (key.contains("[")) {
                int start = key.indexOf("[");
                int end = key.indexOf("]");
                if (start > 0 && end > start) {
                    String arrKey = key.substring(0, start);
                    String indexStr = key.substring(start + 1, end);
                    try {
                        int index = Integer.parseInt(indexStr);
                        node = node.path(arrKey);
                        if (node.isArray() && index >= 0 && index < node.size()) {
                            node = node.get(index);
                        } else {
                            return MissingNode.getInstance();
                        }
                    } catch (NumberFormatException _) { // JDK 21 预览特性
                        return MissingNode.getInstance();
                    }
                    continue;
                }
            }
            node = node.path(key);
        }
        return node == null ? MissingNode.getInstance() : node;
    }
    // -------------------------------------------------------------------------
    // 嵌套判断：节点是否存在（即使值为 null 也算存在）
    // -------------------------------------------------------------------------
    public static boolean hasKey(JsonNode node, String path) {
        JsonNode nested = getNestedNode(node, path);
        // 🔥 修复：NullNode 表示字段存在，只有 MissingNode 才表示不存在
        return !nested.isMissingNode();
    }
    // -------------------------------------------------------------------------
    // 嵌套判断：是否有值（节点存在 且 值不为 null）
    // -------------------------------------------------------------------------
    public static boolean hasValue(JsonNode node, String path) {
        JsonNode nested = getNestedNode(node, path);
        return !nested.isMissingNode() && !nested.isNull();
    }
    // -------------------------------------------------------------------------
    // 嵌套判断：存在 且 值 == 预期 (🔥 增强数值类型比较)
    // -------------------------------------------------------------------------
    public static boolean hasKeyAndEquals(JsonNode node, String path, Object expect) {
        JsonNode nested = getNestedNode(node, path);
        if (nested.isMissingNode()) return false;
        if (expect == null) return nested.isNull();
        JsonNode expectNode = MAPPER.valueToTree(expect);
        // 🔥 增强：如果两边都是数字，按数学值比较，避免 IntNode 与 LongNode 不等的弱类型陷阱
        if (nested.isNumber() && expectNode.isNumber()) {
            return nested.decimalValue().compareTo(expectNode.decimalValue()) == 0;
        }
        return expectNode.equals(nested);
    }
    // ... (省略直接基于 Object 的重载方法、取值方法、类型判断、异常类等，与上一版完全一致)
    // -------------------------------------------------------------------------
    // 🔥🔥🔥 直接基于 Object 的判断与取值方法
    // -------------------------------------------------------------------------
    public static boolean hasKey(Object obj, String path) { return hasKey(toJsonNode(obj), path); }
    public static boolean hasValue(Object obj, String path) { return hasValue(toJsonNode(obj), path); }
    public static boolean hasKeyAndEquals(Object obj, String path, Object expect) { return hasKeyAndEquals(toJsonNode(obj), path, expect); }
    public static String getStr(JsonNode node, String path) { return getStr(node, path, EMPTY_STR); }
    public static String getStr(JsonNode node, String path, String def) {
        JsonNode n = getNestedNode(node, path);
        return n.isMissingNode() || n.isNull() ? def : n.asText(def);
    }
    public static int getInt(JsonNode node, String path) { return getInt(node, path, 0); }
    public static int getInt(JsonNode node, String path, int def) {
        JsonNode n = getNestedNode(node, path);
        return n.isMissingNode() || n.isNull() ? def : n.asInt(def);
    }
    public static long getLong(JsonNode node, String path) { return getLong(node, path, 0L); }
    public static long getLong(JsonNode node, String path, long def) {
        JsonNode n = getNestedNode(node, path);
        return n.isMissingNode() || n.isNull() ? def : n.asLong(def);
    }
    public static boolean getBool(JsonNode node, String path) { return getBool(node, path, false); }
    public static boolean getBool(JsonNode node, String path, boolean def) {
        JsonNode n = getNestedNode(node, path);
        return n.isMissingNode() || n.isNull() ? def : n.asBoolean(def);
    }
    public static String getStr(Object obj, String path, String def) { return getStr(toJsonNode(obj), path, def); }
    public static String getStr(Object obj, String path) { return getStr(obj, path, EMPTY_STR); }
    public static int getInt(Object obj, String path, int def) { return getInt(toJsonNode(obj), path, def); }
    public static int getInt(Object obj, String path) { return getInt(obj, path, 0); }
    public static long getLong(Object obj, String path, long def) { return getLong(toJsonNode(obj), path, def); }
    public static long getLong(Object obj, String path) { return getLong(obj, path, 0L); }
    public static boolean getBool(Object obj, String path, boolean def) { return getBool(toJsonNode(obj), path, def); }
    public static boolean getBool(Object obj, String path) { return getBool(obj, path, false); }
    public static boolean isObject(JsonNode node) { return node != null && node.isObject(); }
    public static boolean isArray(JsonNode node) { return node != null && node.isArray(); }
    public static class ConvertException extends RuntimeException {
        public ConvertException(String msg, Throwable cause) { super(msg, cause); }
    }
}