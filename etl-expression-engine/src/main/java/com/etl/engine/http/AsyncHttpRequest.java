package com.etl.engine.http;

import java.util.concurrent.CompletableFuture;

/**
 * 异步HTTP请求接口，定义了异步HTTP请求的标准操作。
 * <p>
 * 该接口提供了异步请求结果获取、回调处理等功能。
 * 采用密封接口设计，仅允许{@link AsyncHttpRequestImpl}实现。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public sealed interface AsyncHttpRequest permits AsyncHttpRequestImpl {
    
    /**
     * 获取底层CompletableFuture对象，用于高级异步操作。
     *
     * @return 异步请求的CompletableFuture对象
     */
    CompletableFuture<HttpResponse> future();
    
    /**
     * 注册异步请求完成后的消费者回调。
     *
     * @param handler 响应消费者处理器
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    void thenAccept(java.util.function.Consumer<HttpResponse> handler);
    
    /**
     * 注册异步请求完成后的函数回调，可对响应进行转换处理。
     *
     * @param handler 响应函数处理器
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    void thenApply(java.util.function.Function<HttpResponse, ?> handler);
    
    /**
     * 阻塞等待异步请求完成并获取响应结果。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败或被中断时抛出
     */
    HttpResponse get();
    
    /**
     * 在指定超时时间内阻塞等待异步请求完成并获取响应结果。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return HTTP响应对象
     * @throws HttpException 当请求失败或被中断时抛出
     * @throws HttpTimeoutException 当请求超时时抛出
     */
    HttpResponse get(long timeoutMillis);
}
