package com.etl.engine.http;

/**
 * HTTP响应解析异常类，用于表示HTTP响应内容解析过程中发生的错误。
 * <p>
 * 该类继承自{@link HttpException}，专门用于JSON、XML等数据格式解析失败的场景。
 * 当响应内容无法正确解析为目标数据类型时抛出此异常。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public class HttpParseException extends HttpException {
    
    /**
     * 使用错误消息构造解析异常实例。
     *
     * @param message 错误消息，描述解析失败的具体原因
     */
    public HttpParseException(String message) {
        super(message);
    }
    
    /**
     * 使用错误消息和原因构造解析异常实例。
     *
     * @param message 错误消息，描述解析失败的具体原因
     * @param cause 导致解析失败的原始异常
     */
    public HttpParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
