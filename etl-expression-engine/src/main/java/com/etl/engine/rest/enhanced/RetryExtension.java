package com.etl.engine.rest.enhanced;

import java.util.concurrent.atomic.AtomicInteger;

public final class RetryExtension implements RestClientExtension {
    
    public static final String NAME = "retry";
    
    private final int maxRetries;
    private final long retryDelayMs;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    
    public RetryExtension() {
        this(3, 1000);
    }
    
    public RetryExtension(int maxRetries, long retryDelayMs) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 300;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        context.setAttribute("maxRetries", maxRetries);
        context.setAttribute("retryDelayMs", retryDelayMs);
        context.setAttribute("retryCount", retryCount.get());
    }
    
    public int getRetryCount() {
        return retryCount.get();
    }
    
    public void incrementRetry() {
        retryCount.incrementAndGet();
    }
    
    public void reset() {
        retryCount.set(0);
    }
}