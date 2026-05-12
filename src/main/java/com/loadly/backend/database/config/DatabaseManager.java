package com.loadly.backend.database.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Gestor centralizado de acceso a múltiples bases de datos
 * Facilita el cambio entre diferentes conexiones de forma elegante
 */
@Component
public class DatabaseManager {

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate secondaryJdbcTemplate;
    private final JdbcTemplate tertiaryJdbcTemplate;

    @Autowired
    public DatabaseManager(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            @Qualifier("secondaryJdbcTemplate") JdbcTemplate secondaryJdbcTemplate,
            @Qualifier("tertiaryJdbcTemplate") JdbcTemplate tertiaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondaryJdbcTemplate = secondaryJdbcTemplate;
        this.tertiaryJdbcTemplate = tertiaryJdbcTemplate;
    }

    /**
     * Obtiene el JdbcTemplate de la base de datos primaria
     */
    public JdbcTemplate getPrimaryDb() {
        return primaryJdbcTemplate;
    }

    /**
     * Obtiene el JdbcTemplate de la base de datos secundaria
     */
    public JdbcTemplate getSecondaryDb() {
        return secondaryJdbcTemplate;
    }

    /**
     * Obtiene el JdbcTemplate de la base de datos terciaria
     */
    public JdbcTemplate getTertiaryDb() {
        return tertiaryJdbcTemplate;
    }

    /**
     * Obtiene el JdbcTemplate según el nombre especificado
     */
    public JdbcTemplate getDb(String dbName) {
        return switch (dbName.toLowerCase()) {
            case "primary" -> primaryJdbcTemplate;
            case "secondary" -> secondaryJdbcTemplate;
            case "tertiary" -> tertiaryJdbcTemplate;
            default -> throw new IllegalArgumentException("Base de datos no reconocida: " + dbName);
        };
    }
}
