package com.etl.engine.http;

/**
 * HTTP拦截器接口，定义了HTTP请求生命周期中的拦截操作。
 * <p>
 * 拦截器可以在请求发送前、响应接收后、以及请求出错时执行自定义逻辑。
 * 典型应用场景包括：请求日志记录、认证令牌注入、请求重试、响应缓存等。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public interface HttpInterceptor {
    
    /**
     * 在HTTP请求发送前执行拦截逻辑。
     * <p>
     * 可用于修改请求上下文（如添加认证头）或阻止请求发送。
     * </p>
     *
     * @param context HTTP请求上下文，包含请求方法、URL、头信息等
     * @return 如果返回true则继续执行请求，返回false则阻止请求发送
     */
    default boolean beforeRequest(HttpRequestContext context) {
        return true;
    }
    
    /**
     * 在HTTP响应接收后执行拦截逻辑。
     * <p>
     * 可用于日志记录、响应修改等操作。
     * </p>
     *
     * @param context HTTP请求上下文
     * @param response HTTP响应对象
     */
    default void afterResponse(HttpRequestContext context, HttpResponse response) {
    }
    
    /**
     * 在HTTP请求出错时执行拦截逻辑。
     * <p>
     * 可用于错误日志记录、告警通知等操作。
     * </p>
     *
     * @param context HTTP请求上下文
     * @param error 异常对象
     */
    default void onError(HttpRequestContext context, Exception error) {
    }
}
