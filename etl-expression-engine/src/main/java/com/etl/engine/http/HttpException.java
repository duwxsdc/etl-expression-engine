package com.etl.engine.http;

/**
 * HTTP请求异常类，用于表示HTTP请求过程中发生的各种异常情况。
 * <p>
 * 该类继承自{@link RuntimeException}，作为HTTP模块所有异常的基类。
 * 包含HTTP状态码和请求URL等上下文信息，便于错误诊断和处理。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public class HttpException extends RuntimeException {
    
    /**
     * HTTP响应状态码，-1表示无有效状态码（如连接失败等情况）
     */
    private final int statusCode;
    
    /**
     * 请求的URL地址
     */
    private final String url;
    
    /**
     * 使用错误消息构造异常实例。
     *
     * @param message 错误消息，描述异常的具体原因
     */
    public HttpException(String message) {
        super(message);
        this.statusCode = -1;
        this.url = null;
    }
    
    /**
     * 使用错误消息和原因构造异常实例。
     *
     * @param message 错误消息，描述异常的具体原因
     * @param cause 导致此异常的原始异常
     */
    public HttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.url = null;
    }
    
    /**
     * 使用错误消息、状态码和URL构造异常实例。
     *
     * @param message 错误消息，描述异常的具体原因
     * @param statusCode HTTP响应状态码
     * @param url 请求的URL地址
     */
    public HttpException(String message, int statusCode, String url) {
        super(message);
        this.statusCode = statusCode;
        this.url = url;
    }
    
    /**
     * 使用错误消息、状态码、URL和原因构造异常实例。
     *
     * @param message 错误消息，描述异常的具体原因
     * @param statusCode HTTP响应状态码
     * @param url 请求的URL地址
     * @param cause 导致此异常的原始异常
     */
    public HttpException(String message, int statusCode, String url, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.url = url;
    }
    
    /**
     * 获取HTTP响应状态码。
     *
     * @return HTTP状态码，如果无有效状态码则返回-1
     */
    public int getStatusCode() {
        return statusCode;
    }
    
    /**
     * 获取请求的URL地址。
     *
     * @return 请求的URL地址，如果未设置则返回null
     */
    public String getUrl() {
        return url;
    }
}
