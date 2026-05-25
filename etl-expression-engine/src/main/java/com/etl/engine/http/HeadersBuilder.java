package com.etl.engine.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP请求头构建器，提供流式API用于构建HTTP请求头。
 * <p>
 * 该类提供了常用的HTTP头设置方法，包括Content-Type、Accept、Authorization等，
 * 也支持自定义头的添加。使用链式调用风格，便于在请求构建器中使用。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class HeadersBuilder {
    
    /**
     * 请求头映射，使用LinkedHashMap保持插入顺序
     */
    private final Map<String, String> headers = new LinkedHashMap<>();
    
    /**
     * 添加自定义请求头。
     *
     * @param key 请求头名称
     * @param value 请求头值
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder add(String key, String value) {
        headers.put(key, value);
        return this;
    }
    
    /**
     * 设置Content-Type请求头。
     *
     * @param value 内容类型值
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder contentType(String value) {
        headers.put("Content-Type", value);
        return this;
    }
    
    /**
     * 设置Accept请求头。
     *
     * @param value 接受的内容类型值
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder accept(String value) {
        headers.put("Accept", value);
        return this;
    }
    
    /**
     * 设置Authorization请求头。
     *
     * @param value 认证信息值
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder authorization(String value) {
        headers.put("Authorization", value);
        return this;
    }
    
    /**
     * 设置Bearer Token认证头。
     *
     * @param token Bearer认证令牌
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder bearerAuth(String token) {
        headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    /**
     * 设置Basic认证头。
     * <p>
     * 将用户名和密码编码为Base64格式，并设置Authorization头。
     * </p>
     *
     * @param username 用户名
     * @param password 密码
     * @return 当前构建器实例，支持链式调用
     */
    public HeadersBuilder basicAuth(String username, String password) {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
        headers.put("Authorization", "Basic " + encoded);
        return this;
    }
    
    /**
     * 构建并返回不可修改的请求头映射。
     *
     * @return 请求头的不可修改副本
     */
    Map<String, String> build() {
        return Map.copyOf(headers);
    }
}
