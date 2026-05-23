package com.etl.engine.http;

import com.etl.engine.mvel.HttpFunction;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("HTTP请求引擎核心测试")
class HttpRequestEngineCoreTest {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestEngineCoreTest.class);
    private static HttpClientAdapter mockAdapter;
    
    @BeforeAll
    static void setup() {
        mockAdapter = mock(HttpClientAdapter.class);
        HttpRequestBuilderImpl.setClientAdapter(mockAdapter);
        HttpFunction.init(mockAdapter);
    }
    
    @AfterEach
    void resetMock() {
        reset(mockAdapter);
    }
    
    @AfterAll
    static void teardown() {
        HttpFunction.shutdown();
    }
    
    private HttpResponse createResponse(int statusCode, String body, Map<String, String> headers) {
        return new HttpResponseImpl(statusCode, "", headers, body.getBytes(), "application/json");
    }
    
    @Test
    @Order(1)
    @DisplayName("GET请求")
    void testGetRequest() {
        when(mockAdapter.execute(eq("GET"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{\"result\": \"ok\"}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .timeout(5000)
                .get();
        
        assertEquals(200, response.statusCode());
        assertTrue(response.isSuccess());
        verify(mockAdapter, times(1)).execute(eq("GET"), anyString(), any(), any(), eq(5000));
    }
    
    @Test
    @Order(2)
    @DisplayName("POST请求带JSON")
    void testPostJson() {
        when(mockAdapter.execute(eq("POST"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(201, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users")
                .bodyJson(Map.of("name", "test"))
                .post();
        
        assertEquals(201, response.statusCode());
    }
    
    @Test
    @Order(3)
    @DisplayName("路径参数")
    void testPathVariables() {
        when(mockAdapter.execute(eq("GET"), contains("/users/123"), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/{id}")
                .pathVariable("id", 123)
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(4)
    @DisplayName("查询参数")
    void testQueryVariables() {
        when(mockAdapter.execute(eq("GET"), contains("page=1"), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/search")
                .queryVariable("page", 1)
                .queryVariable("size", 10)
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(5)
    @DisplayName("Bearer认证")
    void testBearerAuth() {
        when(mockAdapter.execute(eq("GET"), anyString(), argThat(h -> 
                h.getOrDefault("Authorization", "").startsWith("Bearer ")), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .bearerAuth("token123")
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(6)
    @DisplayName("Basic认证")
    void testBasicAuth() {
        when(mockAdapter.execute(eq("GET"), anyString(), argThat(h -> 
                h.getOrDefault("Authorization", "").startsWith("Basic ")), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .basicAuth("user", "pass")
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(7)
    @DisplayName("PUT请求")
    void testPut() {
        when(mockAdapter.execute(eq("PUT"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/1")
                .bodyJson(Map.of("name", "updated"))
                .put();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(8)
    @DisplayName("DELETE请求")
    void testDelete() {
        when(mockAdapter.execute(eq("DELETE"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(204, "", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/1")
                .delete();
        
        assertEquals(204, response.statusCode());
    }
    
    @Test
    @Order(9)
    @DisplayName("响应解析-asString")
    void testResponseAsString() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "hello world", Map.of()));
        
        String body = HttpFunction.httpRequest("https://api.example.com/test").get().asString();
        
        assertEquals("hello world", body);
    }
    
    @Test
    @Order(10)
    @DisplayName("响应解析-asMap")
    void testResponseAsMap() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{\"key\": \"value\"}", Map.of()));
        
        Map<String, Object> map = HttpFunction.httpRequest("https://api.example.com/test").get().asMap();
        
        assertEquals("value", map.get("key"));
    }
    
    @Test
    @Order(11)
    @DisplayName("错误状态码处理")
    void testErrorStatus() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(404, "Not Found", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/notfound").get();
        
        assertEquals(404, response.statusCode());
        assertFalse(response.isSuccess());
        assertTrue(response.isClientError());
    }
    
    @Test
    @Order(12)
    @DisplayName("客户端错误不重试")
    void testClientErrorNoRetry() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(400, "Bad Request", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test").get();
        
        assertEquals(400, response.statusCode());
        verify(mockAdapter, times(1)).execute(anyString(), anyString(), any(), any(), anyInt());
    }
    
    @Test
    @Order(13)
    @DisplayName("重试机制-超时")
    void testRetryOnTimeout() {
        HttpTimeoutException timeout = new HttpTimeoutException("timeout", 5000);
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenThrow(timeout)
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .timeout(5000)
                .retry(3)
                .retry(10)
                .get();
        
        assertEquals(200, response.statusCode());
        verify(mockAdapter, times(2)).execute(anyString(), anyString(), any(), any(), anyInt());
    }
    
    @Test
    @Order(14)
    @DisplayName("超时异常")
    void testTimeoutException() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenThrow(new HttpTimeoutException("timeout", 5000));
        
        assertThrows(HttpTimeoutException.class, () -> 
                HttpFunction.httpRequest("https://api.example.com/test").timeout(5000).get());
    }
    
    @Test
    @Order(15)
    @DisplayName("解析异常")
    void testParseException() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "invalid json {{{", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test").get();
        assertThrows(HttpParseException.class, () -> response.asJson());
    }
    
    @Test
    @Order(16)
    @DisplayName("异步GET请求")
    void testAsyncGet() throws Exception {
        when(mockAdapter.execute(eq("GET"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        AsyncHttpRequest async = HttpFunction.httpRequest("https://api.example.com/test").asyncGet();
        HttpResponse response = async.get(5000);
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(17)
    @DisplayName("异步POST请求")
    void testAsyncPost() throws Exception {
        when(mockAdapter.execute(eq("POST"), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(201, "{}", Map.of()));
        
        AsyncHttpRequest async = HttpFunction.httpRequest("https://api.example.com/test")
                .bodyJson(Map.of("data", 123))
                .asyncPost();
        
        HttpResponse response = async.get();
        assertEquals(201, response.statusCode());
    }
    
    @Test
    @Order(18)
    @DisplayName("异步回调")
    void testAsyncCallback() throws Exception {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "result", Map.of()));
        
        CompletableFuture<String> future = new CompletableFuture<>();
        
        HttpFunction.httpRequest("https://api.example.com/test")
                .asyncGet()
                .thenAccept(r -> future.complete(r.asString()));
        
        assertEquals("result", future.get(5, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(19)
    @DisplayName("拦截器-请求前")
    void testInterceptorBefore() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        AtomicInteger called = new AtomicInteger(0);
        
        HttpFunction.httpRequest("https://api.example.com/test")
                .interceptor(new HttpInterceptor() {
                    @Override
                    public boolean beforeRequest(HttpRequestContext ctx) {
                        called.incrementAndGet();
                        assertEquals("GET", ctx.getMethod());
                        return true;
                    }
                })
                .get();
        
        assertEquals(1, called.get());
    }
    
    @Test
    @Order(20)
    @DisplayName("拦截器-请求后")
    void testInterceptorAfter() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        AtomicInteger called = new AtomicInteger(0);
        
        HttpFunction.httpRequest("https://api.example.com/test")
                .interceptor(new HttpInterceptor() {
                    @Override
                    public void afterResponse(HttpRequestContext ctx, HttpResponse resp) {
                        called.incrementAndGet();
                        assertEquals(200, resp.statusCode());
                    }
                })
                .get();
        
        assertEquals(1, called.get());
    }
    
    @Test
    @Order(21)
    @DisplayName("拦截器-拒绝请求")
    void testInterceptorReject() {
        assertThrows(HttpException.class, () -> 
                HttpFunction.httpRequest("https://api.example.com/test")
                        .interceptor(new HttpInterceptor() {
                            @Override
                            public boolean beforeRequest(HttpRequestContext ctx) {
                                return false;
                            }
                        })
                        .get());
        
        verify(mockAdapter, never()).execute(anyString(), anyString(), any(), any(), anyInt());
    }
    
    @Test
    @Order(22)
    @DisplayName("多个拦截器顺序")
    void testMultipleInterceptors() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        AtomicInteger order = new AtomicInteger(0);
        AtomicInteger first = new AtomicInteger(0);
        AtomicInteger second = new AtomicInteger(0);
        
        HttpFunction.httpRequest("https://api.example.com/test")
                .interceptor(new HttpInterceptor() {
                    @Override
                    public boolean beforeRequest(HttpRequestContext ctx) {
                        first.set(order.incrementAndGet());
                        return true;
                    }
                })
                .interceptor(new HttpInterceptor() {
                    @Override
                    public boolean beforeRequest(HttpRequestContext ctx) {
                        second.set(order.incrementAndGet());
                        return true;
                    }
                })
                .get();
        
        assertEquals(1, first.get());
        assertEquals(2, second.get());
    }
    
    @Test
    @Order(23)
    @DisplayName("并发同步请求")
    void testConcurrentSync() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(inv -> {
            count.incrementAndGet();
            Thread.sleep(5);
            return createResponse(200, "concurrent-" + count.get(), Map.of());
        });
        
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    HttpResponse r = HttpFunction.httpRequest("https://api.example.com/c" + Thread.currentThread().getId()).get();
                    assertEquals(200, r.statusCode());
                } catch (Exception e) {
                    fail(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(threads, count.get());
        executor.shutdown();
    }
    
    @Test
    @Order(24)
    @DisplayName("并发异步请求")
    void testConcurrentAsync() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(inv -> {
            count.incrementAndGet();
            Thread.sleep(10);
            return createResponse(200, "async-" + count.get(), Map.of());
        });
        
        int requests = 20;
        CompletableFuture<?>[] futures = new CompletableFuture[requests];
        
        for (int i = 0; i < requests; i++) {
            futures[i] = HttpFunction.httpRequest("https://api.example.com/a" + i)
                    .asyncGet()
                    .future();
        }
        
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        assertEquals(requests, count.get());
    }
    
    @Test
    @Order(25)
    @DisplayName("Builder隔离")
    void testBuilderIsolation() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpRequestBuilder b1 = HttpFunction.httpRequest("https://api.example.com/1").header("X-Req", "1");
        HttpRequestBuilder b2 = HttpFunction.httpRequest("https://api.example.com/2").header("X-Req", "2");
        
        b1.get();
        b2.get();
        
        verify(mockAdapter, times(2)).execute(anyString(), anyString(), any(), any(), anyInt());
    }
    
    @Test
    @Order(26)
    @DisplayName("复杂链式配置")
    void testComplexChaining() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/{ver}/users/{id}")
                .pathVariable("ver", "v1")
                .pathVariable("id", 123)
                .queryVariable("expand", "profile")
                .header("X-Api-Key", "key123")
                .bearerAuth("token")
                .timeout(10000)
                .retry(2)
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(27)
    @DisplayName("表单数据")
    void testFormData() {
        when(mockAdapter.execute(eq("POST"), anyString(), argThat(h -> 
                "application/x-www-form-urlencoded".equals(h.get("Content-Type"))), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/login")
                .bodyForm(Map.of("user", "admin", "pass", "123456"))
                .post();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(28)
    @DisplayName("零超时")
    void testZeroTimeout() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), eq(0)))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .timeout(0)
                .get();
        
        assertEquals(200, response.statusCode());
    }
    
    @Test
    @Order(29)
    @DisplayName("零重试")
    void testZeroRetry() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                .retry(0)
                .get();
        
        assertEquals(200, response.statusCode());
        verify(mockAdapter, times(1)).execute(anyString(), anyString(), any(), any(), anyInt());
    }
    
    @Test
    @Order(30)
    @DisplayName("特殊字符路径")
    void testSpecialCharsInPath() {
        when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(createResponse(200, "{}", Map.of()));
        
        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/{name}")
                .pathVariable("name", "john doe")
                .get();
        
        assertEquals(200, response.statusCode());
    }
}
