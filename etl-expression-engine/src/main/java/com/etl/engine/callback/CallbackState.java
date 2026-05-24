package com.etl.engine.callback;

public enum CallbackState {
    PENDING,
    PROCESSING,
    COMPLETED,
    RETRYING,
    FAILED,
    TIMEOUT
}
