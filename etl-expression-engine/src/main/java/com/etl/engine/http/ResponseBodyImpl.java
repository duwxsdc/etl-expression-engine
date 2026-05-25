package com.etl.engine.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.function.Function;

/**
 * HTTP响应体实现类，提供多种格式的响应体解析功能。
 * <p>
 * 该类实现了{@link ResponseBody}接口，支持将响应体解析为字符串、JSON、XML、Map、
 * Java Bean等多种格式。内部使用Jackson进行JSON解析，使用JDK内置解析器进行XML解析。
 * 响应体字符串采用延迟缓存机制，避免重复解析。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class ResponseBodyImpl implements ResponseBody {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseBodyImpl.class);
    
    /**
     * Jackson JSON对象映射器
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    /**
     * Jackson XML对象映射器
     */
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    
    /**
     * XML文档构建器工厂
     */
    private static final DocumentBuilderFactory DOC_FACTORY = DocumentBuilderFactory.newInstance();
    
    /**
     * 响应体原始字节数组
     */
    private final byte[] bytes;
    
    /**
     * 响应体的Content-Type
     */
    private final String contentType;
    
    /**
     * 延迟缓存的响应体字符串
     */
    private volatile String cachedString;
    
    /**
     * 线程安全的响应体字符串缓存（构造时即初始化）
     */
    private final String cachedStringSafe;
    
    /**
     * 构造响应体实现实例。
     *
     * @param bytes 响应体原始字节数组
     * @param contentType 响应体的Content-Type
     */
    ResponseBodyImpl(byte[] bytes, String contentType) {
        this.bytes = bytes;
        this.contentType = contentType;
        this.cachedStringSafe = bytes != null 
                ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) 
                : "";
    }
    
    /**
     * 将响应体解析为字符串。
     * <p>
     * 使用延迟缓存机制，首次调用时进行解析，后续直接返回缓存结果。
     * </p>
     *
     * @return 响应体字符串
     */
    @Override
    public String asString() {
        String result = cachedString;
        if (result == null) {
            result = cachedStringSafe;
            cachedString = result;
        }
        return result;
    }
    
    /**
     * 将响应体解析为JSON节点。
     *
     * @return JSON节点对象
     * @throws HttpParseException 当JSON解析失败时抛出
     */
    @Override
    public JsonNode asJson() {
        try {
            return JSON_MAPPER.readTree(asString());
        } catch (Exception e) {
            logger.error("JSON解析失败: {}", e.getMessage());
            throw new HttpParseException("JSON解析失败", e);
        }
    }
    
    /**
     * 将响应体解析为Map对象。
     *
     * @return Map对象，键为String类型，值为Object类型
     * @throws HttpParseException 当解析失败时抛出
     */
    @Override
    public Map<String, Object> asMap() {
        try {
            return JSON_MAPPER.readValue(asString(), new TypeReference<>() {});
        } catch (Exception e) {
            logger.error("Map解析失败: {}", e.getMessage());
            throw new HttpParseException("Map解析失败", e);
        }
    }
    
    /**
     * 将响应体解析为XML文档对象。
     *
     * @return XML文档对象
     * @throws HttpParseException 当XML解析失败时抛出
     */
    @Override
    public Document asXml() {
        try {
            DocumentBuilder builder = DOC_FACTORY.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            logger.error("XML解析失败: {}", e.getMessage());
            throw new HttpParseException("XML解析失败", e);
        }
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
        try {
            return JSON_MAPPER.readValue(asString(), clazz);
        } catch (Exception e) {
            logger.error("Bean解析失败, 目标类型: {}, 错误: {}", clazz.getName(), e.getMessage());
            throw new HttpParseException("Bean解析失败: " + clazz.getName(), e);
        }
    }
    
    /**
     * 将响应体解析为指定类名的Java对象。
     * <p>
     * 通过{@link TypeResolver}解析类型字符串，支持简单类名、完整类名和泛型类型。
     * </p>
     *
     * @param className 目标类型的类名或泛型类型字符串
     * @return 解析后的Java对象
     * @throws HttpParseException 当类型转换失败时抛出
     * @throws IllegalArgumentException 当类名为空或无效时抛出
     */
    @Override
    public Object asJava(String className) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        
        String trimmed = className.trim();
        logger.debug("asJava通过类名转换: className={}", trimmed);
        
        try {
            JavaType javaType = TypeResolver.resolveType(trimmed);
            return JSON_MAPPER.readValue(asString(), javaType);
        } catch (HttpParseException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("asJava转换失败: className={}, 错误: {}", trimmed, e.getMessage());
            throw new HttpParseException("类型转换失败: " + trimmed + ", 原因: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将响应体解析为指定类型的Java对象。
     *
     * @param <T> 目标Java类型
     * @param clazz 目标类型的Class对象
     * @return 解析后的Java对象
     * @throws HttpParseException 当类型转换失败时抛出
     * @throws IllegalArgumentException 当目标类型为null时抛出
     */
    @Override
    public <T> T asJava(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("目标类型不能为null");
        }
        
        logger.debug("asJava通过Class转换: clazz={}", clazz.getName());
        
        try {
            return JSON_MAPPER.readValue(asString(), clazz);
        } catch (Exception e) {
            logger.error("asJava Class转换失败: clazz={}, 错误: {}", clazz.getName(), e.getMessage());
            throw new HttpParseException("类型转换失败: " + clazz.getName(), e);
        }
    }
    
    /**
     * 从JSON响应体中提取指定路径的值。
     * <p>
     * 支持点分隔的路径表达式和数组索引，如"data.users[0].name"。
     * </p>
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @return 提取的值
     * @throws HttpParseException 当路径无效或提取失败时抛出
     * @throws IllegalArgumentException 当路径为空时抛出
     */
    @Override
    public <T> T extract(String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON路径不能为空");
        }
        
        logger.debug("extract路径提取: path={}", jsonPath);
        
        try {
            JsonNode root = asJson();
            JsonNode result = navigateJsonPath(root, jsonPath);
            
            if (result == null || result.isNull()) {
                return null;
            }
            
            if (result.isValueNode()) {
                return extractValue(result);
            }
            
            return (T) JSON_MAPPER.treeToValue(result, Object.class);
        } catch (HttpParseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("extract提取失败: path={}, 错误: {}", jsonPath, e.getMessage());
            throw new HttpParseException("数据提取失败: " + jsonPath, e);
        }
    }
    
    /**
     * 从JSON响应体中提取指定路径的值并转换为指定类型。
     * <p>
     * 支持点分隔的路径表达式和数组索引，如"data.users[0].name"。
     * </p>
     *
     * @param <T> 提取值的类型
     * @param jsonPath JSON路径表达式
     * @param type 目标类型的Class对象
     * @return 提取并转换后的值
     * @throws HttpParseException 当路径无效或类型转换失败时抛出
     * @throws IllegalArgumentException 当路径为空或类型为null时抛出
     */
    @Override
    public <T> T extract(String jsonPath, Class<T> type) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON路径不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("目标类型不能为null");
        }
        
        logger.debug("extract路径提取(指定类型): path={}, type={}", jsonPath, type.getName());
        
        try {
            JsonNode root = asJson();
            JsonNode result = navigateJsonPath(root, jsonPath);
            
            if (result == null || result.isNull()) {
                return null;
            }
            
            return JSON_MAPPER.treeToValue(result, type);
        } catch (HttpParseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("extract提取失败: path={}, type={}, 错误: {}", jsonPath, type.getName(), e.getMessage());
            throw new HttpParseException("数据提取失败: " + jsonPath + " -> " + type.getName(), e);
        }
    }
    
    /**
     * 根据路径导航JSON节点树。
     * <p>
     * 支持点分隔的属性访问和方括号索引访问。
     * </p>
     *
     * @param root JSON根节点
     * @param path 点分隔的路径表达式
     * @return 导航到的JSON节点，如果路径不存在则返回null
     * @throws HttpParseException 当数组索引格式无效时抛出
     */
    private JsonNode navigateJsonPath(JsonNode root, String path) {
        JsonNode current = root;
        
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            
            String arraySegment = segment;
            int arrayIndex = -1;
            
            int bracketPos = segment.indexOf('[');
            if (bracketPos > 0) {
                String indexStr = segment.substring(bracketPos + 1, segment.length() - 1);
                try {
                    arrayIndex = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    throw new HttpParseException("无效的数组索引: " + indexStr);
                }
                arraySegment = segment.substring(0, bracketPos);
            } else if (segment.endsWith("]")) {
                bracketPos = segment.indexOf('[');
                if (bracketPos == 0) {
                    String indexStr = segment.substring(1, segment.length() - 1);
                    try {
                        arrayIndex = Integer.parseInt(indexStr);
                    } catch (NumberFormatException e) {
                        throw new HttpParseException("无效的数组索引: " + indexStr);
                    }
                    if (current.isArray() && arrayIndex >= 0 && arrayIndex < current.size()) {
                        current = current.get(arrayIndex);
                        continue;
                    }
                    return null;
                }
                arraySegment = segment.substring(0, bracketPos);
                String indexStr = segment.substring(bracketPos + 1, segment.length() - 1);
                try {
                    arrayIndex = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    throw new HttpParseException("无效的数组索引: " + indexStr);
                }
            }
            
            if (!arraySegment.isEmpty() && current.has(arraySegment)) {
                current = current.get(arraySegment);
            } else if (!arraySegment.isEmpty()) {
                return null;
            }
            
            if (arrayIndex >= 0) {
                if (current.isArray() && arrayIndex < current.size()) {
                    current = current.get(arrayIndex);
                } else {
                    return null;
                }
            }
        }
        
        return current;
    }
    
    /**
     * 从JSON值节点中提取原始Java值。
     *
     * @param <T> 提取值的类型
     * @param node JSON值节点
     * @return 提取的Java值（Boolean、Integer、Long、Double或String）
     */
    @SuppressWarnings("unchecked")
    private <T> T extractValue(JsonNode node) {
        if (node.isBoolean()) {
            return (T) Boolean.valueOf(node.asBoolean());
        }
        if (node.isInt()) {
            return (T) Integer.valueOf(node.asInt());
        }
        if (node.isLong()) {
            return (T) Long.valueOf(node.asLong());
        }
        if (node.isDouble()) {
            return (T) Double.valueOf(node.asDouble());
        }
        if (node.isTextual()) {
            return (T) node.asText();
        }
        return (T) node.asText();
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
        try {
            return parser.apply(asString());
        } catch (Exception e) {
            logger.error("自定义解析失败: {}", e.getMessage());
            throw new HttpParseException("自定义解析失败", e);
        }
    }
    
    /**
     * 获取响应体的原始字节数组副本。
     *
     * @return 响应体字节数组的副本
     */
    @Override
    public byte[] asBytes() {
        return bytes != null ? bytes.clone() : new byte[0];
    }
}
