package com.etl.engine;

import com.etl.engine.context.EtlContextManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public EtlContextManager etlContextManager() {
        return new EtlContextManager(null);
    }
}
