package com.etl.engine.util;

import java.util.Collection;
import java.util.Map;

/**
 * 字符串工具类
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String trim(String str) {
        return str != null ? str.trim() : null;
    }

    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    public static String toString(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof Collection<?> collection) {
            return collection.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            return map.toString();
        }
        if (obj.getClass().isArray()) {
            return arrayToString(obj);
        }
        return obj.toString();
    }

    private static String arrayToString(Object array) {
        if (array instanceof Object[] objArray) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < objArray.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(objArray[i]);
            }
            return sb.append("]").toString();
        }
        return array.toString();
    }
}
