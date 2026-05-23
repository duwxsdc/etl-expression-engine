package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public final class HttpResponseImpl implements HttpResponse {
    
    private final int statusCode;
    private final String statusMessage;
    private final Map<String, String> headers;
    private final ResponseBody body;
    
    HttpResponseImpl(int statusCode, String statusMessage, Map<String, String> headers, byte[] bodyBytes, String contentType) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = new ResponseBodyImpl(bodyBytes, contentType);
    }
    
    @Override
    public int statusCode() {
        return statusCode;
    }
    
    @Override
    public String statusMessage() {
        return statusMessage;
    }
    
    @Override
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    @Override
    public boolean isRedirect() {
        return statusCode >= 300 && statusCode < 400;
    }
    
    @Override
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    @Override
    public boolean isServerError() {
        return statusCode >= 500;
    }
    
    @Override
    public Map<String, String> headers() {
        return headers;
    }
    
    @Override
    public String header(String name) {
        return headers.get(name);
    }
    
    @Override
    public String asString() {
        return body.asString();
    }
    
    @Override
    public JsonNode asJson() {
        return body.asJson();
    }
    
    @Override
    public Map<String, Object> asMap() {
        return body.asMap();
    }
    
    @Override
    public Document asXml() {
        return body.asXml();
    }
    
    @Override
    public <T> T asBean(Class<T> clazz) {
        return body.asBean(clazz);
    }
    
    @Override
    public <T> T custom(Function<String, T> parser) {
        return body.custom(parser);
    }
    
    @Override
    public byte[] asBytes() {
        return body.asBytes();
    }
    
    @Override
    public long contentLength() {
        String length = headers.get("Content-Length");
        return length != null ? Long.parseLong(length) : -1;
    }
    
    @Override
    public String contentType() {
        return headers.get("Content-Type");
    }
    
    @Override
    public ResponseBody body() {
        return body;
    }
    
    @Override
    public String toString() {
        return "HttpResponse{statusCode=" + statusCode + 
               ", statusMessage='" + statusMessage + '\'' +
               ", headers=" + headers.size() + " entries" +
               ", body=" + (body != null ? body.asString().length() + " chars" : "null") +
               '}';
    }
}