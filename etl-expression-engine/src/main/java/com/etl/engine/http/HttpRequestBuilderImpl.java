package com.etl.engine.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * HTTP请求构建器实现类，提供流式API构建和执行HTTP请求。
 * <p>
 * 该类实现了{@link HttpRequestBuilder}密封接口，支持同步和异步请求、请求重试、
 * 拦截器链、多种认证方式等功能。内部使用{@link HttpClientAdapter}执行实际HTTP请求，
 * 默认使用JDK 21内置的HttpClient实现。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class HttpRequestBuilderImpl implements HttpRequestBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestBuilderImpl.class);
    
    /**
     * Jackson JSON对象映射器
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    /**
     * 默认请求超时时间（毫秒）
     */
    private static final int DEFAULT_TIMEOUT = 30000;
    
    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 0;
    
    /**
     * 默认重试延迟时间（毫秒）
     */
    private static final long DEFAULT_RETRY_DELAY = 1000;
    
    /**
     * 请求基础URL
     */
    private final String baseUrl;
    
    /**
     * 请求头映射
     */
    private final Map<String, String> headers = new LinkedHashMap<>();
    
    /**
     * 路径变量映射
     */
    private final Map<String, Object> pathVariables = new LinkedHashMap<>();
    
    /**
     * 查询参数映射
     */
    private final Map<String, Object> queryVariables = new LinkedHashMap<>();
    
    /**
     * 请求拦截器列表
     */
    private final List<HttpInterceptor> interceptors = new ArrayList<>();
    
    /**
     * 请求体字节数组
     */
    private byte[] bodyBytes;
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = DEFAULT_TIMEOUT;
    
    /**
     * 最大重试次数
     */
    private int maxRetries = DEFAULT_MAX_RETRIES;
    
    /**
     * 重试延迟时间（毫秒）
     */
    private long retryDelay = DEFAULT_RETRY_DELAY;
    
    /**
     * 是否为异步模式
     */
    private boolean asyncMode = false;
    
    /**
     * Content-Type值
     */
    private String contentType;
    
    /**
     * Accept值
     */
    private String acceptType;
    
    /**
     * HTTP客户端适配器（懒加载单例）
     */
    private static volatile HttpClientAdapter clientAdapter;
    
    /**
     * 异步请求执行器
     */
    private static volatile ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    /**
     * 关闭标志
     */
    private static volatile boolean isShutdown = false;
    
    /**
     * 构造HTTP请求构建器实例。
     *
     * @param baseUrl 请求基础URL
     */
    public HttpRequestBuilderImpl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "";
    }
    
    /**
     * 设置全局HTTP客户端适配器。
     *
     * @param adapter HTTP客户端适配器实例
     */
    public static void setClientAdapter(HttpClientAdapter adapter) {
        clientAdapter = adapter;
    }
    
    /**
     * 获取HTTP客户端适配器，懒加载初始化。
     *
     * @return HTTP客户端适配器实例
     */
    static HttpClientAdapter getClientAdapter() {
        if (clientAdapter == null) {
            synchronized (HttpRequestBuilderImpl.class) {
                if (clientAdapter == null) {
                    clientAdapter = new JavaHttpClientAdapter();
                }
            }
        }
        return clientAdapter;
    }
    
    /**
     * 添加单个请求头。
     *
     * @param key 请求头名称
     * @param value 请求头值
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder header(String key, String value) {
        headers.put(key, value);
        return this;
    }
    
    /**
     * 批量添加请求头。
     *
     * @param headers 请求头映射
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }
    
    /**
     * 使用消费者模式通过HeadersBuilder添加请求头。
     *
     * @param consumer 请求头构建器消费者
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder header(Consumer<HeadersBuilder> consumer) {
        HeadersBuilder builder = new HeadersBuilder();
        consumer.accept(builder);
        this.headers.putAll(builder.build());
        return this;
    }
    
    /**
     * 设置请求体。
     *
     * @param data 请求体数据对象
     * @return 当前构建器实例
     * @throws HttpParseException 当对象序列化失败时抛出
     */
    @Override
    public HttpRequestBuilder body(Object data) {
        if (data == null) {
            this.bodyBytes = null;
            return this;
        }
        if (data instanceof byte[] bytes) {
            this.bodyBytes = bytes;
        } else if (data instanceof String s) {
            this.bodyBytes = s.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                this.bodyBytes = JSON_MAPPER.writeValueAsBytes(data);
                if (!headers.containsKey("Content-Type")) {
                    headers.put("Content-Type", "application/json");
                }
            } catch (Exception e) {
                throw new HttpParseException("请求体序列化失败", e);
            }
        }
        return this;
    }
    
    /**
     * 设置JSON格式的请求体。
     *
     * @param data 要序列化为JSON的数据对象
     * @return 当前构建器实例
     * @throws HttpParseException 当JSON序列化失败时抛出
     */
    @Override
    public HttpRequestBuilder bodyJson(Object data) {
        try {
            logger.debug("bodyJson调用: 输入类型={}, 输入值={}", 
                    data != null ? data.getClass().getName() : "null",
                    data);
            
            this.bodyBytes = JSON_MAPPER.writeValueAsBytes(data);
            headers.put("Content-Type", "application/json");
            
            logger.debug("bodyJson成功: 输出长度={}字节, Content-Type={}", 
                    bodyBytes.length, "application/json");
        } catch (Exception e) {
            logger.error("bodyJson失败: 输入类型={}, 输入值={}, 错误={}", 
                    data != null ? data.getClass().getName() : "null",
                    data,
                    e.getMessage());
            throw new HttpParseException("JSON序列化失败: " + e.getMessage(), e);
        }
        return this;
    }
    
    /**
     * 设置XML格式的请求体。
     *
     * @param xml XML字符串
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder bodyXml(String xml) {
        this.bodyBytes = xml.getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Type", "application/xml");
        return this;
    }
    
    /**
     * 设置表单格式的请求体。
     *
     * @param formData 表单数据映射
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder bodyForm(Map<String, String> formData) {
        StringBuilder sb = new StringBuilder();
        formData.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        this.bodyBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        return this;
    }
    
    /**
     * 批量设置路径变量。
     *
     * @param variables 路径变量映射
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder pathVariables(Map<String, Object> variables) {
        this.pathVariables.putAll(variables);
        return this;
    }
    
    /**
     * 设置单个路径变量。
     *
     * @param key 路径变量名称
     * @param value 路径变量值
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder pathVariable(String key, Object value) {
        this.pathVariables.put(key, value);
        return this;
    }
    
    /**
     * 批量设置查询参数。
     *
     * @param variables 查询参数映射
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder queryVariables(Map<String, Object> variables) {
        this.queryVariables.putAll(variables);
        return this;
    }
    
    /**
     * 设置单个查询参数。
     *
     * @param key 查询参数名称
     * @param value 查询参数值
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder queryVariable(String key, Object value) {
        this.queryVariables.put(key, value);
        return this;
    }
    
    /**
     * 设置请求超时时间。
     *
     * @param millis 超时时间（毫秒）
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder timeout(int millis) {
        this.timeout = Math.max(millis, 0);
        return this;
    }
    
    /**
     * 设置Content-Type请求头。
     *
     * @param contentType 内容类型字符串
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder contentType(String contentType) {
        this.contentType = contentType;
        headers.put("Content-Type", contentType);
        return this;
    }
    
    /**
     * 设置Accept请求头。
     *
     * @param accept 接受的内容类型字符串
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder accept(String accept) {
        this.acceptType = accept;
        headers.put("Accept", accept);
        return this;
    }
    
    /**
     * 设置Basic认证信息。
     *
     * @param username 用户名
     * @param password 密码
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder basicAuth(String username, String password) {
        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoded);
        return this;
    }
    
    /**
     * 设置Bearer Token认证信息。
     *
     * @param token 认证令牌
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder bearerAuth(String token) {
        headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    /**
     * 设置重试次数。
     *
     * @param maxRetries 最大重试次数
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder retry(int maxRetries) {
        this.maxRetries = Math.max(maxRetries, 0);
        return this;
    }
    
    /**
     * 设置重试次数和重试延迟。
     *
     * @param maxRetries 最大重试次数
     * @param delayMillis 重试延迟时间（毫秒）
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder retry(int maxRetries, long delayMillis) {
        this.maxRetries = Math.max(maxRetries, 0);
        this.retryDelay = Math.max(delayMillis, 0);
        return this;
    }
    
    /**
     * 添加请求拦截器。
     *
     * @param interceptor 拦截器实例
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder interceptor(HttpInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }
    
    /**
     * 使用消费者模式配置构建器。
     *
     * @param configurator 构建器配置消费者
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder configure(Consumer<HttpRequestBuilder> configurator) {
        configurator.accept(this);
        return this;
    }
    
    /**
     * 设置为同步请求模式。
     *
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder sync() {
        this.asyncMode = false;
        return this;
    }
    
    /**
     * 设置为异步请求模式。
     *
     * @return 当前构建器实例
     */
    @Override
    public HttpRequestBuilder async() {
        this.asyncMode = true;
        return this;
    }
    
    /**
     * 执行同步GET请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse get() {
        return executeWithRetry("GET");
    }
    
    /**
     * 执行同步POST请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse post() {
        return executeWithRetry("POST");
    }
    
    /**
     * 执行同步PUT请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse put() {
        return executeWithRetry("PUT");
    }
    
    /**
     * 执行同步DELETE请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse delete() {
        return executeWithRetry("DELETE");
    }
    
    /**
     * 执行同步PATCH请求。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse patch() {
        return executeWithRetry("PATCH");
    }
    
    /**
     * 执行同步HTTP请求，使用指定的HTTP方法。
     *
     * @param method HTTP请求方法
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    @Override
    public HttpResponse request(String method) {
        return executeWithRetry(method);
    }
    
    /**
     * 执行异步GET请求。
     *
     * @return 异步HTTP请求对象
     */
    @Override
    public AsyncHttpRequest asyncGet() {
        this.asyncMode = true;
        return executeAsync("GET");
    }
    
    /**
     * 执行异步POST请求。
     *
     * @return 异步HTTP请求对象
     */
    @Override
    public AsyncHttpRequest asyncPost() {
        this.asyncMode = true;
        return executeAsync("POST");
    }
    
    /**
     * 执行异步PUT请求。
     *
     * @return 异步HTTP请求对象
     */
    @Override
    public AsyncHttpRequest asyncPut() {
        this.asyncMode = true;
        return executeAsync("PUT");
    }
    
    /**
     * 执行异步DELETE请求。
     *
     * @return 异步HTTP请求对象
     */
    @Override
    public AsyncHttpRequest asyncDelete() {
        this.asyncMode = true;
        return executeAsync("DELETE");
    }
    
    /**
     * 构建完整的请求URL。
     * <p>
     * 处理路径变量替换和查询参数拼接。
     * </p>
     *
     * @return 完整的请求URL
     */
    private String buildUrl() {
        String url = baseUrl;
        
        for (Map.Entry<String, Object> entry : pathVariables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String replacement = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
            url = url.replace(placeholder, replacement);
        }
        
        if (!queryVariables.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (url.contains("?")) {
                sb.append('&');
            } else {
                sb.append('?');
            }
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryVariables.entrySet()) {
                if (!first) sb.append('&');
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                  .append('=')
                  .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                first = false;
            }
            url += sb;
        }
        
        return url;
    }
    
    /**
     * 带重试机制的请求执行。
     *
     * @param method HTTP请求方法
     * @return HTTP响应对象
     * @throws HttpException 当请求失败时抛出
     */
    private HttpResponse executeWithRetry(String method) {
        String url = buildUrl();
        Map<String, String> finalHeaders = Map.copyOf(headers);
        byte[] finalBody = bodyBytes;
        
        HttpRequestContext context = new HttpRequestContext();
        context.setMethod(method);
        context.setUrl(url);
        context.setHeaders(finalHeaders);
        context.setBody(finalBody);
        context.setTimeout(timeout);
        context.setStartTime(System.currentTimeMillis());
        
        HttpException lastException = null;
        int attemptCount = maxRetries + 1;
        
        for (int attempt = 0; attempt < attemptCount; attempt++) {
            try {
                if (attempt > 0) {
                    logger.info("HTTP请求重试: 第{}次, URL={}, 方法={}", attempt + 1, url, method);
                    context.setRetryCount(attempt);
                    if (retryDelay > 0) {
                        Thread.sleep(retryDelay);
                    }
                }
                
                for (HttpInterceptor interceptor : interceptors) {
                    if (!interceptor.beforeRequest(context)) {
                        throw new HttpException("请求被拦截器拒绝: " + interceptor.getClass().getName());
                    }
                }
                
                HttpResponse response = getClientAdapter().execute(method, url, finalHeaders, finalBody, timeout);
                context.setEndTime(System.currentTimeMillis());
                
                for (HttpInterceptor interceptor : interceptors) {
                    interceptor.afterResponse(context, response);
                }
                
                logger.debug("HTTP请求完成: {} {} -> {} ({}ms)", method, url, 
                        response.statusCode(), context.getDuration());
                
                return response;
                
            } catch (HttpException e) {
                lastException = e;
                context.setEndTime(System.currentTimeMillis());
                for (HttpInterceptor interceptor : interceptors) {
                    interceptor.onError(context, e);
                }
                if (attempt < maxRetries && isRetryable(e)) {
                    logger.warn("HTTP请求失败, 将重试: {} {} -> {}", method, url, e.getMessage());
                    continue;
                }
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpException("请求被中断", e);
            } catch (Exception e) {
                context.setEndTime(System.currentTimeMillis());
                for (HttpInterceptor interceptor : interceptors) {
                    interceptor.onError(context, e);
                }
                lastException = new HttpException("HTTP请求失败: " + e.getMessage(), e);
                if (attempt < maxRetries && isRetryable(e)) {
                    logger.warn("HTTP请求异常, 将重试: {} {} -> {}", method, url, e.getMessage());
                    continue;
                }
                throw lastException;
            }
        }
        
        throw lastException;
    }
    
    /**
     * 判断异常是否可重试。
     *
     * @param e 异常对象
     * @return 如果可重试返回true，否则返回false
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpTimeoutException) return true;
        if (e instanceof HttpException he && he.getStatusCode() >= 500) return true;
        if (e instanceof java.net.ConnectException) return true;
        return false;
    }
    
    /**
     * 执行异步HTTP请求。
     *
     * @param method HTTP请求方法
     * @return 异步HTTP请求对象
     */
    private AsyncHttpRequest executeAsync(String method) {
        if (isShutdown || asyncExecutor.isShutdown()) {
            synchronized (HttpRequestBuilderImpl.class) {
                if (isShutdown || asyncExecutor.isShutdown()) {
                    asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
                    isShutdown = false;
                }
            }
        }
        CompletableFuture<HttpResponse> future = CompletableFuture.supplyAsync(
                () -> executeWithRetry(method), asyncExecutor
        );
        return new AsyncHttpRequestImpl(future);
    }
    
    /**
     * 关闭HTTP请求构建器，释放所有资源。
     */
    public static void shutdown() {
        isShutdown = true;
        asyncExecutor.shutdown();
        if (clientAdapter != null) {
            clientAdapter.shutdown();
        }
    }
    
    /**
     * 重置HTTP请求构建器状态。
     */
    public static void reset() {
        isShutdown = false;
        if (asyncExecutor.isShutdown()) {
            asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
        }
    }
}
