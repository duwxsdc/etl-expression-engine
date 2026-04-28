package com.etl.engine;

import com.etl.engine.context.SessionContext;
import com.etl.engine.context.SessionContextManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话上下文管理器单元测试
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionContextManagerTest {

    private SessionContextManager manager;

    @BeforeEach
    void setUp() {
        manager = new SessionContextManager();
    }

    @Test
    @DisplayName("测试创建会话")
    void testCreateSession() {
        SessionContext context = manager.createSession();

        assertNotNull(context);
        assertNotNull(context.getSessionId());
        assertTrue(context.getSessionId().startsWith("SESSION-"));
        assertEquals(0, context.getVariableCount());
        assertEquals(1, manager.getActiveSessionCount());
    }

    @Test
    @DisplayName("测试获取会话")
    void testGetSession() {
        SessionContext created = manager.createSession();
        SessionContext retrieved = manager.getSession(created.getSessionId());

        assertSame(created, retrieved);
    }

    @Test
    @DisplayName("测试销毁会话")
    void testDestroySession() {
        SessionContext context = manager.createSession();
        context.setVariable("test", 123);

        manager.destroySession(context.getSessionId());

        assertFalse(manager.hasSession(context.getSessionId()));
        assertEquals(0, manager.getActiveSessionCount());
    }

    @Test
    @DisplayName("测试会话隔离")
    void testSessionIsolation() {
        SessionContext session1 = manager.createSession();
        SessionContext session2 = manager.createSession();

        session1.setVariable("a", 100);
        session2.setVariable("a", 200);

        assertEquals(100, session1.getVariable("a"));
        assertEquals(200, session2.getVariable("a"));

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
    }

    @Test
    @DisplayName("测试多个会话")
    void testMultipleSessions() {
        SessionContext session1 = manager.createSession();
        SessionContext session2 = manager.createSession();
        SessionContext session3 = manager.createSession();

        assertEquals(3, manager.getActiveSessionCount());

        manager.destroySession(session1.getSessionId());
        assertEquals(2, manager.getActiveSessionCount());
    }

    @Test
    @DisplayName("测试变量操作")
    void testVariableOperations() {
        SessionContext context = manager.createSession();

        context.setVariable("num", 42);
        context.setVariable("str", "hello");
        context.setVariable("flag", true);

        assertEquals(42, context.getVariable("num"));
        assertEquals("hello", context.getVariable("str"));
        assertEquals(true, context.getVariable("flag"));
        assertEquals(3, context.getVariableCount());

        context.removeVariable("num");
        assertNull(context.getVariable("num"));
        assertEquals(2, context.getVariableCount());

        context.clearVariables();
        assertEquals(0, context.getVariableCount());
    }
}
