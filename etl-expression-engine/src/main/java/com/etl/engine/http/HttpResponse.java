package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Map;
import java.util.function.Function;

public sealed interface HttpResponse permits HttpResponseImpl {
    
    int statusCode();
    
    String statusMessage();
    
    boolean isSuccess();
    
    boolean isRedirect();
    
    boolean isClientError();
    
    boolean isServerError();
    
    Map<String, String> headers();
    
    String header(String name);
    
    String asString();
    
    JsonNode asJson();
    
    Map<String, Object> asMap();
    
    Document asXml();
    
    <T> T asBean(Class<T> clazz);
    
    <T> T custom(Function<String, T> parser);
    
    byte[] asBytes();
    
    long contentLength();
    
    String contentType();
    
    ResponseBody body();
}
