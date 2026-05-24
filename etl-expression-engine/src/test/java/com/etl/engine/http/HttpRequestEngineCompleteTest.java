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
@DisplayName("HTTP请求引擎完整测试套件")
class HttpRequestEngineCompleteTest {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestEngineCompleteTest.class);
    private static HttpClientAdapter mockAdapter;
    
    @BeforeAll
    static void setup() {
        mockAdapter = mock(HttpClientAdapter.class);
        HttpRequestBuilderImpl.setClientAdapter(mockAdapter);
        HttpFunction.init(mockAdapter);
    }
    
    @AfterAll
    static void teardown() {
        // 不在此处shutdown，避免影响@Nested内部类的异步测试
        // HttpFunction.shutdown();
        HttpRequestBuilderImpl.setClientAdapter(null);
    }
    
    @AfterEach
    void resetMock() {
        reset(mockAdapter);
    }
    
    private HttpResponse createMockResponse(int statusCode, String body, Map<String, String> headers) {
        return new HttpResponseImpl(statusCode, "", headers, body.getBytes(), "application/json");
    }
    
    // ==================== 同步请求测试 ====================
    
    @Nested
    @DisplayName("同步请求功能测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SyncRequestTests {
        
        @Test
        @Order(1)
        @DisplayName("基础GET请求")
        void testBasicGetRequest() {
            HttpResponse mockResponse = createMockResponse(200, "{\"result\": \"success\"}", Map.of("Content-Type", "application/json"));
            when(mockAdapter.execute(eq("GET"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .timeout(5000)
                    .get();
            
            assertNotNull(response);
            assertEquals(200, response.statusCode());
            assertTrue(response.isSuccess());
            assertTrue(response.asString().contains("success"));
            verify(mockAdapter, times(1)).execute(eq("GET"), contains("/test"), any(), any(), eq(5000));
        }
        
        @Test
        @Order(2)
        @DisplayName("POST请求带JSON Body")
        void testPostWithJsonBody() {
            HttpResponse mockResponse = createMockResponse(201, "{\"id\": 123}", Map.of("Content-Type", "application/json"));
            when(mockAdapter.execute(eq("POST"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users")
                    .bodyJson(Map.of("name", "test", "age", 25))
                    .post();
            
            assertNotNull(response);
            assertEquals(201, response.statusCode());
            verify(mockAdapter, times(1)).execute(eq("POST"), contains("/users"), any(), any(), anyInt());
        }
        
        @Test
        @Order(3)
        @DisplayName("PUT请求")
        void testPutRequest() {
            HttpResponse mockResponse = createMockResponse(200, "{\"updated\": true}", Map.of());
            when(mockAdapter.execute(eq("PUT"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/123")
                    .bodyJson(Map.of("name", "updated"))
                    .put();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(4)
        @DisplayName("DELETE请求")
        void testDeleteRequest() {
            HttpResponse mockResponse = createMockResponse(204, "", Map.of());
            when(mockAdapter.execute(eq("DELETE"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/123")
                    .delete();
            
            assertEquals(204, response.statusCode());
        }
        
        @Test
        @Order(5)
        @DisplayName("PATCH请求")
        void testPatchRequest() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("PATCH"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/123")
                    .bodyJson(Map.of("status", "active"))
                    .patch();
            
            assertEquals(200, response.statusCode());
        }
    }
    
    // ==================== 请求配置测试 ====================
    
    @Nested
    @DisplayName("请求配置功能测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RequestConfigTests {
        
        @Test
        @Order(1)
        @DisplayName("路径参数替换")
        void testPathVariables() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("GET"), contains("/users/123/posts/456"), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/{userId}/posts/{postId}")
                    .pathVariable("userId", 123)
                    .pathVariable("postId", 456)
                    .get();
            
            assertEquals(200, response.statusCode());
            verify(mockAdapter).execute(eq("GET"), contains("/users/123/posts/456"), any(), any(), anyInt());
        }
        
        @Test
        @Order(2)
        @DisplayName("查询参数拼接")
        void testQueryVariables() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), contains("param1=value1"), any(), any(), anyInt())).thenReturn(mockResponse);
            when(mockAdapter.execute(anyString(), contains("param2=test"), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/search")
                    .queryVariable("param1", "value1")
                    .queryVariable("param2", "test")
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(3)
        @DisplayName("自定义请求头")
        void testCustomHeaders() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("GET"), anyString(), argThat(headers -> 
                    headers.containsKey("X-Custom-Header") && 
                    "custom-value".equals(headers.get("X-Custom-Header"))), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .header("X-Custom-Header", "custom-value")
                    .header("Authorization", "Bearer token123")
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(4)
        @DisplayName("Bearer认证")
        void testBearerAuth() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("GET"), anyString(), argThat(headers -> 
                    headers.containsKey("Authorization") && 
                    headers.get("Authorization").startsWith("Bearer ")), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .bearerAuth("my-secret-token")
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(5)
        @DisplayName("Basic认证")
        void testBasicAuth() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("GET"), anyString(), argThat(headers -> 
                    headers.containsKey("Authorization") && 
                    headers.get("Authorization").startsWith("Basic ")), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .basicAuth("user", "password")
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(6)
        @DisplayName("表单数据请求")
        void testFormData() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(eq("POST"), anyString(), argThat(headers -> 
                    "application/x-www-form-urlencoded".equals(headers.get("Content-Type"))), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/login")
                    .bodyForm(Map.of("username", "test", "password", "pass123"))
                    .post();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(7)
        @DisplayName("复杂链式配置")
        void testComplexChaining() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/{version}/users/{id}")
                    .pathVariable("version", "v1")
                    .pathVariable("id", 123)
                    .queryVariable("include", "profile")
                    .queryVariable("fields", "name,email")
                    .header("X-Request-ID", "req-123")
                    .header("X-API-Key", "key-456")
                    .bearerAuth("token789")
                    .timeout(10000)
                    .retry(2)
                    .get();
            
            assertEquals(200, response.statusCode());
        }
    }
    
    // ==================== 响应解析测试 ====================
    
    @Nested
    @DisplayName("响应解析功能测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ResponseParsingTests {
        
        @Test
        @Order(1)
        @DisplayName("获取响应状态码")
        void testGetStatusCode() {
            HttpResponse mockResponse = createMockResponse(200, "", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            int statusCode = HttpFunction.httpRequest("https://api.example.com/test")
                    .get()
                    .statusCode();
            
            assertEquals(200, statusCode);
        }
        
        @Test
        @Order(2)
        @DisplayName("成功状态判断")
        void testSuccessCheck() {
            HttpResponse successResponse = createMockResponse(200, "{}", Map.of());
            HttpResponse errorResponse = createMockResponse(500, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenReturn(successResponse)
                    .thenReturn(errorResponse);
            
            assertTrue(HttpFunction.httpRequest("https://api.example.com/success").get().isSuccess());
            assertFalse(HttpFunction.httpRequest("https://api.example.com/error").get().isSuccess());
        }
        
        @Test
        @Order(3)
        @DisplayName("响应头获取")
        void testResponseHeaders() {
            Map<String, String> headers = Map.of("Content-Type", "application/json", "X-Custom", "value");
            HttpResponse mockResponse = createMockResponse(200, "{}", headers);
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test").get();
            
            assertEquals("application/json", response.header("Content-Type"));
            assertEquals("value", response.header("X-Custom"));
            assertEquals(2, response.headers().size());
        }
        
        @Test
        @Order(4)
        @DisplayName("原始字符串响应")
        void testAsString() {
            HttpResponse mockResponse = createMockResponse(200, "plain text response", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            String body = HttpFunction.httpRequest("https://api.example.com/test").get().asString();
            
            assertEquals("plain text response", body);
        }
        
        @Test
        @Order(5)
        @DisplayName("多次解析同一响应")
        void testMultipleParsing() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test").get();
            
            String str1 = response.asString();
            String str2 = response.asString();
            
            assertEquals(str1, str2);
            assertSame(str1, str2);
        }
    }
    
    // ==================== 异步请求测试 ====================
    
    @Nested
    @DisplayName("异步请求功能测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AsyncRequestTests {
        
        @Test
        @Order(1)
        @DisplayName("异步GET请求")
        void testAsyncGet() throws Exception {
            HttpResponse mockResponse = createMockResponse(200, "{\"async\": true}", Map.of());
            when(mockAdapter.execute(eq("GET"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            AsyncHttpRequest asyncRequest = HttpFunction.httpRequest("https://api.example.com/async")
                    .asyncGet();
            
            HttpResponse response = asyncRequest.get(5000);
            
            assertNotNull(response);
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(2)
        @DisplayName("异步POST请求")
        void testAsyncPost() throws Exception {
            HttpResponse mockResponse = createMockResponse(201, "{\"created\": true}", Map.of());
            when(mockAdapter.execute(eq("POST"), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            AsyncHttpRequest asyncRequest = HttpFunction.httpRequest("https://api.example.com/users")
                    .bodyJson(Map.of("name", "async-user"))
                    .asyncPost();
            
            HttpResponse response = asyncRequest.get();
            
            assertEquals(201, response.statusCode());
        }
        
        @Test
        @Order(3)
        @DisplayName("异步请求回调处理")
        void testAsyncCallback() throws Exception {
            HttpResponse mockResponse = createMockResponse(200, "callback-result", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            
            HttpFunction.httpRequest("https://api.example.com/test")
                    .asyncGet()
                    .thenAccept(response -> {
                        resultFuture.complete(response.asString());
                    });
            
            String result = resultFuture.get(5, TimeUnit.SECONDS);
            assertEquals("callback-result", result);
        }
        
        @Test
        @Order(4)
        @DisplayName("异步请求超时")
        void testAsyncTimeout() {
            HttpResponse mockResponse = createMockResponse(200, "slow-response", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(invocation -> {
                Thread.sleep(100);
                return mockResponse;
            });
            
            AsyncHttpRequest asyncRequest = HttpFunction.httpRequest("https://api.example.com/slow")
                    .timeout(50)
                    .asyncGet();
            
            assertThrows(HttpTimeoutException.class, () -> asyncRequest.get(100));
        }
    }
    
    // ==================== 重试机制测试 ====================
    
    @Nested
    @DisplayName("重试机制测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RetryTests {
        
        @Test
        @Order(1)
        @DisplayName("成功请求不重试")
        void testNoRetryOnSuccess() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpFunction.httpRequest("https://api.example.com/test")
                    .retry(3)
                    .get();
            
            verify(mockAdapter, times(1)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(2)
        @DisplayName("服务器错误触发重试")
        void testRetryOnServerError() {
            HttpResponse errorResponse = createMockResponse(500, "Server Error", Map.of());
            HttpResponse successResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenReturn(errorResponse)
                    .thenReturn(errorResponse)
                    .thenReturn(successResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .retry(3)
                    .retry(10)
                    .get();
            
            assertEquals(200, response.statusCode());
            verify(mockAdapter, times(3)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(3)
        @DisplayName("客户端错误不重试")
        void testNoRetryOnClientError() {
            HttpResponse errorResponse = createMockResponse(400, "Bad Request", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(errorResponse);
            
            assertThrows(HttpException.class, () -> 
                    HttpFunction.httpRequest("https://api.example.com/test")
                            .retry(3)
                            .get());
            
            verify(mockAdapter, times(1)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(4)
        @DisplayName("超时错误触发重试")
        void testRetryOnTimeout() {
            HttpTimeoutException timeoutException = new HttpTimeoutException("timeout", 1000);
            HttpResponse successResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenThrow(timeoutException)
                    .thenReturn(successResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .retry(3)
                    .retry(10)
                    .timeout(1000)
                    .get();
            
            assertEquals(200, response.statusCode());
            verify(mockAdapter, times(2)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
    }
    
    // ==================== 异常处理测试 ====================
    
    @Nested
    @DisplayName("异常处理测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExceptionHandlingTests {
        
        @Test
        @Order(1)
        @DisplayName("连接异常")
        void testConnectionException() {
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenThrow(new HttpException("Connection refused", -1, "https://api.example.com"));
            
            HttpException exception = assertThrows(HttpException.class, () -> 
                    HttpFunction.httpRequest("https://api.example.com/test").get());
            
            assertTrue(exception.getMessage().contains("Connection refused"));
        }
        
        @Test
        @Order(2)
        @DisplayName("超时异常")
        void testTimeoutException() {
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenThrow(new HttpTimeoutException("Request timeout", 5000));
            
            HttpTimeoutException exception = assertThrows(HttpTimeoutException.class, () -> 
                    HttpFunction.httpRequest("https://api.example.com/test").timeout(5000).get());
            
            assertEquals(5000, exception.getTimeoutMillis());
        }
        
        @Test
        @Order(3)
        @DisplayName("解析异常")
        void testParseException() {
            HttpResponse mockResponse = createMockResponse(200, "invalid json {{{", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test").get();
            
            assertThrows(HttpParseException.class, () -> response.asJson());
        }
        
        @Test
        @Order(4)
        @DisplayName("错误状态码")
        void testErrorStatusCode() {
            HttpResponse mockResponse = createMockResponse(404, "Not Found", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/notfound").get();
            
            assertEquals(404, response.statusCode());
            assertFalse(response.isSuccess());
            assertTrue(response.isClientError());
        }
    }
    
    // ==================== 并发请求测试 ====================
    
    @Nested
    @DisplayName("并发请求测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConcurrencyTests {
        
        @Test
        @Order(1)
        @DisplayName("并发同步请求")
        void testConcurrentSyncRequests() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                Thread.sleep(10);
                return createMockResponse(200, "concurrent-" + callCount.get(), Map.of());
            });
            
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        HttpResponse response = HttpFunction.httpRequest("https://api.example.com/concurrent-" + Thread.currentThread().getId())
                                .get();
                        assertEquals(200, response.statusCode());
                    } catch (Exception e) {
                        fail("Request failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(threadCount, callCount.get());
            executor.shutdown();
        }
        
        @Test
        @Order(2)
        @DisplayName("并发异步请求")
        void testConcurrentAsyncRequests() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                Thread.sleep(20);
                return createMockResponse(200, "async-" + callCount.get(), Map.of());
            });
            
            int requestCount = 20;
            CompletableFuture<HttpResponse>[] futures = new CompletableFuture[requestCount];
            
            for (int i = 0; i < requestCount; i++) {
                final int index = i;
                futures[i] = HttpFunction.httpRequest("https://api.example.com/async-" + index)
                        .asyncGet()
                        .future();
            }
            
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            
            assertEquals(requestCount, callCount.get());
        }
        
        @Test
        @Order(3)
        @DisplayName("同一Builder实例不共享状态")
        void testBuilderIsolation() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpRequestBuilder builder1 = HttpFunction.httpRequest("https://api.example.com/first")
                    .header("X-Request", "request-1");
            
            HttpRequestBuilder builder2 = HttpFunction.httpRequest("https://api.example.com/second")
                    .header("X-Request", "request-2");
            
            builder1.get();
            builder2.get();
            
            verify(mockAdapter, times(2)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(4)
        @DisplayName("并发请求不同URL")
        void testConcurrentDifferentUrls() throws Exception {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), contains("/a"), any(), any(), anyInt()))
                    .thenReturn(createMockResponse(200, "response-a", Map.of()));
            when(mockAdapter.execute(anyString(), contains("/b"), any(), any(), anyInt()))
                    .thenReturn(createMockResponse(200, "response-b", Map.of()));
            when(mockAdapter.execute(anyString(), contains("/c"), any(), any(), anyInt()))
                    .thenReturn(createMockResponse(200, "response-c", Map.of()));
            
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch latch = new CountDownLatch(3);
            
            for (String url : new String[]{"https://api.example.com/a", "https://api.example.com/b", "https://api.example.com/c"}) {
                executor.submit(() -> {
                    try {
                        String body = HttpFunction.httpRequest(url).get().asString();
                        assertNotNull(body);
                    } catch (Exception e) {
                        fail("Request failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();
        }
    }
    
    // ==================== 边界条件测试 ====================
    
    @Nested
    @DisplayName("边界条件测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BoundaryTests {
        
        @Test
        @Order(1)
        @DisplayName("空URL")
        void testEmptyUrl() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("").get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(2)
        @DisplayName("空Body")
        void testEmptyBody() {
            HttpResponse mockResponse = createMockResponse(200, "", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), isNull(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .body(null)
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(3)
        @DisplayName("零超时")
        void testZeroTimeout() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), eq(0))).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .timeout(0)
                    .get();
            
            assertEquals(200, response.statusCode());
        }
        
        @Test
        @Order(4)
        @DisplayName("零重试次数")
        void testZeroRetry() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/test")
                    .retry(0)
                    .get();
            
            assertEquals(200, response.statusCode());
            verify(mockAdapter, times(1)).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(5)
        @DisplayName("特殊字符路径参数")
        void testSpecialCharsInPath() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            HttpResponse response = HttpFunction.httpRequest("https://api.example.com/users/{name}")
                    .pathVariable("name", "john doe")
                    .get();
            
            assertEquals(200, response.statusCode());
        }
    }
    
    // ==================== 拦截器测试 ====================
    
    @Nested
    @DisplayName("拦截器测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InterceptorTests {
        
        @Test
        @Order(1)
        @DisplayName("请求前拦截器")
        void testBeforeRequestInterceptor() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            AtomicInteger beforeCalled = new AtomicInteger(0);
            
            HttpFunction.httpRequest("https://api.example.com/test")
                    .interceptor(new HttpInterceptor() {
                        @Override
                        public boolean beforeRequest(HttpRequestContext context) {
                            beforeCalled.incrementAndGet();
                            assertEquals("GET", context.getMethod());
                            return true;
                        }
                    })
                    .get();
            
            assertEquals(1, beforeCalled.get());
        }
        
        @Test
        @Order(2)
        @DisplayName("请求后拦截器")
        void testAfterResponseInterceptor() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            AtomicInteger afterCalled = new AtomicInteger(0);
            
            HttpFunction.httpRequest("https://api.example.com/test")
                    .interceptor(new HttpInterceptor() {
                        @Override
                        public void afterResponse(HttpRequestContext context, HttpResponse response) {
                            afterCalled.incrementAndGet();
                            assertEquals(200, response.statusCode());
                        }
                    })
                    .get();
            
            assertEquals(1, afterCalled.get());
        }
        
        @Test
        @Order(3)
        @DisplayName("错误拦截器")
        void testErrorInterceptor() {
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt()))
                    .thenThrow(new HttpException("Test error", 500, "url"));
            
            AtomicInteger errorCalled = new AtomicInteger(0);
            
            assertThrows(HttpException.class, () -> 
                    HttpFunction.httpRequest("https://api.example.com/test")
                            .interceptor(new HttpInterceptor() {
                                @Override
                                public void onError(HttpRequestContext context, Exception error) {
                                    errorCalled.incrementAndGet();
                                }
                            })
                            .retry(0)
                            .get());
            
            assertEquals(1, errorCalled.get());
        }
        
        @Test
        @Order(4)
        @DisplayName("拒绝请求的拦截器")
        void testRejectingInterceptor() {
            assertThrows(HttpException.class, () -> 
                    HttpFunction.httpRequest("https://api.example.com/test")
                            .interceptor(new HttpInterceptor() {
                                @Override
                                public boolean beforeRequest(HttpRequestContext context) {
                                    return false;
                                }
                            })
                            .get());
            
            verify(mockAdapter, never()).execute(anyString(), anyString(), any(), any(), anyInt());
        }
        
        @Test
        @Order(5)
        @DisplayName("多个拦截器顺序执行")
        void testMultipleInterceptors() {
            HttpResponse mockResponse = createMockResponse(200, "{}", Map.of());
            when(mockAdapter.execute(anyString(), anyString(), any(), any(), anyInt())).thenReturn(mockResponse);
            
            AtomicInteger order = new AtomicInteger(0);
            AtomicInteger first = new AtomicInteger(-1);
            AtomicInteger second = new AtomicInteger(-1);
            
            HttpFunction.httpRequest("https://api.example.com/test")
                    .interceptor(new HttpInterceptor() {
                        @Override
                        public boolean beforeRequest(HttpRequestContext context) {
                            first.set(order.incrementAndGet());
                            return true;
                        }
                    })
                    .interceptor(new HttpInterceptor() {
                        @Override
                        public boolean beforeRequest(HttpRequestContext context) {
                            second.set(order.incrementAndGet());
                            return true;
                        }
                    })
                    .get();
            
            assertEquals(1, first.get());
            assertEquals(2, second.get());
        }
    }
}
