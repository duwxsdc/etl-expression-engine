package com.etl.engine.http;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("类型解析器测试")
class TypeResolverTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("解析简单类名")
    void testResolveSimpleClass() {
        JavaType type = TypeResolver.resolveType("java.lang.String");
        assertNotNull(type);
        assertEquals(String.class, type.getRawClass());
    }
    
    @Test
    @DisplayName("解析基本类型")
    void testResolvePrimitiveType() {
        JavaType intType = TypeResolver.resolveType("int");
        assertEquals(int.class, intType.getRawClass());
        
        JavaType longType = TypeResolver.resolveType("long");
        assertEquals(long.class, longType.getRawClass());
    }
    
    @Test
    @DisplayName("解析包装类型")
    void testResolveBoxedType() {
        JavaType integerType = TypeResolver.resolveType("Integer");
        assertEquals(Integer.class, integerType.getRawClass());
        
        JavaType longType = TypeResolver.resolveType("Long");
        assertEquals(Long.class, longType.getRawClass());
    }
    
    @Test
    @DisplayName("解析List泛型类型")
    void testResolveListGenericType() {
        JavaType type = TypeResolver.resolveType("java.util.List<java.lang.String>");
        assertNotNull(type);
        assertTrue(type.isCollectionLikeType());
        assertEquals(List.class, type.getRawClass());
    }
    
    @Test
    @DisplayName("解析Map泛型类型")
    void testResolveMapGenericType() {
        JavaType type = TypeResolver.resolveType("java.util.Map<java.lang.String, java.lang.Integer>");
        assertNotNull(type);
        assertTrue(type.isMapLikeType());
        assertEquals(Map.class, type.getRawClass());
    }
    
    @Test
    @DisplayName("解析简写泛型类型")
    void testResolveShortGenericType() {
        JavaType listType = TypeResolver.resolveType("List<String>");
        assertNotNull(listType);
        assertTrue(listType.isCollectionLikeType());
        
        JavaType mapType = TypeResolver.resolveType("Map<String, Integer>");
        assertNotNull(mapType);
        assertTrue(mapType.isMapLikeType());
    }
    
    @Test
    @DisplayName("解析无效类名抛出异常")
    void testResolveInvalidClass() {
        assertThrows(IllegalArgumentException.class, () -> {
            TypeResolver.resolveType("com.invalid.NonExistentClass");
        });
    }
    
    @Test
    @DisplayName("解析空类名抛出异常")
    void testResolveEmptyClass() {
        assertThrows(IllegalArgumentException.class, () -> {
            TypeResolver.resolveType("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TypeResolver.resolveType(null);
        });
    }
    
    @Test
    @DisplayName("检测泛型类型")
    void testIsGenericType() {
        assertTrue(TypeResolver.isGenericType("List<String>"));
        assertTrue(TypeResolver.isGenericType("Map<String, Integer>"));
        assertFalse(TypeResolver.isGenericType("String"));
        assertFalse(TypeResolver.isGenericType("java.lang.Integer"));
    }
}
