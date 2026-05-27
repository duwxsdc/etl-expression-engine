package com.etl.engine.rest.enhanced;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class SigningExtension implements RestClientExtension {
    
    public static final String NAME = "signing";
    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    
    private final String secretKey;
    private final String signatureHeader;
    
    public SigningExtension() {
        this("default-secret-key");
    }
    
    public SigningExtension(String secretKey) {
        this(secretKey, SIGNATURE_HEADER);
    }
    
    public SigningExtension(String secretKey, String signatureHeader) {
        this.secretKey = secretKey;
        this.signatureHeader = signatureHeader;
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public int order() {
        return 400;
    }
    
    @Override
    public void apply(ExtensionContext context) {
        long timestamp = System.currentTimeMillis();
        String url = context.getUrl();
        String method = context.getMethod();
        Object body = context.getBody();
        
        String dataToSign = method + url + timestamp + (body != null ? body.toString() : "");
        String signature = generateSignature(dataToSign, secretKey);
        
        context.addHeader(signatureHeader, signature);
        context.addHeader(TIMESTAMP_HEADER, String.valueOf(timestamp));
    }
    
    private String generateSignature(String data, String secret) {
        try {
            String dataWithSecret = data + secret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(dataWithSecret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
}