package com.etl.engine.model;

import java.util.Map;
import java.util.Collections;

/**
 * HTTP执行结果记录类
 * 使用JDK21 Record实现不可变结构
 * 
 * @param sessionId 会话ID
 * @param originExpr 原始表达式
 * @param success 执行是否成功
 * @param finalResult 最终执行结果
 * @param errorMsg 错误信息
 * @param contextVars 当前上下文变量
 */
public record ExecuteResult(
    String sessionId,
    String originExpr,
    boolean success,
    Object finalResult,
    String errorMsg,
    Map<String, Object> contextVars
) {
    
    public static ExecuteResult success(String sessionId, String originExpr, Object finalResult, Map<String, Object> contextVars) {
        return new ExecuteResult(
            sessionId, 
            originExpr, 
            true, 
            finalResult, 
            null,
            contextVars != null ? contextVars : Collections.emptyMap()
        );
    }
    
    public static ExecuteResult failure(String sessionId, String originExpr, String errorMsg, Map<String, Object> contextVars) {
        return new ExecuteResult(
            sessionId, 
            originExpr, 
            false, 
            null, 
            errorMsg,
            contextVars != null ? contextVars : Collections.emptyMap()
        );
    }
}
