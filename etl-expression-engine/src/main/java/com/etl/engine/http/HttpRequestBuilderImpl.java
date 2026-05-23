package com.etl.engine.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class HttpRequestBuilderImpl implements HttpRequestBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestBuilderImpl.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final long DEFAULT_RETRY_DELAY = 1000;
    
    private final String baseUrl;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> pathVariables = new LinkedHashMap<>();
    private final Map<String, Object> queryVariables = new LinkedHashMap<>();
    private final List<HttpInterceptor> interceptors = new ArrayList<>();
    
    private byte[] bodyBytes;
    private int timeout = DEFAULT_TIMEOUT;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private long retryDelay = DEFAULT_RETRY_DELAY;
    private boolean asyncMode = false;
    private String contentType;
    private String acceptType;
    
    private static volatile HttpClientAdapter clientAdapter;
    private static final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    public HttpRequestBuilderImpl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "";
    }
    
    public static void setClientAdapter(HttpClientAdapter adapter) {
        clientAdapter = adapter;
    }
    
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
    
    @Override
    public HttpRequestBuilder header(String key, String value) {
        headers.put(key, value);
        return this;
    }
    
    @Override
    public HttpRequestBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }
    
    @Override
    public HttpRequestBuilder header(Consumer<HeadersBuilder> consumer) {
        HeadersBuilder builder = new HeadersBuilder();
        consumer.accept(builder);
        this.headers.putAll(builder.build());
        return this;
    }
    
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
    
    @Override
    public HttpRequestBuilder bodyXml(String xml) {
        this.bodyBytes = xml.getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Type", "application/xml");
        return this;
    }
    
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
    
    @Override
    public HttpRequestBuilder pathVariables(Map<String, Object> variables) {
        this.pathVariables.putAll(variables);
        return this;
    }
    
    @Override
    public HttpRequestBuilder pathVariable(String key, Object value) {
        this.pathVariables.put(key, value);
        return this;
    }
    
    @Override
    public HttpRequestBuilder queryVariables(Map<String, Object> variables) {
        this.queryVariables.putAll(variables);
        return this;
    }
    
    @Override
    public HttpRequestBuilder queryVariable(String key, Object value) {
        this.queryVariables.put(key, value);
        return this;
    }
    
    @Override
    public HttpRequestBuilder timeout(int millis) {
        this.timeout = Math.max(millis, 0);
        return this;
    }
    
    @Override
    public HttpRequestBuilder contentType(String contentType) {
        this.contentType = contentType;
        headers.put("Content-Type", contentType);
        return this;
    }
    
    @Override
    public HttpRequestBuilder accept(String accept) {
        this.acceptType = accept;
        headers.put("Accept", accept);
        return this;
    }
    
    @Override
    public HttpRequestBuilder basicAuth(String username, String password) {
        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoded);
        return this;
    }
    
    @Override
    public HttpRequestBuilder bearerAuth(String token) {
        headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    @Override
    public HttpRequestBuilder retry(int maxRetries) {
        this.maxRetries = Math.max(maxRetries, 0);
        return this;
    }
    
    @Override
    public HttpRequestBuilder retry(int maxRetries, long delayMillis) {
        this.maxRetries = Math.max(maxRetries, 0);
        this.retryDelay = Math.max(delayMillis, 0);
        return this;
    }
    
    @Override
    public HttpRequestBuilder interceptor(HttpInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }
    
    @Override
    public HttpRequestBuilder configure(Consumer<HttpRequestBuilder> configurator) {
        configurator.accept(this);
        return this;
    }
    
    @Override
    public HttpRequestBuilder sync() {
        this.asyncMode = false;
        return this;
    }
    
    @Override
    public HttpRequestBuilder async() {
        this.asyncMode = true;
        return this;
    }
    
    @Override
    public HttpResponse get() {
        return executeWithRetry("GET");
    }
    
    @Override
    public HttpResponse post() {
        return executeWithRetry("POST");
    }
    
    @Override
    public HttpResponse put() {
        return executeWithRetry("PUT");
    }
    
    @Override
    public HttpResponse delete() {
        return executeWithRetry("DELETE");
    }
    
    @Override
    public HttpResponse patch() {
        return executeWithRetry("PATCH");
    }
    
    @Override
    public HttpResponse request(String method) {
        return executeWithRetry(method);
    }
    
    @Override
    public AsyncHttpRequest asyncGet() {
        this.asyncMode = true;
        return executeAsync("GET");
    }
    
    @Override
    public AsyncHttpRequest asyncPost() {
        this.asyncMode = true;
        return executeAsync("POST");
    }
    
    @Override
    public AsyncHttpRequest asyncPut() {
        this.asyncMode = true;
        return executeAsync("PUT");
    }
    
    @Override
    public AsyncHttpRequest asyncDelete() {
        this.asyncMode = true;
        return executeAsync("DELETE");
    }
    
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
    
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpTimeoutException) return true;
        if (e instanceof HttpException he && he.getStatusCode() >= 500) return true;
        if (e instanceof java.net.ConnectException) return true;
        return false;
    }
    
    private AsyncHttpRequest executeAsync(String method) {
        CompletableFuture<HttpResponse> future = CompletableFuture.supplyAsync(
                () -> executeWithRetry(method), asyncExecutor
        );
        return new AsyncHttpRequestImpl(future);
    }
    
    public static void shutdown() {
        asyncExecutor.shutdown();
        if (clientAdapter != null) {
            clientAdapter.shutdown();
        }
    }
}
