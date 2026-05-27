package com.etl.engine.rest.ext.interceptor;

import lombok.Data;

import java.util.Map;

/**
 * 请求拦截器接口
 * 
 * <p>在请求发送前拦截，可修改headers、body等。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * RequestInterceptor interceptor = ctx -> {
 *     ctx.getHeaders().put("X-Request-Id", UUID.randomUUID().toString());
 *     ctx.getHeaders().put("X-Client-Version", "1.0.0");
 * };
 * }</pre>
 */
@FunctionalInterface
public interface RequestInterceptor {

    /**
     * 拦截请求
     * @param context 请求上下文
     */
    void intercept(RequestContext context);

    /**
     * 请求上下文
     */
    @Data
    class RequestContext {
        private String url;
        private String method;
        private Map<String, String> headers;
        private Object body;
        private Map<String, Object> attributes;
    }
}
