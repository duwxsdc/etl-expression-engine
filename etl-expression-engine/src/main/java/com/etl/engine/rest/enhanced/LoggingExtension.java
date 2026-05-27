package com.etl.engine.rest.enhanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingExtension implements RestClientExtension {
    
    public static final String NAME = "logging";
    private static final Logger logger = LoggerFactory.getLogger(LoggingExtension.class);
    
    private final boolean logHeaders;
    private final boolean logBody;
    
    public LoggingExtension() {
        this(true, false);
    }
    
    public LoggingExtension(boolean logHeaders, boolean logBody) {
        this.logHeaders = logHeaders;
        this.logBody = logBody;
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 10;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        StringBuilder log = new StringBuilder();
        log.append("[HTTP] ").append(context.getMethod()).append(" ").append(context.getUrl());
        
        if (logHeaders && !context.getHeaders().isEmpty()) {
            log.append(" | Headers: ").append(context.getHeaders());
        }
        
        if (logBody && context.getBody() != null) {
            log.append(" | Body: ").append(context.getBody());
        }
        
        logger.info(log.toString());
    }
}