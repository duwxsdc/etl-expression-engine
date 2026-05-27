package com.etl.engine.rest.ext;

import com.etl.engine.rest.ext.callback.CallbackRegistry;
import com.etl.engine.rest.ext.interceptor.ResponseInterceptor;
import com.etl.engine.rest.ext.validator.ResponseValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 扩展响应处理器
 * 
 * <p>支持响应校验、拦截、回调等待等功能。</p>
 * 
 * <h3>校验方法列表：</h3>
 * <ul>
 *   <li>assertContainsKey(key) - 校验字段存在</li>
 *   <li>assertKeyValue(key, value) - 校验字段值</li>
 *   <li>assertStatus(status) - 校验HTTP状态码</li>
 *   <li>peek(consumer) - 查看响应内容</li>
 *   <li>intercept(interceptor) - 响应拦截器</li>
 * </ul>
 */
@Slf4j
public class ExtResponseSpec {

    private final RestClient.ResponseSpec delegate;
    private final ExtRequestSpec requestSpec;
    private Object cachedResponse;

    ExtResponseSpec(RestClient.ResponseSpec delegate, ExtRequestSpec requestSpec) {
        this.delegate = delegate;
        this.requestSpec = requestSpec;
    }

    // ==================== 原生方法 ====================

    /**
     * 获取响应体
     */
    @SuppressWarnings("unchecked")
    public <T> T body(Class<T> type) {
        T result = delegate.body(type);
        this.cachedResponse = result;

        if (requestSpec.isCallbackEnabled()) {
            return (T) handleCallback(result);
        }
        return result;
    }

    /**
     * 获取响应实体
     */
    public <T> org.springframework.http.ResponseEntity<T> toEntity(Class<T> type) {
        org.springframework.http.ResponseEntity<T> response = delegate.toEntity(type);
        this.cachedResponse = response.getBody();
        return response;
    }

    /**
     * 获取无体响应实体
     */
    public org.springframework.http.ResponseEntity<Void> toBodilessEntity() {
        return delegate.toBodilessEntity();
    }

    // ==================== 校验扩展 ====================

    /**
     * 校验响应中是否包含指定key
     */
    public ExtResponseSpec assertContainsKey(String key) {
        ensureCachedResponse();
        if (cachedResponse instanceof Map<?, ?> map) {
            if (!map.containsKey(key)) {
                throw new ResponseValidationException("响应缺少必要字段: " + key);
            }
        }
        return this;
    }

    /**
     * 校验响应中指定key的值
     */
    public ExtResponseSpec assertKeyValue(String key, Object expectedValue) {
        ensureCachedResponse();
        if (cachedResponse instanceof Map<?, ?> map) {
            Object actual = map.get(key);
            if (!Objects.equals(actual, expectedValue)) {
                throw new ResponseValidationException(
                        "字段值不匹配: " + key + ", 期望=" + expectedValue + ", 实际=" + actual);
            }
        }
        return this;
    }

    /**
     * 校验响应状态码
     */
    public ExtResponseSpec assertStatus(int expectedStatus) {
        org.springframework.http.ResponseEntity<Void> entity = delegate.toBodilessEntity();
        if (entity.getStatusCode().value() != expectedStatus) {
            throw new ResponseValidationException(
                    "状态码不匹配: 期望=" + expectedStatus + ", 实际=" + entity.getStatusCode().value());
        }
        return this;
    }

    // ==================== 后处理扩展 ====================

    /**
     * 查看响应内容（不消费响应）
     */
    public ExtResponseSpec peek(Consumer<Object> consumer) {
        ensureCachedResponse();
        consumer.accept(cachedResponse);
        return this;
    }

    /**
     * 添加响应拦截器
     */
    public ExtResponseSpec intercept(ResponseInterceptor interceptor) {
        ensureCachedResponse();
        interceptor.intercept(cachedResponse);
        return this;
    }

    // ==================== 内部方法 ====================

    private void ensureCachedResponse() {
        if (cachedResponse == null) {
            cachedResponse = delegate.body(Object.class);
        }
    }

    /**
     * 处理回调等待
     */
    private Object handleCallback(Object initialResult) {
        String eventId = requestSpec.getHeaders().get("X-Callback-EventId");
        long timeoutMs = requestSpec.getCallbackTimeoutMs();

        log.debug("等待回调: eventId={}, timeout={}ms", eventId, timeoutMs);
        Object callbackResult = CallbackRegistry.getInstance().waitForCallback(eventId, timeoutMs);
        log.debug("回调完成: eventId={}", eventId);
        return callbackResult;
    }
}
