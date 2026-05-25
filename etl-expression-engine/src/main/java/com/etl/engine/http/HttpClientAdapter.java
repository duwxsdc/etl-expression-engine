package com.etl.engine.http;

/**
 * HTTP客户端适配器接口，定义了HTTP请求执行的标准规范。
 * <p>
 * 该接口抽象了底层HTTP客户端的实现细节，支持不同的HTTP客户端库。
 * 通过实现此接口，可以灵活切换不同的HTTP客户端实现（如Java HttpClient、OkHttp等）。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public interface HttpClientAdapter {
    
    /**
     * 执行HTTP请求并返回响应结果。
     *
     * @param method HTTP请求方法（如GET、POST、PUT、DELETE等）
     * @param url 请求的URL地址
     * @param headers 请求头映射，键为头名称，值为头值
     * @param body 请求体字节数组，对于无请求体的请求可传null
     * @param timeoutMillis 请求超时时间（毫秒）
     * @return HTTP响应对象，包含状态码、响应头和响应体
     * @throws HttpException 当请求执行失败时抛出
     * @throws HttpTimeoutException 当请求超时时抛出
     */
    HttpResponse execute(String method, String url, 
                        java.util.Map<String, String> headers, 
                        byte[] body, 
                        int timeoutMillis);
    
    /**
     * 关闭HTTP客户端适配器，释放相关资源。
     * <p>
     * 调用此方法后，适配器将不再可用。
     * </p>
     */
    void shutdown();
    
    /**
     * 获取HTTP客户端适配器的名称和描述信息。
     *
     * @return 适配器的名称描述字符串
     */
    String getName();
}
