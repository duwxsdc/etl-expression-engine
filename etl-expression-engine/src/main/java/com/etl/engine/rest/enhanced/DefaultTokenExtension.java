package com.etl.engine.rest.enhanced;

import java.util.function.Function;

public final class DefaultTokenExtension implements RestClientExtension {
    
    public static final String NAME = "defaultToken";
    public static final String DEFAULT_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    
    private final Function<String, String> tokenProvider;
    private final String headerName;
    private final String tokenPrefix;
    
    public DefaultTokenExtension() {
        this(url -> null, DEFAULT_HEADER, TOKEN_PREFIX);
    }
    
    public DefaultTokenExtension(Function<String, String> tokenProvider) {
        this(tokenProvider, DEFAULT_HEADER, TOKEN_PREFIX);
    }
    
    public DefaultTokenExtension(Function<String, String> tokenProvider, String headerName, String tokenPrefix) {
        this.tokenProvider = tokenProvider;
        this.headerName = headerName;
        this.tokenPrefix = tokenPrefix != null ? tokenPrefix : "";
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 100;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        String url = context.getUrl();
        String token = tokenProvider.apply(url);
        
        if (token == null || token.isEmpty()) {
            token = context.getAttribute("staticToken");
        }
        
        if (token != null && !token.isEmpty()) {
            String headerValue = tokenPrefix + token;
            context.addHeader(headerName, headerValue);
        }
    }
    
    public static DefaultTokenExtension withStaticToken(String token) {
        return new DefaultTokenExtension(url -> token);
    }
    
    public static DefaultTokenExtension withStaticToken(String token, String headerName) {
        return new DefaultTokenExtension(url -> token, headerName, "");
    }
    
    public static DefaultTokenExtension withBearerToken(String token) {
        return new DefaultTokenExtension(url -> token, DEFAULT_HEADER, TOKEN_PREFIX);
    }
}