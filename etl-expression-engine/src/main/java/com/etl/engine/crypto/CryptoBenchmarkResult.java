package com.etl.engine.crypto;

public record CryptoBenchmarkResult(
        int iterations,
        int dataSize,
        double aesEncryptAvgMs,
        double aesDecryptAvgMs,
        double rsaEncryptAvgMs,
        double rsaDecryptAvgMs,
        double aesThroughput,
        double rsaThroughput
) {
    @Override
    public String toString() {
        return """
                
                === 加解密性能基准测试结果 ===
                测试迭代: %d次, 数据大小: %d字节
                
                AES-256-GCM:
                  加密平均耗时: %.3fms
                  解密平均耗时: %.3fms
                  加密吞吐量: %.2f ops/sec
                
                RSA-2048:
                  加密平均耗时: %.3fms
                  解密平均耗时: %.3fms
                  加密吞吐量: %.2f ops/sec
                """.formatted(iterations, dataSize,
                aesEncryptAvgMs, aesDecryptAvgMs, aesThroughput,
                rsaEncryptAvgMs, rsaDecryptAvgMs, rsaThroughput);
    }
}
