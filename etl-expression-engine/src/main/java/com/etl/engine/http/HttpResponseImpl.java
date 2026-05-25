package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * HTTP响应实现类，封装了HTTP响应的完整信息。
 * <p>
 * 该类实现了{@link HttpResponse}密封接口，包含状态码、状态消息、响应头和响应体。
 * 响应头使用不可修改的Map保证线程安全，响应体通过{@link ResponseBodyImpl}进行解析。
 * 提供了便捷的响应处理方法，支持消费者模式和函数式处理。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class HttpResponseImpl implements HttpResponse {
    
    /**
     * HTTP响应状态码
     */
    private final int statusCode;
    
    /**
     * HTTP响应状态消息
     */
    private final String statusMessage;
    
    /**
     * 不可修改的响应头映射
     */
    private final Map<String, String> headers;
    
    /**
     * 响应体对象
     */
    private final ResponseBody body;
    
    /**
     * 构造HTTP响应实现实例。
     *
     * @param statusCode HTTP状态码
     * @param statusMessage 状态消息
     * @param headers 响应头映射
     * @param bodyBytes 响应体字节数组
     * @param contentType 响应体内容类型
     */
    HttpResponseImpl(int statusCode, String statusMessage, Map<String, String> headers, byte[] bodyBytes, String contentType) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = new ResponseBodyImpl(bodyBytes, contentType);
    }
    
    /**
     * 获取HTTP响应状态码。
     *
     * @return HTTP状态码
     */
    @Override
    public int statusCode() {
        return statusCode;
    }
    
    /**
     * 获取HTTP响应状态消息。
     *
     * @return 状态消息字符串
     */
    @Override
    public String statusMessage() {
        return statusMessage;
    }
    
    /**
     * 判断响应是否为成功状态（状态码在200-299范围内）。
     *
     * @return 如果是成功状态返回true，否则返回false
     */
    @Override
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * 判断响应是否为重定向状态（状态码在300-399范围内）。
     *
     * @return 如果是重定向状态返回true，否则返回false
     */
    @Override
    public boolean isRedirect() {
        return statusCode >= 300 && statusCode < 400;
    }
    
    /**
     * 判断响应是否为客户端错误状态（状态码在400-499范围内）。
     *
     * @return 如果是客户端错误状态返回true，否则返回false
     */
    @Override
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * 判断响应是否为服务器错误状态（状态码大于等于500）。
     *
     * @return 如果是服务器错误状态返回true，否则返回false
     */
    @Override
    public boolean isServerError() {
        return statusCode >= 500;
    }
    
    /**
     * 获取所有响应头。
     *
     * @return 不可修改的响应头映射
     */
    @Override
    public Map<String, String> headers() {
        return headers;
    }
    
    /**
     * 根据名称获取指定的响应头值。
     *
     * @param name 响应头名称
     * @return 响应头值，如果不存在则返回null
     */
    @Override
    public String header(String name) {
        return headers.get(name);
    }
    
    /**
     * 将响应体解析为字符串。
     *
     * @return 响应体字符串
     */
    @Override
    public String asString() {
        return body.asString();
    }
    
    /**
     * 将响应体解析为JSON节点。
     *
     * @return JSON节点对象
     * @throws HttpParseException 当JSON解析失败时抛出
     */
    @Override
    public JsonNode asJson() {
        return body.asJson();
    }
    
    /**
     * 将响应体解析为Map对象。
     *
     * @return Map对象
     * @throws HttpParseException 当解析失败时抛出
     */
    @Override
    public Map<String, Object> asMap() {
        return body.asMap();
    }
    
    /**
     * 将响应体解析为XML文档对象。
     *
     * @return XML文档对象
     * @throws HttpParseException 当XML解析失败时抛出
     */
    @Override
    public Document asXml() {
        return body.asXml();
    }
    
    /**
     * 将响应体解析为指定类型的Java Bean对象。
     *
     * @param <T> 目标Java Bean类型
     * @param clazz 目标Java Bean的Class对象
     * @return 解析后的Java Bean对象
     * @throws HttpParseException 当解析失败时抛出
     */
    @Override
    public <T> T asBean(Class<T> clazz) {
        return body.asBean(clazz);
    }
    
    /**
     * 将响应体解析为指定类名的Java对象。
     *
     * @param className 目标类型的类名或泛型类型字符串
     * @return 解析后的Java对象
     * @throws HttpParseException 当解析失败时抛出
     * @throws IllegalArgumentException 当类名为空时抛出
     */
    @Override
    public Object asJava(String className) {
        return body.asJava(className);
    }
    
    /**
     * 将响应体解析为指定类型的Java对象。
     *
     * @param <T> 目标Java类型
     * @param clazz 目标类型的Class对象
     * @return 解析后的Java对象
     * @throws HttpParseException 当解析失败时抛出
     */
    @Override
    public <T> T asJava(Class<T> clazz) {
        return body.asJava(clazz);
    }
    
    /**
     * 使用消费者处理器处理当前响应。
     *
     * @param handler 响应处理器
     * @return 当前响应对象，支持链式调用
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    @Override
    public HttpResponse response(Consumer<HttpResponse> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("处理器不能为null");
        }
        handler.accept(this);
        return this;
    }
    
    /**
     * 使用函数处理器处理当前响应并返回结果。
     *
     * @param <R> 处理器返回值类型
     * @param handler 响应处理器函数
     * @return 处理器的返回结果
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    @Override
    public <R> R response(Function<HttpResponse, R> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("处理器不能为null");
        }
        return handler.apply(this);
    }
    
    /**
     * 从JSON响应体中提取指定路径的值。
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @return 提取的值
     * @throws HttpParseException 当路径无效或提取失败时抛出
     */
    @Override
    public <T> T extract(String jsonPath) {
        return body.extract(jsonPath);
    }
    
    /**
     * 从JSON响应体中提取指定路径的值并转换为指定类型。
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @param type 目标类型的Class对象
     * @return 提取并转换后的值
     * @throws HttpParseException 当路径无效或类型转换失败时抛出
     */
    @Override
    public <T> T extract(String jsonPath, Class<T> type) {
        return body.extract(jsonPath, type);
    }
    
    /**
     * 使用自定义解析器解析响应体。
     *
     * @param <T> 解析结果类型
     * @param parser 自定义解析函数
     * @return 解析结果
     * @throws HttpParseException 当解析失败时抛出
     */
    @Override
    public <T> T custom(Function<String, T> parser) {
        return body.custom(parser);
    }
    
    /**
     * 获取响应体的原始字节数组副本。
     *
     * @return 响应体字节数组的副本
     */
    @Override
    public byte[] asBytes() {
        return body.asBytes();
    }
    
    /**
     * 获取响应体的内容长度。
     *
     * @return 内容长度（字节），如果响应头中未包含Content-Length则返回-1
     */
    @Override
    public long contentLength() {
        String length = headers.get("Content-Length");
        return length != null ? Long.parseLong(length) : -1;
    }
    
    /**
     * 获取响应体的内容类型。
     *
     * @return 内容类型字符串，如果响应头中未包含Content-Type则返回null
     */
    @Override
    public String contentType() {
        return headers.get("Content-Type");
    }
    
    /**
     * 获取响应体对象。
     *
     * @return 响应体对象
     */
    @Override
    public ResponseBody body() {
        return body;
    }
    
    /**
     * 返回HTTP响应的字符串表示。
     *
     * @return 包含状态码、状态消息、头数量和响应体长度的字符串
     */
    @Override
    public String toString() {
        return "HttpResponse{statusCode=" + statusCode + 
               ", statusMessage='" + statusMessage + '\'' +
               ", headers=" + headers.size() + " entries" +
               ", body=" + (body != null ? body.asString().length() + " chars" : "null") +
               '}';
    }
}
