package com.etl.engine.callback;

public record CallbackLog(
        String logId,
        String callbackId,
        String callbackUrl,
        String triggerEvent,
        String requestPayload,
        Integer responseCode,
        String responseBody,
        String status,
        String errorMessage,
        int retryCount,
        long timestamp
) {}
