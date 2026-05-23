package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Map;
import java.util.function.Function;

public sealed interface ResponseBody permits ResponseBodyImpl {
    
    String asString();
    
    JsonNode asJson();
    
    Map<String, Object> asMap();
    
    Document asXml();
    
    <T> T asBean(Class<T> clazz);
    
    <T> T custom(Function<String, T> parser);
    
    byte[] asBytes();
}