package com.etl.engine.http;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * HTTP请求构建器接口，定义了构建和执行HTTP请求的流式API。
 * <p>
 * 该接口提供了丰富的请求配置方法，支持请求头设置、请求体构建、认证配置、
 * 重试策略、拦截器注册等功能。采用密封接口设计，仅允许{@link HttpRequestBuilderImpl}实现。
 * 所有配置方法都返回当前构建器实例，支持链式调用。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public sealed interface HttpRequestBuilder permits HttpRequestBuilderImpl {
    
    /**
     * 添加单个请求头。
     *
     * @param key 请求头名称
     * @param value 请求头值
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder header(String key, String value);
    
    /**
     * 批量添加请求头。
     *
     * @param headers 请求头映射，键为头名称，值为头值
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder headers(Map<String, String> headers);
    
    /**
     * 使用消费者模式通过{@link HeadersBuilder}添加请求头。
     *
     * @param consumer 请求头构建器的消费者
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder header(Consumer<HeadersBuilder> consumer);
    
    /**
     * 设置请求体。
     * <p>
     * 支持多种数据类型：byte[]直接使用，String按UTF-8编码，
     * 其他对象自动序列化为JSON并设置Content-Type为application/json。
     * </p>
     *
     * @param data 请求体数据对象
     * @return 当前构建器实例，支持链式调用
     * @throws HttpParseException 当对象序列化失败时抛出
     */
    HttpRequestBuilder body(Object data);
    
    /**
     * 设置JSON格式的请求体。
     * <p>
     * 将数据对象序列化为JSON字节，并自动设置Content-Type为application/json。
     * </p>
     *
     * @param data 要序列化为JSON的数据对象
     * @return 当前构建器实例，支持链式调用
     * @throws HttpParseException 当JSON序列化失败时抛出
     */
    HttpRequestBuilder bodyJson(Object data);
    
    /**
     * 设置XML格式的请求体。
     *
     * @param xml XML字符串
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder bodyXml(String xml);
    
    /**
     * 设置表单格式的请求体。
     * <p>
     * 将表单数据编码为application/x-www-form-urlencoded格式。
     * </p>
     *
     * @param formData 表单数据映射
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder bodyForm(Map<String, String> formData);
    
    /**
     * 批量设置路径变量。
     * <p>
     * 路径变量用于替换URL中的占位符，如{userId}。
     * </p>
     *
     * @param variables 路径变量映射
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder pathVariables(Map<String, Object> variables);
    
    /**
     * 设置单个路径变量。
     *
     * @param key 路径变量名称
     * @param value 路径变量值
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder pathVariable(String key, Object value);
    
    /**
     * 批量设置查询参数。
     *
     * @param variables 查询参数映射
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder queryVariables(Map<String, Object> variables);
    
    /**
     * 设置单个查询参数。
     *
     * @param key 查询参数名称
     * @param value 查询参数值
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder queryVariable(String key, Object value);
    
    /**
     * 设置请求超时时间。
     *
     * @param millis 超时时间（毫秒），0表示不超时
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder timeout(int millis);
    
    /**
     * 设置Content-Type请求头。
     *
     * @param contentType 内容类型字符串
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder contentType(String contentType);
    
    /**
     * 设置Accept请求头。
     *
     * @param accept 接受的内容类型字符串
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder accept(String accept);
    
    /**
     * 设置Basic认证信息。
     *
     * @param username 用户名
     * @param password 密码
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder basicAuth(String username, String password);
    
    /**
     * 设置Bearer Token认证信息。
     *
     * @param token 认证令牌
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder bearerAuth(String token);
    
    /**
     * 设置重试次数（不指定重试延迟）。
     *
     * @param maxRetries 最大重试次数
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder retry(int maxRetries);
    
    /**
     * 设置重试次数和重试延迟。
     *
     * @param maxRetries 最大重试次数
     * @param delayMillis 重试之间的延迟时间（毫秒）
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder retry(int maxRetries, long delayMillis);
    
    /**
     * 添加请求拦截器。
     *
     * @param interceptor 拦截器实例
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder interceptor(HttpInterceptor interceptor);
    
    /**
     * 使用消费者模式配置构建器。
     *
     * @param configurator 构建器配置消费者
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder configure(Consumer<HttpRequestBuilder> configurator);
    
    /**
     * 设置为同步请求模式。
     *
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder sync();
    
    /**
     * 设置为异步请求模式。
     *
     * @return 当前构建器实例，支持链式调用
     */
    HttpRequestBuilder async();
    
    /**
     * 执行同步GET请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse get();
    
    /**
     * 执行同步POST请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse post();
    
    /**
     * 执行同步PUT请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse put();
    
    /**
     * 执行同步DELETE请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse delete();
    
    /**
     * 执行同步PATCH请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse patch();
    
    /**
     * 执行同步HTTP请求，使用指定的HTTP方法。
     *
     * @param method HTTP请求方法
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    HttpResponse request(String method);
    
    /**
     * 执行异步GET请求。
     *
     * @return 异步HTTP请求对象
     */
    AsyncHttpRequest asyncGet();
    
    /**
     * 执行异步POST请求。
     *
     * @return 异步HTTP请求对象
     */
    AsyncHttpRequest asyncPost();
    
    /**
     * 执行异步PUT请求。
     *
     * @return 异步HTTP请求对象
     */
    AsyncHttpRequest asyncPut();
    
    /**
     * 执行异步DELETE请求。
     *
     * @return 异步HTTP请求对象
     */
    AsyncHttpRequest asyncDelete();
}
