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

public final class ResponseBodyImpl implements ResponseBody {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseBodyImpl.class);
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    private static final DocumentBuilderFactory DOC_FACTORY = DocumentBuilderFactory.newInstance();
    
    private final byte[] bytes;
    private final String contentType;
    private volatile String cachedString;
    private final String cachedStringSafe;
    
    ResponseBodyImpl(byte[] bytes, String contentType) {
        this.bytes = bytes;
        this.contentType = contentType;
        this.cachedStringSafe = bytes != null 
                ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) 
                : "";
    }
    
    @Override
    public String asString() {
        String result = cachedString;
        if (result == null) {
            result = cachedStringSafe;
            cachedString = result;
        }
        return result;
    }
    
    @Override
    public JsonNode asJson() {
        try {
            return JSON_MAPPER.readTree(asString());
        } catch (Exception e) {
            logger.error("JSON解析失败: {}", e.getMessage());
            throw new HttpParseException("JSON解析失败", e);
        }
    }
    
    @Override
    public Map<String, Object> asMap() {
        try {
            return JSON_MAPPER.readValue(asString(), new TypeReference<>() {});
        } catch (Exception e) {
            logger.error("Map解析失败: {}", e.getMessage());
            throw new HttpParseException("Map解析失败", e);
        }
    }
    
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
    
    @Override
    public <T> T asBean(Class<T> clazz) {
        try {
            return JSON_MAPPER.readValue(asString(), clazz);
        } catch (Exception e) {
            logger.error("Bean解析失败, 目标类型: {}, 错误: {}", clazz.getName(), e.getMessage());
            throw new HttpParseException("Bean解析失败: " + clazz.getName(), e);
        }
    }
    
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
    
    @Override
    public <T> T custom(Function<String, T> parser) {
        try {
            return parser.apply(asString());
        } catch (Exception e) {
            logger.error("自定义解析失败: {}", e.getMessage());
            throw new HttpParseException("自定义解析失败", e);
        }
    }
    
    @Override
    public byte[] asBytes() {
        return bytes != null ? bytes.clone() : new byte[0];
    }
}
