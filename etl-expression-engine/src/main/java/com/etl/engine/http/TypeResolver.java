package com.etl.engine.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 类型解析器，用于将字符串类型描述解析为Java类型。
 * <p>
 * 该工具类支持解析简单类名、完整类名、泛型类型字符串（如"List&lt;User&gt;"、"Map&lt;String, Object&gt;"），
 * 并转换为Jackson的{@link JavaType}对象，用于JSON反序列化时的类型推断。
 * 内置了基本类型和常用包装类型的映射表，支持自动补全java.lang和java.util包前缀。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class TypeResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(TypeResolver.class);
    
    /**
     * Jackson对象映射器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 泛型类型匹配模式，匹配"Container&lt;TypeParams&gt;"格式
     */
    private static final Pattern GENERIC_TYPE_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<(.+)>\\s*$"
    );
    
    /**
     * 简单泛型类型匹配模式，匹配"Container&lt;Element&gt;"格式
     */
    private static final Pattern SIMPLE_GENERIC_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*>\\s*$"
    );
    
    /**
     * Map泛型类型匹配模式，匹配"Container&lt;Key, Value&gt;"格式
     */
    private static final Pattern MAP_GENERIC_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*,\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*>\\s*$"
    );
    
    /**
     * 基本类型名称到Class对象的映射
     */
    private static final Map<String, Class<?>> PRIMITIVE_TYPES = Map.of(
        "int", int.class,
        "long", long.class,
        "double", double.class,
        "float", float.class,
        "boolean", boolean.class,
        "char", char.class,
        "byte", byte.class,
        "short", short.class,
        "void", void.class
    );
    
    /**
     * 常用包装类型名称到Class对象的映射
     */
    private static final Map<String, Class<?>> BOXED_TYPES = Map.of(
        "Integer", Integer.class,
        "Long", Long.class,
        "Double", Double.class,
        "Float", Float.class,
        "Boolean", Boolean.class,
        "Character", Character.class,
        "Byte", Byte.class,
        "Short", Short.class,
        "String", String.class,
        "Object", Object.class
    );
    
    /**
     * 私有构造方法，防止实例化。
     */
    private TypeResolver() {
    }
    
    /**
     * 解析类型名称字符串为JavaType对象。
     * <p>
     * 支持多种格式：
     * <ul>
     *   <li>简单类型：如"String"、"Integer"</li>
     *   <li>完整类名：如"java.util.List"</li>
     *   <li>简单泛型：如"List&lt;User&gt;"</li>
     *   <li>Map泛型：如"Map&lt;String, User&gt;"</li>
     *   <li>嵌套泛型：如"List&lt;Map&lt;String, User&gt;&gt;"</li>
     * </ul>
     * </p>
     *
     * @param typeName 类型名称字符串
     * @return 解析后的JavaType对象
     * @throws IllegalArgumentException 当类型名称为空或无法解析时抛出
     */
    public static JavaType resolveType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("类型名称不能为空");
        }
        
        String trimmed = typeName.trim();
        logger.debug("解析类型: {}", trimmed);
        
        JavaType result = tryResolveGenericType(trimmed);
        if (result != null) {
            return result;
        }
        
        Class<?> rawClass = resolveClass(trimmed);
        return OBJECT_MAPPER.constructType(rawClass);
    }
    
    /**
     * 尝试解析泛型类型字符串。
     * <p>
     * 内部方法，根据正则模式匹配不同的泛型格式。
     * </p>
     *
     * @param typeName 类型名称字符串
     * @return 解析后的JavaType对象，如果不是泛型类型则返回null
     */
    private static JavaType tryResolveGenericType(String typeName) {
        Matcher mapMatcher = MAP_GENERIC_PATTERN.matcher(typeName);
        if (mapMatcher.matches()) {
            String containerClass = mapMatcher.group(1);
            String keyClass = mapMatcher.group(2);
            String valueClass = mapMatcher.group(3);
            
            logger.debug("解析Map泛型类型: container={}, key={}, value={}", 
                    containerClass, keyClass, valueClass);
            
            Class<?> container = resolveClass(containerClass);
            Class<?> key = resolveClass(keyClass);
            Class<?> value = resolveClass(valueClass);
            
            return OBJECT_MAPPER.getTypeFactory().constructMapType(
                    (Class<? extends Map>) container, key, value);
        }
        
        Matcher simpleMatcher = SIMPLE_GENERIC_PATTERN.matcher(typeName);
        if (simpleMatcher.matches()) {
            String containerClass = simpleMatcher.group(1);
            String elementClass = simpleMatcher.group(2);
            
            logger.debug("解析简单泛型类型: container={}, element={}", 
                    containerClass, elementClass);
            
            Class<?> container = resolveClass(containerClass);
            Class<?> element = resolveClass(elementClass);
            
            if (Collection.class.isAssignableFrom(container)) {
                return OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                        (Class<? extends Collection>) container, element);
            }
            
            if (Map.class.isAssignableFrom(container)) {
                return OBJECT_MAPPER.getTypeFactory().constructMapType(
                        (Class<? extends Map>) container, String.class, element);
            }
            
            return OBJECT_MAPPER.getTypeFactory().constructParametricType(
                    container, element);
        }
        
        Matcher nestedMatcher = GENERIC_TYPE_PATTERN.matcher(typeName);
        if (nestedMatcher.matches()) {
            String containerClass = nestedMatcher.group(1);
            String typeParams = nestedMatcher.group(2);
            
            if (typeParams.contains("<")) {
                return resolveNestedGenericType(containerClass, typeParams);
            }
        }
        
        return null;
    }
    
    /**
     * 解析嵌套泛型类型。
     * <p>
     * 处理类似"List&lt;Map&lt;String, User&gt;&gt;"这样的嵌套泛型类型。
     * </p>
     *
     * @param containerClass 容器类名
     * @param typeParams 类型参数字符串
     * @return 解析后的JavaType对象
     */
    private static JavaType resolveNestedGenericType(String containerClass, String typeParams) {
        logger.debug("解析嵌套泛型类型: container={}, params={}", containerClass, typeParams);
        
        List<JavaType> typeArguments = parseTypeArguments(typeParams);
        Class<?> container = resolveClass(containerClass);
        
        JavaType[] typeArray = typeArguments.toArray(new JavaType[0]);
        return OBJECT_MAPPER.getTypeFactory().constructParametricType(container, typeArray);
    }
    
    /**
     * 解析类型参数列表。
     * <p>
     * 将逗号分隔的类型参数字符串解析为JavaType列表，正确处理嵌套的尖括号。
     * </p>
     *
     * @param typeParams 类型参数字符串
     * @return JavaType列表
     */
    private static List<JavaType> parseTypeArguments(String typeParams) {
        List<JavaType> types = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        
        for (char c : typeParams.toCharArray()) {
            if (c == '<') {
                depth++;
                current.append(c);
            } else if (c == '>') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                if (!current.isEmpty()) {
                    types.add(resolveType(current.toString().trim()));
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        
        if (!current.isEmpty()) {
            types.add(resolveType(current.toString().trim()));
        }
        
        return types;
    }
    
    /**
     * 解析类名对应的Class对象。
     * <p>
     * 支持简单类名（自动补全java.lang和java.util包）、完整类名、基本类型和包装类型。
     * </p>
     *
     * @param className 类名字符串
     * @return 对应的Class对象
     * @throws IllegalArgumentException 当类名无效或类不存在时抛出
     */
    public static Class<?> resolveClass(String className) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        
        String trimmed = className.trim();
        
        Class<?> primitive = PRIMITIVE_TYPES.get(trimmed);
        if (primitive != null) {
            return primitive;
        }
        
        Class<?> boxed = BOXED_TYPES.get(trimmed);
        if (boxed != null) {
            return boxed;
        }
        
        try {
            return Class.forName(trimmed);
        } catch (ClassNotFoundException e) {
            for (Map.Entry<String, Class<?>> entry : BOXED_TYPES.entrySet()) {
                if (trimmed.equals(entry.getKey())) {
                    return entry.getValue();
                }
            }
            
            try {
                String fullName = "java.lang." + trimmed;
                return Class.forName(fullName);
            } catch (ClassNotFoundException e2) {
                try {
                    String utilName = "java.util." + trimmed;
                    return Class.forName(utilName);
                } catch (ClassNotFoundException e3) {
                    throw new IllegalArgumentException("无法加载类: " + trimmed, e);
                }
            }
        }
    }
    
    /**
     * 判断类型名称是否为泛型类型。
     *
     * @param typeName 类型名称字符串
     * @return 如果是泛型类型返回true，否则返回false
     */
    public static boolean isGenericType(String typeName) {
        return typeName != null && typeName.contains("<") && typeName.contains(">");
    }
    
    /**
     * 根据类型名称创建TypeReference对象。
     * <p>
     * 用于在需要TypeReference的场景下使用字符串类型描述。
     * </p>
     *
     * @param typeName 类型名称字符串
     * @return TypeReference对象
     * @throws IllegalArgumentException 当类型名称无效时抛出
     */
    public static TypeReference<?> createTypeReference(String typeName) {
        JavaType javaType = resolveType(typeName);
        return new TypeReference<Object>() {
            @Override
            public Type getType() {
                return javaType;
            }
        };
    }
}
