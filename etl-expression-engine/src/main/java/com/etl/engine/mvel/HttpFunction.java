package com.etl.engine.mvel;

import com.etl.engine.http.HttpClientAdapter;
import com.etl.engine.http.HttpRequestBuilder;
import com.etl.engine.http.HttpRequestBuilderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpFunction {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpFunction.class);
    
    private static volatile boolean initialized = false;
    
    private HttpFunction() {
    }
    
    public static void init() {
        if (!initialized) {
            initialized = true;
            logger.info("HTTP函数初始化完成");
        }
    }
    
    public static void init(HttpClientAdapter adapter) {
        if (!initialized) {
            HttpRequestBuilderImpl.setClientAdapter(adapter);
            initialized = true;
            logger.info("HTTP函数初始化完成, 客户端: {}", adapter.getName());
        }
    }
    
    public static HttpRequestBuilder httpRequest(String url) {
        if (!initialized) {
            init();
        }
        logger.debug("MVEL HTTP函数调用: {}", url);
        return new HttpRequestBuilderImpl(url);
    }
    
    public static HttpRequestBuilder http(String url) {
        return httpRequest(url);
    }
    
    public static HttpRequestBuilder get(String url) {
        return httpRequest(url);
    }
    
    public static HttpRequestBuilder post(String url) {
        return httpRequest(url);
    }
    
    public static void shutdown() {
        HttpRequestBuilderImpl.shutdown();
        initialized = false;
        logger.info("HTTP函数已关闭");
    }
}
