package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * HTTP响应接口，定义了HTTP响应的标准操作。
 * <p>
 * 该接口提供了丰富的响应内容处理方法，支持字符串、JSON、XML、Map等多种格式的解析。
 * 采用密封接口设计，仅允许{@link HttpResponseImpl}实现。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public sealed interface HttpResponse permits HttpResponseImpl {
    
    /**
     * 获取HTTP响应状态码。
     *
     * @return HTTP状态码（如200、404、500等）
     */
    int statusCode();
    
    /**
     * 获取HTTP响应状态消息。
     *
     * @return 状态消息字符串
     */
    String statusMessage();
    
    /**
     * 判断响应是否为成功状态（状态码在200-299范围内）。
     *
     * @return 如果是成功状态返回true，否则返回false
     */
    boolean isSuccess();
    
    /**
     * 判断响应是否为重定向状态（状态码在300-399范围内）。
     *
     * @return 如果是重定向状态返回true，否则返回false
     */
    boolean isRedirect();
    
    /**
     * 判断响应是否为客户端错误状态（状态码在400-499范围内）。
     *
     * @return 如果是客户端错误状态返回true，否则返回false
     */
    boolean isClientError();
    
    /**
     * 判断响应是否为服务器错误状态（状态码在500-599范围内）。
     *
     * @return 如果是服务器错误状态返回true，否则返回false
     */
    boolean isServerError();
    
    /**
     * 获取所有响应头。
     *
     * @return 响应头映射，键为头名称，值为头值
     */
    Map<String, String> headers();
    
    /**
     * 根据名称获取指定的响应头值。
     *
     * @param name 响应头名称
     * @return 响应头值，如果不存在则返回null
     */
    String header(String name);
    
    /**
     * 将响应体解析为字符串。
     *
     * @return 响应体字符串
     * @throws HttpParseException 当解析失败时抛出
     */
    String asString();
    
    /**
     * 将响应体解析为JSON节点。
     *
     * @return JSON节点对象
     * @throws HttpParseException 当JSON解析失败时抛出
     */
    JsonNode asJson();
    
    /**
     * 将响应体解析为Map对象。
     *
     * @return Map对象，键为String类型，值为Object类型
     * @throws HttpParseException 当解析失败时抛出
     */
    Map<String, Object> asMap();
    
    /**
     * 将响应体解析为XML文档对象。
     *
     * @return XML文档对象
     * @throws HttpParseException 当XML解析失败时抛出
     */
    Document asXml();
    
    /**
     * 将响应体解析为指定类型的Java Bean对象。
     *
     * @param <T> 目标Java Bean类型
     * @param clazz 目标Java Bean的Class对象
     * @return 解析后的Java Bean对象
     * @throws HttpParseException 当解析失败时抛出
     */
    <T> T asBean(Class<T> clazz);
    
    /**
     * 将响应体解析为指定类名的Java对象。
     * <p>
     * 支持简单类名和完整类名，也支持泛型类型字符串（如"List&lt;User&gt;"）。
     * </p>
     *
     * @param className 目标类型的类名或泛型类型字符串
     * @return 解析后的Java对象
     * @throws HttpParseException 当解析失败时抛出
     * @throws IllegalArgumentException 当类名为空或无效时抛出
     */
    Object asJava(String className);
    
    /**
     * 将响应体解析为指定类型的Java对象。
     *
     * @param <T> 目标Java类型
     * @param clazz 目标类型的Class对象
     * @return 解析后的Java对象
     * @throws HttpParseException 当解析失败时抛出
     */
    <T> T asJava(Class<T> clazz);
    
    /**
     * 使用消费者处理器处理当前响应。
     *
     * @param handler 响应处理器
     * @return 当前响应对象，支持链式调用
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    HttpResponse response(Consumer<HttpResponse> handler);
    
    /**
     * 使用函数处理器处理当前响应并返回结果。
     *
     * @param <R> 处理器返回值类型
     * @param handler 响应处理器函数
     * @return 处理器的返回结果
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
    <R> R response(Function<HttpResponse, R> handler);
    
    /**
     * 从JSON响应体中提取指定路径的值。
     * <p>
     * 支持点分隔的路径表达式，如"data.user.name"，也支持数组索引如"items[0]"。
     * </p>
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @return 提取的值
     * @throws HttpParseException 当路径无效或提取失败时抛出
     * @throws IllegalArgumentException 当路径为空时抛出
     */
    <T> T extract(String jsonPath);
    
    /**
     * 从JSON响应体中提取指定路径的值并转换为指定类型。
     * <p>
     * 支持点分隔的路径表达式，如"data.user.name"，也支持数组索引如"items[0]"。
     * </p>
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @param type 目标类型的Class对象
     * @return 提取并转换后的值
     * @throws HttpParseException 当路径无效或类型转换失败时抛出
     * @throws IllegalArgumentException 当路径为空或类型为null时抛出
     */
    <T> T extract(String jsonPath, Class<T> type);
    
    /**
     * 使用自定义解析器解析响应体。
     *
     * @param <T> 解析结果类型
     * @param parser 自定义解析函数
     * @return 解析结果
     * @throws HttpParseException 当解析失败时抛出
     */
    <T> T custom(Function<String, T> parser);
    
    /**
     * 获取响应体的原始字节数组。
     *
     * @return 响应体字节数组的副本
     */
    byte[] asBytes();
    
    /**
     * 获取响应体的内容长度。
     *
     * @return 内容长度（字节），如果响应头中未包含Content-Length则返回-1
     */
    long contentLength();
    
    /**
     * 获取响应体的内容类型。
     *
     * @return 内容类型字符串，如果响应头中未包含Content-Type则返回null
     */
    String contentType();
    
    /**
     * 获取响应体对象。
     *
     * @return 响应体对象
     */
    ResponseBody body();
}
