package com.loadly.backend.database.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración para múltiples conexiones a base de datos MySQL
 * Permite conectar a diferentes instancias de BD simultáneamente
 */
@Configuration
public class MultipleDataSourceConfig {

    // ===== PRIMARY DATABASE =====
    @Value("${spring.datasource.primary.url:jdbc:mysql://127.0.0.1:3306/mydb?useSSL=false&serverTimezone=UTC}")
    private String primaryUrl;
    @Value("${spring.datasource.primary.username:root}")
    private String primaryUsername;
    @Value("${spring.datasource.primary.password:12345}")
    private String primaryPassword;
    @Value("${spring.datasource.primary.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String primaryDriver;

    // ===== SECONDARY DATABASE =====
    @Value("${spring.datasource.secondary.url:jdbc:mysql://127.0.0.1:3306/mydb?useSSL=false&serverTimezone=UTC}")
    private String secondaryUrl;
    @Value("${spring.datasource.secondary.username:root}")
    private String secondaryUsername;
    @Value("${spring.datasource.secondary.password:12345}")
    private String secondaryPassword;
    @Value("${spring.datasource.secondary.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String secondaryDriver;

    // ===== TERTIARY DATABASE =====
    @Value("${spring.datasource.tertiary.url:jdbc:mysql://127.0.0.1:3306/mydb?useSSL=false&serverTimezone=UTC}")
    private String tertiaryUrl;
    @Value("${spring.datasource.tertiary.username:root}")
    private String tertiaryUsername;
    @Value("${spring.datasource.tertiary.password:12345}")
    private String tertiaryPassword;
    @Value("${spring.datasource.tertiary.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String tertiaryDriver;

    /**
     * DataSource principal - Base de datos primaria
     */
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
                .url(primaryUrl)
                .username(primaryUsername)
                .password(primaryPassword)
                .driverClassName(primaryDriver)
                .build();
    }

    /**
     * DataSource secundario - Base de datos secundaria
     */
    @Bean(name = "secondaryDataSource")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create()
                .url(secondaryUrl)
                .username(secondaryUsername)
                .password(secondaryPassword)
                .driverClassName(secondaryDriver)
                .build();
    }

    /**
     * DataSource terciario - Base de datos terciaria (opcional)
     */
    @Bean(name = "tertiaryDataSource")
    public DataSource tertiaryDataSource() {
        return DataSourceBuilder.create()
                .url(tertiaryUrl)
                .username(tertiaryUsername)
                .password(tertiaryPassword)
                .driverClassName(tertiaryDriver)
                .build();
    }

    /**
     * JdbcTemplate para la base de datos primaria
     */
    @Bean(name = "primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate() {
        return new JdbcTemplate(primaryDataSource());
    }

    /**
     * JdbcTemplate para la base de datos secundaria
     */
    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate() {
        return new JdbcTemplate(secondaryDataSource());
    }

    /**
     * JdbcTemplate para la base de datos terciaria
     */
    @Bean(name = "tertiaryJdbcTemplate")
    public JdbcTemplate tertiaryJdbcTemplate() {
        return new JdbcTemplate(tertiaryDataSource());
    }

    /**
     * Mapa de DataSources para acceso dinámico
     */
    @Bean(name = "dataSourceMap")
    public Map<String, DataSource> dataSourceMap() {
        Map<String, DataSource> map = new HashMap<>();
        map.put("primary", primaryDataSource());
        map.put("secondary", secondaryDataSource());
        map.put("tertiary", tertiaryDataSource());
        return map;
    }

    /**
     * Mapa de JdbcTemplates para acceso dinámico
     */
    @Bean(name = "jdbcTemplateMap")
    public Map<String, JdbcTemplate> jdbcTemplateMap() {
        Map<String, JdbcTemplate> map = new HashMap<>();
        map.put("primary", primaryJdbcTemplate());
        map.put("secondary", secondaryJdbcTemplate());
        map.put("tertiary", tertiaryJdbcTemplate());
        return map;
    }
}
