package com.etl.engine.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HTTP响应实现测试")
class HttpResponseImplTest {
    
    private HttpResponseImpl response;
    
    @BeforeEach
    void setUp() {
        String jsonData = "{\"name\":\"张三\",\"age\":30,\"items\":[1,2,3]}";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "test-value");
        
        response = new HttpResponseImpl(
            200,
            "OK",
            headers,
            jsonData.getBytes(StandardCharsets.UTF_8),
            "application/json"
        );
    }
    
    @Test
    @DisplayName("状态码判断")
    void testStatusCodes() {
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.statusMessage());
        assertTrue(response.isSuccess());
        assertFalse(response.isRedirect());
        assertFalse(response.isClientError());
        assertFalse(response.isServerError());
    }
    
    @Test
    @DisplayName("头部信息获取")
    void testHeaders() {
        Map<String, String> headers = response.headers();
        assertEquals(2, headers.size());
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("test-value", response.header("X-Custom-Header"));
    }
    
    @Test
    @DisplayName("asJava通过类名转换")
    void testAsJavaWithClassName() {
        Object result = response.asJava("java.util.Map");
        assertNotNull(result);
        assertTrue(result instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("张三", map.get("name"));
        assertEquals(30, map.get("age"));
    }
    
    @Test
    @DisplayName("asJava通过Class转换")
    void testAsJavaWithClass() {
        Map<String, Object> result = response.asJava(Map.class);
        assertNotNull(result);
        assertEquals("张三", result.get("name"));
    }
    
    @Test
    @DisplayName("asJava转换List泛型")
    void testAsJavaListGeneric() {
        String arrayData = "[1, 2, 3]";
        HttpResponseImpl arrayResponse = new HttpResponseImpl(
            200, "OK", Map.of(), arrayData.getBytes(StandardCharsets.UTF_8), "application/json"
        );
        
        Object items = arrayResponse.asJava("java.util.List<Integer>");
        assertNotNull(items);
        assertTrue(items instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Integer> list = (List<Integer>) items;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
    }
    
    @Test
    @DisplayName("response Consumer处理")
    void testResponseConsumer() {
        AtomicReference<String> extractedName = new AtomicReference<>();
        
        HttpResponse returned = response.response(resp -> {
            extractedName.set(resp.extract("name"));
        });
        
        assertEquals("张三", extractedName.get());
        assertSame(response, returned);
    }
    
    @Test
    @DisplayName("response Function处理")
    void testResponseFunction() {
        Integer age = response.response(resp -> {
            return resp.extract("age", Integer.class);
        });
        
        assertEquals(30, age);
    }
    
    @Test
    @DisplayName("extract提取字段")
    void testExtract() {
        String name = response.extract("name");
        assertEquals("张三", name);
        
        Integer age = response.extract("age");
        assertEquals(30, age);
        
        Integer firstItem = response.extract("items[0]");
        assertEquals(1, firstItem);
    }
    
    @Test
    @DisplayName("extract指定类型")
    void testExtractWithType() {
        Integer age = response.extract("age", Integer.class);
        assertEquals(30, age);
    }
    
    @Test
    @DisplayName("链式调用response和extract")
    void testChainedResponseAndExtract() {
        Map<String, Object> result = response.response(resp -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", resp.extract("name"));
            map.put("age", resp.extract("age", Integer.class));
            return map;
        });
        
        assertEquals("张三", result.get("name"));
        assertEquals(30, result.get("age"));
    }
    
    @Test
    @DisplayName("response空处理器抛出异常")
    void testResponseNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> {
            response.response((java.util.function.Consumer<HttpResponse>) null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            response.response((java.util.function.Function<HttpResponse, Object>) null);
        });
    }
    
    @Test
    @DisplayName("contentLength和contentType")
    void testContentMeta() {
        assertEquals("application/json", response.contentType());
    }
    
    @Test
    @DisplayName("错误状态码判断")
    void testErrorStatusCodes() {
        HttpResponseImpl clientError = new HttpResponseImpl(
            404, "Not Found", Map.of(), new byte[0], null
        );
        assertFalse(clientError.isSuccess());
        assertTrue(clientError.isClientError());
        
        HttpResponseImpl serverError = new HttpResponseImpl(
            500, "Internal Server Error", Map.of(), new byte[0], null
        );
        assertFalse(serverError.isSuccess());
        assertTrue(serverError.isServerError());
        
        HttpResponseImpl redirect = new HttpResponseImpl(
            302, "Found", Map.of(), new byte[0], null
        );
        assertFalse(redirect.isSuccess());
        assertTrue(redirect.isRedirect());
    }
}
