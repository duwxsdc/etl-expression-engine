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

public final class TypeResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(TypeResolver.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final Pattern GENERIC_TYPE_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<(.+)>\\s*$"
    );
    
    private static final Pattern SIMPLE_GENERIC_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*>\\s*$"
    );
    
    private static final Pattern MAP_GENERIC_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*<\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*,\\s*([a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)\\s*>\\s*$"
    );
    
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
    
    private TypeResolver() {
    }
    
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
    
    private static JavaType resolveNestedGenericType(String containerClass, String typeParams) {
        logger.debug("解析嵌套泛型类型: container={}, params={}", containerClass, typeParams);
        
        List<JavaType> typeArguments = parseTypeArguments(typeParams);
        Class<?> container = resolveClass(containerClass);
        
        JavaType[] typeArray = typeArguments.toArray(new JavaType[0]);
        return OBJECT_MAPPER.getTypeFactory().constructParametricType(container, typeArray);
    }
    
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
    
    public static boolean isGenericType(String typeName) {
        return typeName != null && typeName.contains("<") && typeName.contains(">");
    }
    
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
