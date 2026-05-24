package com.etl.engine.callback;

public record CallbackStatus(
        String callbackId,
        String callbackUrl,
        CallbackState state,
        long createdAt,
        int retryCount,
        Integer statusCode,
        String errorMessage
) {}
