package com.etl.engine.crypto;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CryptoService单元测试")
class CryptoServiceTest {
    
    private CryptoService cryptoService;
    
    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
    }
    
    @AfterEach
    void tearDown() {
        cryptoService.deleteKey("test-aes");
        cryptoService.deleteKey("test-rsa");
    }
    
    @Test
    @DisplayName("生成AES-256密钥")
    void testGenerateAesKey() {
        String keyId = cryptoService.generateAesKey("test-aes");
        assertEquals("test-aes", keyId);
        assertTrue(cryptoService.hasKey("test-aes"));
        
        var status = cryptoService.getKeyStatus("test-aes");
        assertTrue((Boolean) status.get("exists"));
        assertEquals("AES", status.get("algorithm"));
        assertEquals(256, status.get("keySize"));
    }
    
    @Test
    @DisplayName("AES加密解密-字符串")
    void testAesEncryptDecryptString() {
        cryptoService.generateAesKey("test-aes");
        
        String plaintext = "Hello, World! 这是一个测试消息。";
        String encrypted = cryptoService.aesEncrypt("test-aes", plaintext);
        
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        
        String decrypted = cryptoService.aesDecryptToString("test-aes", encrypted);
        assertEquals(plaintext, decrypted);
    }
    
    @Test
    @DisplayName("AES加密解密-字节数组")
    void testAesEncryptDecryptBytes() {
        cryptoService.generateAesKey("test-aes");
        
        byte[] data = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        String encrypted = cryptoService.aesEncrypt("test-aes", data);
        
        byte[] decrypted = cryptoService.aesDecrypt("test-aes", encrypted);
        assertArrayEquals(data, decrypted);
    }
    
    @Test
    @DisplayName("AES加密每次产生不同密文")
    void testAesEncryptionRandomness() {
        cryptoService.generateAesKey("test-aes");
        
        String plaintext = "Same message";
        String encrypted1 = cryptoService.aesEncrypt("test-aes", plaintext);
        String encrypted2 = cryptoService.aesEncrypt("test-aes", plaintext);
        
        assertNotEquals(encrypted1, encrypted2, "不同加密应产生不同密文(随机IV)");
        
        assertEquals(plaintext, cryptoService.aesDecryptToString("test-aes", encrypted1));
        assertEquals(plaintext, cryptoService.aesDecryptToString("test-aes", encrypted2));
    }
    
    @Test
    @DisplayName("生成RSA-2048密钥对")
    void testGenerateRsaKeyPair() {
        KeyPairResult result = cryptoService.generateRsaKeyPair("test-rsa");
        
        assertEquals("test-rsa", result.keyId());
        assertNotNull(result.publicKey());
        assertTrue(result.publicKey().length() > 0);
        
        var status = cryptoService.getKeyStatus("test-rsa");
        assertEquals("RSA", status.get("algorithm"));
        assertEquals(2048, status.get("keySize"));
    }
    
    @Test
    @DisplayName("RSA加密解密")
    void testRsaEncryptDecrypt() {
        cryptoService.generateRsaKeyPair("test-rsa");
        
        String plaintext = "Secret message";
        String encrypted = cryptoService.rsaEncrypt("test-rsa", plaintext);
        
        assertNotNull(encrypted);
        
        String decrypted = cryptoService.rsaDecrypt("test-rsa", encrypted);
        assertEquals(plaintext, decrypted);
    }
    
    @Test
    @DisplayName("密钥不存在时加密应抛出异常")
    void testEncryptWithNonExistentKey() {
        assertThrows(CryptoException.class, () -> 
                cryptoService.aesEncrypt("non-existent", "test"));
    }
    
    @Test
    @DisplayName("密钥不存在时解密应抛出异常")
    void testDecryptWithNonExistentKey() {
        assertThrows(CryptoException.class, () -> 
                cryptoService.aesDecrypt("non-existent", "dGVzdA=="));
    }
    
    @Test
    @DisplayName("删除密钥")
    void testDeleteKey() {
        cryptoService.generateAesKey("test-aes");
        assertTrue(cryptoService.hasKey("test-aes"));
        
        cryptoService.deleteKey("test-aes");
        assertFalse(cryptoService.hasKey("test-aes"));
    }
    
    @Test
    @DisplayName("密钥轮换策略")
    void testKeyRotationPolicy() {
        cryptoService.generateAesKey("test-aes");
        cryptoService.setKeyRotationPolicy("test-aes", KeyRotationPolicy.of(30));
        
        var status = cryptoService.getKeyStatus("test-aes");
        assertTrue(status.containsKey("rotationDays"));
        assertEquals(30, status.get("rotationDays"));
    }
    
    @Test
    @DisplayName("获取密钥状态")
    void testGetKeyStatus() {
        cryptoService.generateAesKey("test-aes");
        
        var status = cryptoService.getKeyStatus("test-aes");
        
        assertTrue((Boolean) status.get("exists"));
        assertEquals("test-aes", status.get("keyId"));
        assertEquals("AES", status.get("algorithm"));
        assertTrue((Boolean) status.get("active"));
    }
    
    @Test
    @DisplayName("获取不存在的密钥状态")
    void testGetNonExistentKeyStatus() {
        var status = cryptoService.getKeyStatus("non-existent");
        assertFalse((Boolean) status.get("exists"));
    }
    
    @Test
    @DisplayName("KeyRotationPolicy验证")
    void testKeyRotationPolicyValidation() {
        KeyRotationPolicy policy = KeyRotationPolicy.of(90);
        assertEquals(90, policy.rotationDays());
        assertTrue(policy.autoRotate());
        
        assertThrows(IllegalArgumentException.class, () -> KeyRotationPolicy.of(0));
        assertThrows(IllegalArgumentException.class, () -> KeyRotationPolicy.of(-1));
    }
    
    @Test
    @DisplayName("性能基准测试")
    void testBenchmark() {
        CryptoBenchmarkResult result = cryptoService.runBenchmark();
        
        assertNotNull(result);
        assertEquals(100, result.iterations());
        assertTrue(result.aesEncryptAvgMs() > 0);
        assertTrue(result.aesDecryptAvgMs() > 0);
        assertTrue(result.aesThroughput() > 0);
        
        System.out.println(result);
    }
}
