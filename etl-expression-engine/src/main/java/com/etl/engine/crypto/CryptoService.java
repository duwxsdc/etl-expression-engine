package com.etl.engine.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CryptoService {
    
    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);
    
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int RSA_KEY_SIZE = 2048;
    
    private final Map<String, KeyInfo> keyStore = new ConcurrentHashMap<>();
    private final Map<String, KeyRotationPolicy> rotationPolicies = new ConcurrentHashMap<>();
    private final Map<String, String> keyContextMap = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom;
    
    public CryptoService() {
        this.secureRandom = new SecureRandom();
    }
    
    public String generateAesKey(String keyId) {
        return generateAesKey(keyId, AES_KEY_SIZE);
    }
    
    public String generateAesKey(String keyId, int keySize) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(keySize, secureRandom);
            SecretKey secretKey = keyGen.generateKey();
            
            byte[] encodedKey = secretKey.getEncoded();
            String encryptedKey = encryptKeyStorage(encodedKey, keyId);
            Arrays.fill(encodedKey, (byte) 0);
            
            keyContextMap.put(keyId, keyId);
            
            KeyInfo keyInfo = new KeyInfo(
                    keyId,
                    "AES",
                    keySize,
                    encryptedKey,
                    Instant.now().getEpochSecond(),
                    null,
                    true
            );
            
            keyStore.put(keyId, keyInfo);
            logger.info("生成AES密钥: id={}, size={}", keyId, keySize);
            
            return keyId;
            
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("AES密钥生成失败", e);
        }
    }
    
    public KeyPairResult generateRsaKeyPair(String keyId) {
        return generateRsaKeyPair(keyId, RSA_KEY_SIZE);
    }
    
    public KeyPairResult generateRsaKeyPair(String keyId, int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySize, secureRandom);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            byte[] encodedPrivateKey = keyPair.getPrivate().getEncoded();
            byte[] encodedPublicKey = keyPair.getPublic().getEncoded();
            
            String encryptedPrivateKey = encryptKeyStorage(encodedPrivateKey, keyId + "_private");
            String publicKeyBase64 = Base64.getEncoder().encodeToString(encodedPublicKey);
            
            Arrays.fill(encodedPrivateKey, (byte) 0);
            
            keyContextMap.put(keyId, keyId + "_private");
            
            KeyInfo keyInfo = new KeyInfo(
                    keyId,
                    "RSA",
                    keySize,
                    encryptedPrivateKey,
                    Instant.now().getEpochSecond(),
                    publicKeyBase64,
                    true
            );
            
            keyStore.put(keyId, keyInfo);
            logger.info("生成RSA密钥对: id={}, size={}", keyId, keySize);
            
            return new KeyPairResult(keyId, publicKeyBase64);
            
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("RSA密钥对生成失败", e);
        }
    }
    
    public String aesEncrypt(String keyId, String plaintext) {
        return aesEncrypt(keyId, plaintext.getBytes(StandardCharsets.UTF_8));
    }
    
    public String aesEncrypt(String keyId, byte[] plaintext) {
        KeyInfo keyInfo = getKeyInfoOrThrow(keyId);
        
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            SecretKey secretKey = getAesSecretKey(keyInfo);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            String result = Base64.getEncoder().encodeToString(combined);
            logger.debug("AES加密成功: keyId={}, inputLen={}, outputLen={}", 
                    keyId, plaintext.length, combined.length);
            
            return result;
            
        } catch (Exception e) {
            throw new CryptoException("AES加密失败: " + e.getMessage(), e);
        }
    }
    
    public byte[] aesDecrypt(String keyId, String ciphertext) {
        KeyInfo keyInfo = getKeyInfoOrThrow(keyId);
        
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            SecretKey secretKey = getAesSecretKey(keyInfo);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            byte[] plaintext = cipher.doFinal(encrypted);
            logger.debug("AES解密成功: keyId={}, outputLen={}", keyId, plaintext.length);
            
            return plaintext;
            
        } catch (Exception e) {
            throw new CryptoException("AES解密失败: " + e.getMessage(), e);
        }
    }
    
    public String aesDecryptToString(String keyId, String ciphertext) {
        return new String(aesDecrypt(keyId, ciphertext), StandardCharsets.UTF_8);
    }
    
    public String rsaEncrypt(String keyId, String plaintext) {
        KeyInfo keyInfo = getKeyInfoOrThrow(keyId);
        
        if (!"RSA".equals(keyInfo.getAlgorithm())) {
            throw new CryptoException("密钥类型不匹配，期望RSA，实际为: " + keyInfo.getAlgorithm());
        }
        
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(keyInfo.getPublicKey());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String result = Base64.getEncoder().encodeToString(encrypted);
            
            logger.debug("RSA加密成功: keyId={}, inputLen={}", keyId, plaintext.length());
            return result;
            
        } catch (Exception e) {
            throw new CryptoException("RSA加密失败: " + e.getMessage(), e);
        }
    }
    
    public String rsaDecrypt(String keyId, String ciphertext) {
        KeyInfo keyInfo = getKeyInfoOrThrow(keyId);
        
        if (!"RSA".equals(keyInfo.getAlgorithm())) {
            throw new CryptoException("密钥类型不匹配，期望RSA，实际为: " + keyInfo.getAlgorithm());
        }
        
        try {
            String context = keyContextMap.getOrDefault(keyId, keyId + "_private");
            byte[] privateKeyBytes = decryptKeyStorage(keyInfo.getEncryptedKey(), context);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            
            Arrays.fill(privateKeyBytes, (byte) 0);
            
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            String result = new String(decrypted, StandardCharsets.UTF_8);
            
            logger.debug("RSA解密成功: keyId={}", keyId);
            return result;
            
        } catch (Exception e) {
            throw new CryptoException("RSA解密失败: " + e.getMessage(), e);
        }
    }
    
    public void setKeyRotationPolicy(String keyId, KeyRotationPolicy policy) {
        rotationPolicies.put(keyId, policy);
        logger.info("设置密钥轮换策略: keyId={}, rotationDays={}", keyId, policy.rotationDays());
    }
    
    public void rotateKey(String keyId) {
        KeyInfo oldKey = keyStore.get(keyId);
        if (oldKey == null) {
            throw new CryptoException("密钥不存在: " + keyId);
        }
        
        String oldKeyId = keyId + "_backup_" + Instant.now().getEpochSecond();
        keyStore.put(oldKeyId, oldKey);
        
        if ("AES".equals(oldKey.getAlgorithm())) {
            generateAesKey(keyId, oldKey.getKeySize());
        } else if ("RSA".equals(oldKey.getAlgorithm())) {
            generateRsaKeyPair(keyId, oldKey.getKeySize());
        }
        
        logger.info("密钥轮换完成: keyId={}, oldKeyId={}", keyId, oldKeyId);
    }
    
    public void checkAndRotateKeys() {
        long now = Instant.now().getEpochSecond();
        
        rotationPolicies.forEach((keyId, policy) -> {
            KeyInfo keyInfo = keyStore.get(keyId);
            if (keyInfo != null) {
                long ageSeconds = now - keyInfo.getCreatedAt();
                long rotationSeconds = policy.rotationDays() * 24 * 3600L;
                
                if (ageSeconds >= rotationSeconds) {
                    logger.info("检测到密钥需要轮换: keyId={}, age={}天", 
                            keyId, ageSeconds / (24 * 3600));
                    rotateKey(keyId);
                }
            }
        });
    }
    
    public void deleteKey(String keyId) {
        KeyInfo removed = keyStore.remove(keyId);
        if (removed != null) {
            secureDelete(removed);
            logger.info("密钥已删除: keyId={}", keyId);
        }
        rotationPolicies.remove(keyId);
        keyContextMap.remove(keyId);
    }
    
    public boolean hasKey(String keyId) {
        return keyStore.containsKey(keyId);
    }
    
    public Optional<KeyInfo> getKeyInfo(String keyId) {
        return Optional.ofNullable(keyStore.get(keyId));
    }
    
    public Map<String, Object> getKeyStatus(String keyId) {
        KeyInfo keyInfo = keyStore.get(keyId);
        if (keyInfo == null) {
            return Map.of("exists", false);
        }
        
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("exists", true);
        status.put("keyId", keyInfo.getKeyId());
        status.put("algorithm", keyInfo.getAlgorithm());
        status.put("keySize", keyInfo.getKeySize());
        status.put("createdAt", keyInfo.getCreatedAt());
        status.put("active", keyInfo.isActive());
        
        KeyRotationPolicy policy = rotationPolicies.get(keyId);
        if (policy != null) {
            long ageSeconds = Instant.now().getEpochSecond() - keyInfo.getCreatedAt();
            long rotationSeconds = policy.rotationDays() * 24 * 3600L;
            status.put("rotationDays", policy.rotationDays());
            status.put("ageDays", ageSeconds / (24 * 3600));
            status.put("needsRotation", ageSeconds >= rotationSeconds);
        }
        
        return status;
    }
    
    public CryptoBenchmarkResult runBenchmark() {
        logger.info("开始加解密性能基准测试...");
        
        String aesKeyId = "benchmark_aes";
        String rsaKeyId = "benchmark_rsa";
        
        try {
            generateAesKey(aesKeyId);
            generateRsaKeyPair(rsaKeyId);
            
            String testData = "A".repeat(1024);
            int iterations = 100;
            
            long aesEncryptStart = System.nanoTime();
            String aesEncrypted = null;
            for (int i = 0; i < iterations; i++) {
                aesEncrypted = aesEncrypt(aesKeyId, testData);
            }
            long aesEncryptTime = System.nanoTime() - aesEncryptStart;
            
            long aesDecryptStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                aesDecrypt(aesKeyId, aesEncrypted);
            }
            long aesDecryptTime = System.nanoTime() - aesDecryptStart;
            
            long rsaEncryptStart = System.nanoTime();
            String rsaEncrypted = null;
            for (int i = 0; i < iterations; i++) {
                rsaEncrypted = rsaEncrypt(rsaKeyId, testData.substring(0, 100));
            }
            long rsaEncryptTime = System.nanoTime() - rsaEncryptStart;
            
            long rsaDecryptStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                rsaDecrypt(rsaKeyId, rsaEncrypted);
            }
            long rsaDecryptTime = System.nanoTime() - rsaDecryptStart;
            
            deleteKey(aesKeyId);
            deleteKey(rsaKeyId);
            
            return new CryptoBenchmarkResult(
                    iterations,
                    testData.length(),
                    aesEncryptTime / iterations / 1_000_000.0,
                    aesDecryptTime / iterations / 1_000_000.0,
                    rsaEncryptTime / iterations / 1_000_000.0,
                    rsaDecryptTime / iterations / 1_000_000.0,
                    iterations * 1000 / (aesEncryptTime / 1_000_000_000.0),
                    iterations * 100 / (rsaEncryptTime / 1_000_000_000.0)
            );
            
        } catch (Exception e) {
            deleteKey(aesKeyId);
            deleteKey(rsaKeyId);
            throw new CryptoException("性能测试失败", e);
        }
    }
    
    private KeyInfo getKeyInfoOrThrow(String keyId) {
        KeyInfo keyInfo = keyStore.get(keyId);
        if (keyInfo == null) {
            throw new CryptoException("密钥不存在: " + keyId);
        }
        if (!keyInfo.isActive()) {
            throw new CryptoException("密钥已禁用: " + keyId);
        }
        return keyInfo;
    }
    
    private SecretKey getAesSecretKey(KeyInfo keyInfo) {
        String context = keyContextMap.getOrDefault(keyInfo.getKeyId(), keyInfo.getKeyId());
        byte[] keyBytes = decryptKeyStorage(keyInfo.getEncryptedKey(), context);
        try {
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
    
    private String encryptKeyStorage(byte[] keyData, String context) {
        try {
            byte[] storageKey = deriveStorageKey(context);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            SecretKeySpec keySpec = new SecretKeySpec(storageKey, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            
            byte[] encrypted = cipher.doFinal(keyData);
            
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            Arrays.fill(storageKey, (byte) 0);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            throw new CryptoException("密钥存储加密失败", e);
        }
    }
    
    private byte[] decryptKeyStorage(String encryptedKey, String context) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedKey);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            byte[] storageKey = deriveStorageKey(context);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(storageKey, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            Arrays.fill(storageKey, (byte) 0);
            
            return decrypted;
            
        } catch (Exception e) {
            throw new CryptoException("密钥存储解密失败", e);
        }
    }
    
    private byte[] deriveStorageKey(String context) {
        try {
            String envKey = System.getenv("ETL_CRYPTO_MASTER_KEY");
            if (envKey == null || envKey.isEmpty()) {
                envKey = System.getProperty("etl.crypto.masterKey", "default-development-key-change-in-production");
            }
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(envKey.getBytes(StandardCharsets.UTF_8));
            digest.update(context.getBytes(StandardCharsets.UTF_8));
            
            return digest.digest();
            
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("存储密钥派生失败", e);
        }
    }
    
    private void secureDelete(KeyInfo keyInfo) {
        if (keyInfo.getEncryptedKey() != null) {
            keyInfo.getEncryptedKey().chars().forEach(c -> {});
        }
    }
}
