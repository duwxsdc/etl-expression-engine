package com.etl.engine.http;

/**
 * HTTP请求超时异常类，用于表示HTTP请求因超时而失败的情况。
 * <p>
 * 该类继承自{@link HttpException}，专门用于请求超时场景。
 * 包含超时时间信息，便于调用方进行超时相关的错误处理和重试策略。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public class HttpTimeoutException extends HttpException {
    
    /**
     * 超时时间（毫秒）
     */
    private final int timeoutMillis;
    
    /**
     * 使用错误消息和超时时间构造超时异常实例。
     *
     * @param message 错误消息，描述超时的具体情况
     * @param timeoutMillis 超时时间（毫秒）
     */
    public HttpTimeoutException(String message, int timeoutMillis) {
        super(message);
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 使用错误消息、超时时间和原因构造超时异常实例。
     *
     * @param message 错误消息，描述超时的具体情况
     * @param timeoutMillis 超时时间（毫秒）
     * @param cause 导致超时的原始异常
     */
    public HttpTimeoutException(String message, int timeoutMillis, Throwable cause) {
        super(message, cause);
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 获取超时时间。
     *
     * @return 超时时间（毫秒）
     */
    public int getTimeoutMillis() {
        return timeoutMillis;
    }
}
