package com.etl.engine.rest.enhanced;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enhanced.rest-client")
public class EnhancedRestClientProperties {
    
    private boolean defaultTokenEnabled = false;
    private String defaultToken;
    private String tokenHeader = "Authorization";
    private String tokenPrefix = "Bearer ";
    
    private boolean callbackEnabled = false;
    private String callbackTargetIp;
    private int callbackTargetPort = 8080;
    private long callbackDefaultTimeoutMs = 30000;
    private int callbackMaxPending = 10000;
    
    public boolean isDefaultTokenEnabled() {
        return defaultTokenEnabled;
    }
    
    public void setDefaultTokenEnabled(boolean defaultTokenEnabled) {
        this.defaultTokenEnabled = defaultTokenEnabled;
    }
    
    public String getDefaultToken() {
        return defaultToken;
    }
    
    public void setDefaultToken(String defaultToken) {
        this.defaultToken = defaultToken;
    }
    
    public String getTokenHeader() {
        return tokenHeader;
    }
    
    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }
    
    public String getTokenPrefix() {
        return tokenPrefix;
    }
    
    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }
    
    public boolean isCallbackEnabled() {
        return callbackEnabled;
    }
    
    public void setCallbackEnabled(boolean callbackEnabled) {
        this.callbackEnabled = callbackEnabled;
    }
    
    public String getCallbackTargetIp() {
        return callbackTargetIp;
    }
    
    public void setCallbackTargetIp(String callbackTargetIp) {
        this.callbackTargetIp = callbackTargetIp;
    }
    
    public int getCallbackTargetPort() {
        return callbackTargetPort;
    }
    
    public void setCallbackTargetPort(int callbackTargetPort) {
        this.callbackTargetPort = callbackTargetPort;
    }
    
    public long getCallbackDefaultTimeoutMs() {
        return callbackDefaultTimeoutMs;
    }
    
    public void setCallbackDefaultTimeoutMs(long callbackDefaultTimeoutMs) {
        this.callbackDefaultTimeoutMs = callbackDefaultTimeoutMs;
    }
    
    public int getCallbackMaxPending() {
        return callbackMaxPending;
    }
    
    public void setCallbackMaxPending(int callbackMaxPending) {
        this.callbackMaxPending = callbackMaxPending;
    }
}