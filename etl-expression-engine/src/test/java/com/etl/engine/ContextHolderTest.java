package com.etl.engine;

import com.etl.engine.context.ContextHolder;
import com.etl.engine.context.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ThreadLocal上下文透传测试
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
class ContextHolderTest {

    @Test
    @DisplayName("测试ThreadLocal绑定和获取")
    void testThreadLocalBinding() {
        SessionContext context = new SessionContext("TEST-123");
        
        try {
            ContextHolder.setContext(context);
            assertTrue(ContextHolder.hasContext());
            assertEquals("TEST-123", ContextHolder.getCurrentSessionId());
            assertSame(context, ContextHolder.getCurrentContext());
        } finally {
            ContextHolder.clearContext();
        }
        
        assertFalse(ContextHolder.hasContext());
    }

    @Test
    @DisplayName("测试ThreadLocal变量操作")
    void testThreadLocalVariableOperations() {
        SessionContext context = new SessionContext("TEST-456");
        
        try {
            ContextHolder.setContext(context);
            ContextHolder.setVariable("test", 100);
            assertEquals(100, ContextHolder.getVariable("test"));
            assertEquals(100, context.getVariable("test"));
        } finally {
            ContextHolder.clearContext();
        }
        
        assertEquals(100, context.getVariable("test"));
    }

    @Test
    @DisplayName("测试无绑定情况")
    void testNoBinding() {
        assertFalse(ContextHolder.hasContext());
        assertNull(ContextHolder.getCurrentContext());
        assertNull(ContextHolder.getCurrentSessionId());
    }

    @Test
    @DisplayName("测试多线程隔离")
    void testThreadIsolation() throws InterruptedException {
        SessionContext context1 = new SessionContext("THREAD-1");
        SessionContext context2 = new SessionContext("THREAD-2");
        
        Thread t1 = new Thread(() -> {
            try {
                ContextHolder.setContext(context1);
                ContextHolder.setVariable("var", "value1");
                Thread.sleep(50);
                assertEquals("value1", ContextHolder.getVariable("var"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ContextHolder.clearContext();
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                ContextHolder.setContext(context2);
                ContextHolder.setVariable("var", "value2");
                Thread.sleep(50);
                assertEquals("value2", ContextHolder.getVariable("var"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ContextHolder.clearContext();
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        assertEquals("value1", context1.getVariable("var"));
        assertEquals("value2", context2.getVariable("var"));
    }
}
