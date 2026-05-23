package com.etl.engine.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

public class EtlContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(EtlContext.class);
    
    private String sessionId;
    private Map<String, Object> variables;
    private long createTime;
    private long lastAccessTime;
    
    public EtlContext() {
        this.variables = new ConcurrentHashMap<>();
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = this.createTime;
        logger.debug("[EtlContext] 无参构造函数被调用, createTime={}", createTime);
    }
    
    public EtlContext(String sessionId) {
        this.sessionId = sessionId;
        this.variables = new ConcurrentHashMap<>();
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = this.createTime;
        logger.debug("[EtlContext] 有参构造函数被调用, sessionId={}, createTime={}", sessionId, createTime);
    }
    
    @JsonCreator
    public EtlContext(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("variables") Map<String, Object> variables,
            @JsonProperty("createTime") long createTime,
            @JsonProperty("lastAccessTime") long lastAccessTime) {
        this.sessionId = sessionId;
        this.variables = new ConcurrentHashMap<>(variables != null ? variables : new HashMap<>());
        this.createTime = createTime;
        this.lastAccessTime = lastAccessTime;
        logger.info("[EtlContext] @JsonCreator反序列化被调用 <== 从Redis读取, sessionId={}, variables={}, createTime={}, lastAccessTime={}", 
            sessionId, variables, createTime, lastAccessTime);
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public void setVariable(String name, Object value) {
        variables.put(name, value);
        updateAccessTime();
        logger.debug("[EtlContext] setVariable: {} = {}", name, value);
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
    
    public Map<String, Object> getVariables() {
        Map<String, Object> result = new HashMap<>(variables);
        logger.info("[EtlContext] getVariables被调用 ==> 序列化到Redis, sessionId={}, variables={}, count={}", 
            sessionId, result, result.size());
        return result;
    }
    
    public void setVariables(Map<String, Object> variables) {
        this.variables = new ConcurrentHashMap<>(variables != null ? variables : new HashMap<>());
        logger.info("[EtlContext] setVariables被调用 <== 从Redis反序列化, sessionId={}, variables={}, count={}", 
            sessionId, variables, this.variables.size());
    }
    
    @JsonIgnore
    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }
    
    public void clearVariables() {
        variables.clear();
        updateAccessTime();
    }
    
    @JsonIgnore
    public int getVariableCount() {
        return variables.size();
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
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
