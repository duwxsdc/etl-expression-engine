package com.etl.engine.http;

import com.fasterxml.jackson.core.type.TypeReference;
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
