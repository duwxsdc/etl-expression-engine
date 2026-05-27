package com.etl.engine.rest.ext.token;

import java.util.Map;

/**
 * 动态Token计算函数式接口
 * 
 * <p>基于请求上下文动态计算Token值，支持每次请求实时计算。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 静态Bearer Token
 * TokenProvider bearer = TokenProvider.bearer("my-token");
 * 
 * // 动态计算
 * TokenProvider dynamic = (url, method, headers, body) -> {
 *     return "Bearer " + TokenService.getToken(url);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface TokenProvider {

    /**
     * 根据请求上下文计算Token
     * 
     * @param url 请求URL
     * @param method HTTP方法
     * @param headers 请求头
     * @param body 请求体
     * @return 计算后的Token值（如 "Bearer xxx"）
     */
    String compute(String url, String method, Map<String, String> headers, Object body);

    /**
     * 静态Bearer Token工厂方法
     */
    static TokenProvider bearer(String token) {
        return (url, method, headers, body) -> "Bearer " + token;
    }

    /**
     * 静态Header值工厂方法
     */
    static TokenProvider header(String value) {
        return (url, method, headers, body) -> value;
    }
}
