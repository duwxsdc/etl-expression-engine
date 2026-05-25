package com.etl.engine.http;

import java.util.Map;

/**
 * HTTP请求上下文类，封装了HTTP请求的完整上下文信息。
 * <p>
 * 该类在HTTP请求的整个生命周期中使用，包含请求方法、URL、头信息、请求体、
 * 超时设置、重试次数以及时间信息。拦截器可以通过此上下文访问和修改请求信息。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class HttpRequestContext {
    
    /**
     * HTTP请求方法（如GET、POST等）
     */
    private String method;
    
    /**
     * 请求的URL地址
     */
    private String url;
    
    /**
     * 请求头映射
     */
    private Map<String, String> headers;
    
    /**
     * 请求体字节数组
     */
    private byte[] body;
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout;
    
    /**
     * 当前重试次数
     */
    private int retryCount;
    
    /**
     * 请求开始时间（时间戳毫秒）
     */
    private long startTime;
    
    /**
     * 请求结束时间（时间戳毫秒）
     */
    private long endTime;
    
    /**
     * 获取HTTP请求方法。
     *
     * @return HTTP请求方法
     */
    public String getMethod() { return method; }
    
    /**
     * 设置HTTP请求方法。
     *
     * @param method HTTP请求方法
     */
    public void setMethod(String method) { this.method = method; }
    
    /**
     * 获取请求URL地址。
     *
     * @return 请求URL地址
     */
    public String getUrl() { return url; }
    
    /**
     * 设置请求URL地址。
     *
     * @param url 请求URL地址
     */
    public void setUrl(String url) { this.url = url; }
    
    /**
     * 获取请求头映射。
     *
     * @return 请求头映射
     */
    public Map<String, String> getHeaders() { return headers; }
    
    /**
     * 设置请求头映射。
     *
     * @param headers 请求头映射
     */
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    
    /**
     * 获取请求体字节数组。
     *
     * @return 请求体字节数组
     */
    public byte[] getBody() { return body; }
    
    /**
     * 设置请求体字节数组。
     *
     * @param body 请求体字节数组
     */
    public void setBody(byte[] body) { this.body = body; }
    
    /**
     * 获取请求超时时间。
     *
     * @return 超时时间（毫秒）
     */
    public int getTimeout() { return timeout; }
    
    /**
     * 设置请求超时时间。
     *
     * @param timeout 超时时间（毫秒）
     */
    public void setTimeout(int timeout) { this.timeout = timeout; }
    
    /**
     * 获取当前重试次数。
     *
     * @return 重试次数
     */
    public int getRetryCount() { return retryCount; }
    
    /**
     * 设置当前重试次数。
     *
     * @param retryCount 重试次数
     */
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    /**
     * 获取请求开始时间。
     *
     * @return 开始时间（时间戳毫秒）
     */
    public long getStartTime() { return startTime; }
    
    /**
     * 设置请求开始时间。
     *
     * @param startTime 开始时间（时间戳毫秒）
     */
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    /**
     * 获取请求结束时间。
     *
     * @return 结束时间（时间戳毫秒）
     */
    public long getEndTime() { return endTime; }
    
    /**
     * 设置请求结束时间。
     *
     * @param endTime 结束时间（时间戳毫秒）
     */
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    /**
     * 获取请求持续时间。
     * <p>
     * 如果请求已结束，返回开始时间到结束时间的差值；
     * 如果请求未结束，返回开始时间到当前时间的差值。
     * </p>
     *
     * @return 持续时间（毫秒）
     */
    public long getDuration() {
        return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }
    
    /**
     * 返回请求上下文的字符串表示。
     *
     * @return 包含请求方法、URL、超时和重试次数的字符串
     */
    @Override
    public String toString() {
        return "HttpRequestContext{method='" + method + "', url='" + url + 
               "', timeout=" + timeout + ", retryCount=" + retryCount + '}';
    }
}
