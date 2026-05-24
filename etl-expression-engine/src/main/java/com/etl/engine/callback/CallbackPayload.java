package com.etl.engine.callback;

import java.util.Map;

public record CallbackPayload(
        String callbackId,
        String event,
        String timestamp,
        String status,
        Map<String, Object> metadata,
        Object data
) {}
