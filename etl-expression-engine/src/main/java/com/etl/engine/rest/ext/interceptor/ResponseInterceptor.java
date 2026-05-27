package com.etl.engine.rest.ext.interceptor;

/**
 * 响应拦截器接口
 * 
 * <p>在响应返回后拦截，可用于日志、转换、后处理等。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ResponseInterceptor logInterceptor = resp -> {
 *     log.info("响应结果: {}", resp);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ResponseInterceptor {

    /**
     * 拦截响应
     * @param response 响应对象
     */
    void intercept(Object response);
}
