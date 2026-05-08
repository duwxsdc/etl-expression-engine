package com.etl.engine.context;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

/**
 * ETL上下文类
 * 可序列化，支持存入Redis
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
public class EtlContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String sessionId;
    private final Map<String, Object> variables;
    private final long createTime;
    private volatile long lastAccessTime;
    
    public EtlContext(String sessionId) {
        this.sessionId = sessionId;
        this.variables = new ConcurrentHashMap<>();
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = this.createTime;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setVariable(String name, Object value) {
        variables.put(name, value);
        updateAccessTime();
    }
    
    public Object getVariable(String name) {
        updateAccessTime();
        return variables.get(name);
    }
    
    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }
    
    public void removeVariable(String name) {
        variables.remove(name);
        updateAccessTime();
    }
    
    public Map<String, Object> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }
    
    public void clearVariables() {
        variables.clear();
        updateAccessTime();
    }
    
    public int getVariableCount() {
        return variables.size();
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    private void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "EtlContext{sessionId='%s', variables=%d, createTime=%d, lastAccessTime=%d}"
                .formatted(sessionId, variables.size(), createTime, lastAccessTime);
    }
}
