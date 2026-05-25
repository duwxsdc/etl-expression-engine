package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Map;
import java.util.function.Function;

/**
 * HTTP响应体接口，定义了HTTP响应体的标准解析操作。
 * <p>
 * 该接口提供了多种响应体解析方法，支持字符串、JSON、XML、Java Bean等格式。
 * 采用密封接口设计，仅允许{@link ResponseBodyImpl}实现。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public sealed interface ResponseBody permits ResponseBodyImpl {
    
    /**
     * 将响应体解析为字符串。
     *
     * @return 响应体字符串
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
}
