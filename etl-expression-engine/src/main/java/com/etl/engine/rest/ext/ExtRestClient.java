package com.etl.engine.rest.ext;

import com.etl.engine.rest.ext.token.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 扩展RestClient装饰器入口
 * 
 * <p>基于装饰器模式包装原生RestClient，保持100%API兼容。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 注入使用
 * @Autowired
 * private ExtRestClient extClient;
 * 
 * // 动态Token + 异步回调
 * Object result = extClient.post()
 *     .uri("http://api.example.com/data")
 *     .defaultToken()
 *     .callback(30000)
 *     .body(data)
 *     .retrieve()
 *     .body(Object.class);
 * }</pre>
 */
@Slf4j
public final class ExtRestClient {

    private final RestClient delegate;
    private final TokenProvider tokenProvider;

    private ExtRestClient(RestClient delegate, TokenProvider tokenProvider) {
        this.delegate = delegate;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 创建ExtRestClient（默认Token）
     */
    public static ExtRestClient of(RestClient restClient) {
        return new ExtRestClient(restClient, TokenProvider.bearer("default-token"));
    }

    /**
     * 创建ExtRestClient（自定义TokenProvider）
     */
    public static ExtRestClient of(RestClient restClient, TokenProvider tokenProvider) {
        return new ExtRestClient(restClient, tokenProvider);
    }

    public ExtRequestSpec get() {
        return new ExtRequestSpec(delegate, HttpMethod.GET, tokenProvider);
    }

    public ExtRequestSpec post() {
        return new ExtRequestSpec(delegate, HttpMethod.POST, tokenProvider);
    }

    public ExtRequestSpec put() {
        return new ExtRequestSpec(delegate, HttpMethod.PUT, tokenProvider);
    }

    public ExtRequestSpec delete() {
        return new ExtRequestSpec(delegate, HttpMethod.DELETE, tokenProvider);
    }

    public ExtRequestSpec patch() {
        return new ExtRequestSpec(delegate, HttpMethod.PATCH, tokenProvider);
    }

    public ExtRequestSpec head() {
        return new ExtRequestSpec(delegate, HttpMethod.HEAD, tokenProvider);
    }

    public ExtRequestSpec method(HttpMethod method) {
        return new ExtRequestSpec(delegate, method, tokenProvider);
    }

    public ExtRequestSpec method(String method) {
        return new ExtRequestSpec(delegate, HttpMethod.valueOf(method.toUpperCase()), tokenProvider);
    }

    /**
     * 获取原生RestClient实例
     */
    public RestClient unwrap() {
        return delegate;
    }
}
