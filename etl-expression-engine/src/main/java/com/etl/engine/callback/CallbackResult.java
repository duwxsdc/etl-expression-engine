package com.etl.engine.callback;

public record CallbackResult(
        boolean success,
        int statusCode,
        String responseBody,
        String errorMessage
) {}
