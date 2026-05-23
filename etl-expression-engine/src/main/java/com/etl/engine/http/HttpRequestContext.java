package com.etl.engine.http;

import java.util.Map;

public final class HttpRequestContext {
    
    private String method;
    private String url;
    private Map<String, String> headers;
    private byte[] body;
    private int timeout;
    private int retryCount;
    private long startTime;
    private long endTime;
    
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }
    
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    public long getDuration() {
        return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }
    
    @Override
    public String toString() {
        return "HttpRequestContext{method='" + method + "', url='" + url + 
               "', timeout=" + timeout + ", retryCount=" + retryCount + '}';
    }
}
