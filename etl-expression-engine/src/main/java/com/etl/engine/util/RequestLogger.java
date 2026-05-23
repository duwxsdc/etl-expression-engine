package com.etl.engine.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RequestLogger {
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;
    
    private RequestLogger() {}
    
    public static String generateRequestId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public static String timestamp() {
        return TIMESTAMP_FORMAT.format(Instant.now());
    }
    
    public static void logRequest(Logger logger, String requestId, String endpoint, 
                                  String method, Map<String, Object> params) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", "REQUEST_RECEIVED");
        logEntry.put("endpoint", endpoint);
        logEntry.put("method", method);
        logEntry.put("params", params);
        logEntry.put("status", "STARTED");
        
        logger.info(toJson(logEntry));
    }
    
    public static void logStage(Logger logger, String requestId, String stage, 
                                String message, Map<String, Object> details) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", stage);
        logEntry.put("message", message);
        if (details != null && !details.isEmpty()) {
            logEntry.putAll(details);
        }
        
        logger.info(toJson(logEntry));
    }
    
    public static void logDebug(Logger logger, String requestId, String stage,
                                String message, Map<String, Object> details) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", stage);
        logEntry.put("message", message);
        if (details != null && !details.isEmpty()) {
            logEntry.putAll(details);
        }
        
        logger.debug(toJson(logEntry));
    }
    
    public static void logSuccess(Logger logger, String requestId, String endpoint,
                                  long durationMs, Map<String, Object> result) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", "REQUEST_COMPLETED");
        logEntry.put("endpoint", endpoint);
        logEntry.put("status", "SUCCESS");
        logEntry.put("durationMs", durationMs);
        if (result != null) {
            logEntry.put("result", result);
        }
        
        logger.info(toJson(logEntry));
    }
    
    public static void logError(Logger logger, String requestId, String stage,
                                String error, Throwable exception) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", stage);
        logEntry.put("status", "ERROR");
        logEntry.put("error", error);
        if (exception != null) {
            logEntry.put("exceptionType", exception.getClass().getSimpleName());
            logEntry.put("exceptionMessage", exception.getMessage());
        }
        
        logger.error(toJson(logEntry));
    }
    
    public static void logWarn(Logger logger, String requestId, String stage,
                               String message, Map<String, Object> details) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", timestamp());
        logEntry.put("requestId", requestId);
        logEntry.put("stage", stage);
        logEntry.put("status", "WARNING");
        logEntry.put("message", message);
        if (details != null && !details.isEmpty()) {
            logEntry.putAll(details);
        }
        
        logger.warn(toJson(logEntry));
    }
    
    private static String toJson(Map<String, Object> map) {
        try {
            return JSON_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
