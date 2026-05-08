package com.etl.engine.context;

import java.lang.ScopedValue;

/**
 * ETL上下文作用域工具类
 * 使用ScopedValue替代ThreadLocal，适配虚拟线程
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
public final class EtlContextScope {
    
    /**
     * 当前会话上下文的ScopedValue
     */
    public static final ScopedValue<EtlContext> CURRENT_CONTEXT = ScopedValue.newInstance();
    
    private EtlContextScope() {}
    
    /**
     * 获取当前会话上下文
     * @return 当前会话上下文，如果不存在则返回null
     */
    public static EtlContext getCurrentContext() {
        if (CURRENT_CONTEXT.isBound()) {
            return CURRENT_CONTEXT.get();
        }
        return null;
    }
    
    /**
     * 检查是否存在当前会话上下文
     * @return 是否存在当前会话上下文
     */
    public static boolean hasCurrentContext() {
        return CURRENT_CONTEXT.isBound();
    }
    
    /**
     * 获取当前会话ID
     * @return 当前会话ID，如果不存在则返回null
     */
    public static String getCurrentSessionId() {
        EtlContext context = getCurrentContext();
        return context != null ? context.getSessionId() : null;
    }
    
    /**
     * 获取当前上下文中的变量
     * @param name 变量名
     * @return 变量值，如果不存在则返回null
     */
    public static Object getVariable(String name) {
        EtlContext context = getCurrentContext();
        return context != null ? context.getVariable(name) : null;
    }
    
    /**
     * 设置当前上下文中的变量
     * @param name 变量名
     * @param value 变量值
     */
    public static void setVariable(String name, Object value) {
        EtlContext context = getCurrentContext();
        if (context != null) {
            context.setVariable(name, value);
        }
    }
}
