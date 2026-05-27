package com.etl.engine.rest.ext;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 扩展请求体构建器
 * 
 * <p>用于构建带请求体的请求，支持链式调用。</p>
 */
public class ExtRequestBodySpec {

    private final ExtRequestSpec requestSpec;

    ExtRequestBodySpec(ExtRequestSpec requestSpec) {
        this.requestSpec = requestSpec;
    }

    public ExtRequestBodySpec body(Object body) {
        requestSpec.body(body);
        return this;
    }

    public ExtRequestBodySpec header(String name, String value) {
        requestSpec.header(name, value);
        return this;
    }

    public ExtRequestSpec defaultToken() {
        requestSpec.defaultToken();
        return requestSpec;
    }

    public ExtRequestSpec appId(String appId) {
        requestSpec.appId(appId);
        return requestSpec;
    }

    public ExtRequestSpec tokenByAppId() {
        requestSpec.tokenByAppId();
        return requestSpec;
    }

    public ExtRequestSpec callback() {
        requestSpec.callback();
        return requestSpec;
    }

    public ExtRequestSpec callback(long timeoutMs) {
        requestSpec.callback(timeoutMs);
        return requestSpec;
    }

    public ExtRequestSpec retry(int maxRetries, long delayMs) {
        requestSpec.retry(maxRetries, delayMs);
        return requestSpec;
    }

    public ExtRequestSpec sign(String secretKey) {
        requestSpec.sign(secretKey);
        return requestSpec;
    }

    public ExtRequestSpec trace() {
        requestSpec.trace();
        return requestSpec;
    }

    public ExtResponseSpec retrieve() {
        return requestSpec.retrieve();
    }

    public <T> T body(Class<T> type) {
        return requestSpec.body(type);
    }

    public <T> org.springframework.http.ResponseEntity<T> toEntity(Class<T> type) {
        return requestSpec.toEntity(type);
    }
}
