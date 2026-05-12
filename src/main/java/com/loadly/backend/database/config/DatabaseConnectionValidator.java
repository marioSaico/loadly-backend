package com.loadly.backend.database.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validador de conexiones a bases de datos
 * Se ejecuta automáticamente al iniciar la aplicación
 */
@Configuration
public class DatabaseConnectionValidator {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Se ejecuta después de que Spring inicie
     * Verifica todas las conexiones disponibles
     */
    @Bean
    public ApplicationRunner validateDatabaseConnections() {
        return args -> {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("  VALIDANDO CONEXIONES A BASES DE DATOS");
            System.out.println("=".repeat(60));

            validarConexion("primary");
            validarConexion("secondary");
            validarConexion("tertiary");

            System.out.println("=".repeat(60) + "\n");
        };
    }

    /**
     * Valida una conexión específica
     */
    private void validarConexion(String dbName) {
        try {
            JdbcTemplate jdbcTemplate = databaseManager.getDb(dbName);
            
            // Test 1: Verificar conexión
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            
            // Test 2: Obtener nombre de BD
            String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            
            // Test 3: Obtener usuario
            String user = jdbcTemplate.queryForObject("SELECT USER()", String.class);
            
            System.out.println(String.format(
                "✓ [%s] CONECTADO\n" +
                "  Base de datos: %s\n" +
                "  Usuario: %s\n" +
                "  Versión MySQL: %s\n",
                dbName.toUpperCase(),
                database != null ? database : "sin BD seleccionada",
                user,
                version
            ));

        } catch (Exception e) {
            System.out.println(String.format(
                "✗ [%s] ERROR DE CONEXIÓN\n" +
                "  Causa: %s\n" +
                "  Mensaje: %s\n",
                dbName.toUpperCase(),
                e.getClass().getSimpleName(),
                e.getMessage()
            ));
        }
    }
}
