package com.etl.engine.rest.enhanced;

public final class CallbackExtension implements RestClientExtension {
    
    public static final String NAME = "callback";
    public static final String EVENT_ID_HEADER = "X-Callback-EventId";
    public static final String TARGET_IP_HEADER = "X-Callback-TargetIp";
    public static final String TARGET_PORT_HEADER = "X-Callback-TargetPort";
    
    private final String targetIp;
    private final int targetPort;
    
    public CallbackExtension() {
        this(null, 0);
    }
    
    public CallbackExtension(String targetIp, int targetPort) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 200;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        CallbackManager callbackManager = CallbackManager.getInstance();
        
        String eventId = context.getAttribute("callbackEventId");
        if (eventId == null) {
            eventId = callbackManager.generateEventId();
            context.setAttribute("callbackEventId", eventId);
        }
        
        String ip = targetIp;
        if (ip == null) {
            ip = context.getAttribute("callbackTargetIp");
        }
        if (ip == null) {
            ip = "127.0.0.1";
        }
        
        Integer port = context.getAttribute("callbackTargetPort");
        if (port == null) {
            port = targetPort > 0 ? targetPort : 8080;
        }
        
        context.addHeader(EVENT_ID_HEADER, eventId);
        context.addHeader(TARGET_IP_HEADER, ip);
        context.addHeader(TARGET_PORT_HEADER, String.valueOf(port));
    }
}