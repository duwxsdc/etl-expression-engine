package com.etl.engine.callback;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallbackRecord(
    String eventId,
    String targetIp,
    int targetPort,
    Object payload,
    String status,
    String message,
    Instant timestamp,
    Map<String, Object> metadata
) {
    
    public static CallbackRecord of(String eventId, String targetIp, int targetPort, Object payload) {
        return new CallbackRecord(eventId, targetIp, targetPort, payload, "SUCCESS", null, Instant.now(), null);
    }
    
    public static CallbackRecord success(String eventId, String targetIp, int targetPort, Object payload) {
        return new CallbackRecord(eventId, targetIp, targetPort, payload, "SUCCESS", null, Instant.now(), null);
    }
    
    public static CallbackRecord error(String eventId, String targetIp, int targetPort, String message) {
        return new CallbackRecord(eventId, targetIp, targetPort, null, "ERROR", message, Instant.now(), null);
    }
    
    public static CallbackRecord forward(String eventId, String targetIp, int targetPort, Object payload, String status) {
        return new CallbackRecord(eventId, targetIp, targetPort, payload, status, null, Instant.now(), null);
    }
    
    public CallbackRecord withPayload(Object newPayload) {
        return new CallbackRecord(eventId, targetIp, targetPort, newPayload, status, message, timestamp, metadata);
    }
    
    public CallbackRecord withStatus(String newStatus) {
        return new CallbackRecord(eventId, targetIp, targetPort, payload, newStatus, message, timestamp, metadata);
    }
    
    public boolean isLocalNode(String localIp, int localPort) {
        return targetIp != null && targetIp.equals(localIp) && targetPort == localPort;
    }
}
