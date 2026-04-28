package com.etl.engine.context;

/**
 * 上下文持有者
 * 使用ThreadLocal实现跨组件上下文透传
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
public final class ContextHolder {

    private ContextHolder() {
    }

    private static final ThreadLocal<SessionContext> SESSION_CONTEXT = new ThreadLocal<>();

    public static void setContext(SessionContext context) {
        SESSION_CONTEXT.set(context);
    }

    public static void clearContext() {
        SESSION_CONTEXT.remove();
    }

    public static SessionContext getCurrentContext() {
        return SESSION_CONTEXT.get();
    }

    public static boolean hasContext() {
        return SESSION_CONTEXT.get() != null;
    }

    public static String getCurrentSessionId() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getSessionId() : null;
    }

    public static Object getVariable(String name) {
        SessionContext context = getCurrentContext();
        return context != null ? context.getVariable(name) : null;
    }

    public static void setVariable(String name, Object value) {
        SessionContext context = getCurrentContext();
        if (context != null) {
            context.setVariable(name, value);
        }
    }
}
