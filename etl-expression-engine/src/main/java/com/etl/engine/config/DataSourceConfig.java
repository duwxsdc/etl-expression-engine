package com.etl.engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

/**
 * 数据源配置类
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(10);
        return jdbcTemplate;
    }
}
