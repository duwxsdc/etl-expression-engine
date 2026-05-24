package com.etl.engine.callback;

import java.util.Map;

public class CallbackRequest {
    private String callbackUrl;
    private String triggerEvent;
    private String method;
    private Map<String, String> headers;
    private Object data;
    private Integer maxRetries;
    private Long retryIntervalMs;
    
    public CallbackRequest() {}
    
    public CallbackRequest(String callbackUrl, String triggerEvent, String method,
                           Map<String, String> headers, Object data, 
                           Integer maxRetries, Long retryIntervalMs) {
        this.callbackUrl = callbackUrl;
        this.triggerEvent = triggerEvent;
        this.method = method;
        this.headers = headers;
        this.data = data;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
    }
    
    public static CallbackRequest of(String url, String event, Object data) {
        return new CallbackRequest(url, event, "POST", null, data, 3, 1000L);
    }
    
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public Long getRetryIntervalMs() { return retryIntervalMs; }
    public void setRetryIntervalMs(Long retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }
}
