package com.etl.engine.rest.enhanced;

import java.util.UUID;

public final class TracingExtension implements RestClientExtension {
    
    public static final String NAME = "tracing";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    
    private final String traceIdHeader;
    private final String spanIdHeader;
    
    public TracingExtension() {
        this(TRACE_ID_HEADER, SPAN_ID_HEADER);
    }
    
    public TracingExtension(String traceIdHeader, String spanIdHeader) {
        this.traceIdHeader = traceIdHeader;
        this.spanIdHeader = spanIdHeader;
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 5;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        String traceId = context.getAttribute("traceId");
        if (traceId == null) {
            traceId = generateTraceId();
            context.setAttribute("traceId", traceId);
        }
        
        String spanId = generateSpanId();
        
        context.addHeader(traceIdHeader, traceId);
        context.addHeader(spanIdHeader, spanId);
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}