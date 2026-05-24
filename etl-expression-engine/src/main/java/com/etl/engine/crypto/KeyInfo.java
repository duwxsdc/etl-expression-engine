package com.etl.engine.crypto;

public class KeyInfo {
    
    private final String keyId;
    private final String algorithm;
    private final int keySize;
    private final String encryptedKey;
    private final long createdAt;
    private final String publicKey;
    private boolean active;
    
    public KeyInfo(String keyId, String algorithm, int keySize, 
                   String encryptedKey, long createdAt, String publicKey, boolean active) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.keySize = keySize;
        this.encryptedKey = encryptedKey;
        this.createdAt = createdAt;
        this.publicKey = publicKey;
        this.active = active;
    }
    
    public String getKeyId() { return keyId; }
    public String getAlgorithm() { return algorithm; }
    public int getKeySize() { return keySize; }
    public String getEncryptedKey() { return encryptedKey; }
    public long getCreatedAt() { return createdAt; }
    public String getPublicKey() { return publicKey; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    @Override
    public String toString() {
        return "KeyInfo{" +
                "keyId='" + keyId + '\'' +
                ", algorithm='" + algorithm + '\'' +
                ", keySize=" + keySize +
                ", createdAt=" + createdAt +
                ", active=" + active +
                '}';
    }
}
