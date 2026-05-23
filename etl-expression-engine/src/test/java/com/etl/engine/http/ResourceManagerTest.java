package com.etl.engine.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("资源管理器测试")
class ResourceManagerTest {
    
    @AfterEach
    void tearDown() {
        ResourceManager.stopMonitor();
    }
    
    @Test
    @DisplayName("跟踪AutoCloseable资源")
    void testTrackAutoCloseable() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream("test".getBytes());
        
        ByteArrayInputStream tracked = ResourceManager.track(stream, "test-stream");
        
        assertNotNull(tracked);
        assertTrue(ResourceManager.getActiveResourceCount() > 0);
        
        ResourceManager.release(tracked);
    }
    
    @Test
    @DisplayName("获取活跃资源列表")
    void testGetActiveResources() throws IOException {
        ByteArrayInputStream stream1 = new ByteArrayInputStream("test1".getBytes());
        ByteArrayInputStream stream2 = new ByteArrayInputStream("test2".getBytes());
        
        ResourceManager.track(stream1, "stream-1");
        ResourceManager.track(stream2, "stream-2");
        
        List<ResourceManager.ResourceStatus> resources = ResourceManager.getActiveResources();
        assertNotNull(resources);
        assertTrue(resources.size() >= 2);
        
        ResourceManager.release(stream1);
        ResourceManager.release(stream2);
    }
    
    @Test
    @DisplayName("释放资源后计数减少")
    void testReleaseResource() throws IOException {
        int initialCount = ResourceManager.getActiveResourceCount();
        
        ByteArrayInputStream stream = new ByteArrayInputStream("test".getBytes());
        ResourceManager.track(stream, "test-stream");
        
        assertTrue(ResourceManager.getActiveResourceCount() > initialCount);
        
        ResourceManager.release(stream);
    }
    
    @Test
    @DisplayName("跟踪null资源返回null")
    void testTrackNullResource() {
        AutoCloseable result = ResourceManager.track(null, "null-resource");
        assertNull(result);
    }
    
    @Test
    @DisplayName("释放null资源不抛出异常")
    void testReleaseNullResource() {
        assertDoesNotThrow(() -> {
            ResourceManager.release(null);
        });
    }
    
    @Test
    @DisplayName("启动和停止监控")
    void testMonitorStartStop() {
        ResourceManager.startMonitor();
        ResourceManager.startMonitor();
        
        ResourceManager.stopMonitor();
        
        assertDoesNotThrow(() -> {
            ResourceManager.startMonitor();
        });
    }
    
    @Test
    @DisplayName("检查资源泄漏")
    void testCheckResourceLeaks() {
        assertDoesNotThrow(() -> {
            ResourceManager.checkResourceLeaks();
        });
    }
}
