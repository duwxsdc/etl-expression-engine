package com.etl.engine.rest.ext;

import com.etl.engine.rest.ext.callback.CallbackRegistry;
import com.etl.engine.rest.ext.interceptor.RequestInterceptor;
import com.etl.engine.rest.ext.token.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 扩展请求构建器
 * 
 * <p>支持所有原生RestClient方法 + 扩展方法。</p>
 * 
 * <h3>扩展方法列表：</h3>
 * <ul>
 *   <li>defaultToken() - 动态Token注入</li>
 *   <li>appId() + tokenByAppId() - 根据AppId获取Token</li>
 *   <li>callback(timeoutMs) - 异步回调等待</li>
 *   <li>retry(maxRetries, delayMs) - 重试机制</li>
 *   <li>sign(secretKey) - 请求签名</li>
 *   <li>trace() - 链路追踪</li>
 *   <li>intercept(interceptor) - 请求拦截器</li>
 * </ul>
 */
@Slf4j
public class ExtRequestSpec {

    private final RestClient restClient;
    private final HttpMethod method;
    private final TokenProvider tokenProvider;

    private String url;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<RequestInterceptor> interceptors = new ArrayList<>();
    private Object body;

    private boolean tokenEnabled;
    private boolean tokenByAppIdEnabled;
    private boolean callbackEnabled;
    private long callbackTimeoutMs = 30000;

    ExtRequestSpec(RestClient restClient, HttpMethod method, TokenProvider tokenProvider) {
        this.restClient = restClient;
        this.method = method;
        this.tokenProvider = tokenProvider;
    }

    // ==================== 原生方法 ====================

    public ExtRequestSpec uri(String uri) {
        this.url = uri;
        return this;
    }

    public ExtRequestSpec uri(String uri, Object... uriVariables) {
        this.url = uri;
        return this;
    }

    public ExtRequestSpec header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public ExtRequestSpec accept(MediaType... types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].toString());
        }
        this.headers.put("Accept", sb.toString());
        return this;
    }

    public ExtRequestSpec contentType(MediaType contentType) {
        this.headers.put("Content-Type", contentType.toString());
        return this;
    }

    public ExtRequestSpec contentType(String contentType) {
        this.headers.put("Content-Type", contentType);
        return this;
    }

    public ExtRequestSpec body(Object body) {
        this.body = body;
        return this;
    }

    // ==================== Token扩展 ====================

    /**
     * 启用动态Token注入
     */
    public ExtRequestSpec defaultToken() {
        this.tokenEnabled = true;
        return this;
    }

    /**
     * 设置应用ID（用于tokenByAppId）
     */
    public ExtRequestSpec appId(String appId) {
        this.attributes.put("appId", appId);
        return this;
    }

    /**
     * 根据appId动态获取Token
     * 需先调用 appId() 设置应用标识
     */
    public ExtRequestSpec tokenByAppId() {
        this.tokenByAppIdEnabled = true;
        return this;
    }

    // ==================== 回调扩展 ====================

    /**
     * 启用异步回调等待（默认30秒超时）
     */
    public ExtRequestSpec callback() {
        this.callbackEnabled = true;
        return this;
    }

    /**
     * 启用异步回调等待（自定义超时）
     */
    public ExtRequestSpec callback(long timeoutMs) {
        this.callbackEnabled = true;
        this.callbackTimeoutMs = timeoutMs;
        return this;
    }

    // ==================== 增强扩展 ====================

    /**
     * 重试机制
     */
    public ExtRequestSpec retry(int maxRetries, long delayMs) {
        this.attributes.put("maxRetries", maxRetries);
        this.attributes.put("retryDelayMs", delayMs);
        return this;
    }

    /**
     * 请求签名
     */
    public ExtRequestSpec sign(String secretKey) {
        this.attributes.put("signSecretKey", secretKey);
        return this;
    }

    /**
     * 链路追踪
     */
    public ExtRequestSpec trace() {
        this.headers.put("X-Trace-Id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        this.headers.put("X-Span-Id", UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        return this;
    }

    /**
     * 添加请求拦截器
     */
    public ExtRequestSpec intercept(RequestInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    // ==================== 执行 ====================

    public ExtResponseSpec retrieve() {
        applyExtensions();
        RestClient.ResponseSpec responseSpec = execute();
        return new ExtResponseSpec(responseSpec, this);
    }

    public <T> T body(Class<T> type) {
        applyExtensions();
        return execute().body(type);
    }

    public <T> org.springframework.http.ResponseEntity<T> toEntity(Class<T> type) {
        applyExtensions();
        return execute().toEntity(type);
    }

    // ==================== 内部方法 ====================

    /**
     * 应用所有扩展逻辑
     */
    private void applyExtensions() {
        // 1. 动态Token
        if (tokenEnabled && tokenProvider != null) {
            String token = tokenProvider.compute(url, method.name(), headers, body);
            if (token != null && !token.isEmpty()) {
                headers.put("Authorization", token);
                log.debug("动态Token已注入: url={}", url);
            }
        }

        // 2. 根据AppId获取Token
        if (tokenByAppIdEnabled) {
            String appId = (String) attributes.get("appId");
            if (appId != null) {
                String token = getAppToken(appId);
                headers.put("Authorization", "Bearer " + token);
                log.debug("AppId Token已注入: appId={}, url={}", appId, url);
            }
        }

        // 3. 回调绑定
        if (callbackEnabled) {
            CallbackRegistry registry = CallbackRegistry.getInstance();
            String eventId = registry.generateEventId();
            registry.register(eventId, callbackTimeoutMs);
            headers.put("X-Callback-EventId", eventId);
            headers.put("X-Callback-TargetIp", registry.getLocalIp());
            headers.put("X-Callback-TargetPort", String.valueOf(registry.getLocalPort()));
            attributes.put("callbackEventId", eventId);
            log.debug("回调已绑定: eventId={}, target={}:{}", eventId, registry.getLocalIp(), registry.getLocalPort());
        }

        // 4. 请求拦截器
        if (!interceptors.isEmpty()) {
            RequestInterceptor.RequestContext ctx = new RequestInterceptor.RequestContext();
            ctx.setUrl(url);
            ctx.setMethod(method.name());
            ctx.setHeaders(headers);
            ctx.setBody(body);
            ctx.setAttributes(attributes);
            for (RequestInterceptor interceptor : interceptors) {
                interceptor.intercept(ctx);
            }
        }
    }

    /**
     * 根据AppId获取Token（可扩展为从缓存/配置获取）
     */
    private String getAppToken(String appId) {
        // 这里可以扩展为从TokenCache、配置中心、认证服务等获取
        return appId + "-token-" + System.currentTimeMillis();
    }

    private RestClient.ResponseSpec execute() {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(url);
        headers.forEach(spec::header);
        if (body != null) {
            spec.body(body);
        }
        return spec.retrieve();
    }

    // ==================== Getter ====================

    HttpMethod getMethod() { return method; }
    String getUrl() { return url; }
    boolean isCallbackEnabled() { return callbackEnabled; }
    long getCallbackTimeoutMs() { return callbackTimeoutMs; }
    Map<String, String> getHeaders() { return headers; }
    Map<String, Object> getAttributes() { return attributes; }
}
