package com.etl.engine.crypto;

public record KeyRotationPolicy(int rotationDays, boolean autoRotate) {
    
    public KeyRotationPolicy {
        if (rotationDays <= 0) {
            throw new IllegalArgumentException("轮换天数必须大于0");
        }
    }
    
    public static KeyRotationPolicy of(int rotationDays) {
        return new KeyRotationPolicy(rotationDays, true);
    }
    
    public static KeyRotationPolicy of(int rotationDays, boolean autoRotate) {
        return new KeyRotationPolicy(rotationDays, autoRotate);
    }
}
