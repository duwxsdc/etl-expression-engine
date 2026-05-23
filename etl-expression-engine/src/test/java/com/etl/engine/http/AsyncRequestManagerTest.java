package com.etl.engine.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("异步请求管理器测试")
class AsyncRequestManagerTest {
    
    @BeforeAll
    static void setUp() {
    }
    
    @AfterAll
    static void tearDown() {
        AsyncRequestManager.cancelAll();
    }
    
    @Test
    @DisplayName("注册异步请求")
    void testRegisterRequest() {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        
        long requestId = AsyncRequestManager.registerRequest(future, "http://test.com", "GET");
        
        assertTrue(requestId > 0);
        assertTrue(AsyncRequestManager.getActiveRequestCount() > 0);
        
        AsyncRequestManager.completeRequest(requestId);
    }
    
    @Test
    @DisplayName("完成异步请求")
    void testCompleteRequest() {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        long requestId = AsyncRequestManager.registerRequest(future, "http://test.com", "POST");
        
        AsyncRequestManager.completeRequest(requestId);
        
        assertTrue(requestId > 0);
    }
    
    @Test
    @DisplayName("包装Future")
    void testWrapFuture() {
        CompletableFuture<String> original = CompletableFuture.completedFuture("test-result");
        
        CompletableFuture<String> wrapped = AsyncRequestManager.wrapFuture(original, "test-operation");
        
        assertNotNull(wrapped);
        assertTrue(wrapped.isDone());
    }
    
    @Test
    @DisplayName("带超时的Future")
    void testWithTimeout() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
                return "result";
            } catch (InterruptedException e) {
                return "interrupted";
            }
        });
        
        CompletableFuture<String> withTimeout = AsyncRequestManager.withTimeout(future, 5000);
        
        assertNotNull(withTimeout);
    }
    
    @Test
    @DisplayName("取消所有请求")
    void testCancelAll() {
        CompletableFuture<HttpResponse> future1 = new CompletableFuture<>();
        CompletableFuture<HttpResponse> future2 = new CompletableFuture<>();
        
        AsyncRequestManager.registerRequest(future1, "http://test1.com", "GET");
        AsyncRequestManager.registerRequest(future2, "http://test2.com", "POST");
        
        AsyncRequestManager.cancelAll();
        
        assertTrue(future1.isCancelled() || future1.isDone());
        assertTrue(future2.isCancelled() || future2.isDone());
    }
    
    @Test
    @DisplayName("检查是否可以创建新请求")
    void testCanCreateNewRequest() {
        boolean canCreate = AsyncRequestManager.canCreateNewRequest();
        assertTrue(canCreate || AsyncRequestManager.getActiveRequestCount() >= 100);
    }
    
    @Test
    @DisplayName("获取活跃请求数量")
    void testGetActiveRequestCount() {
        int count = AsyncRequestManager.getActiveRequestCount();
        assertTrue(count >= 0);
    }
}
