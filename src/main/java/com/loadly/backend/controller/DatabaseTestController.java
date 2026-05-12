package com.loadly.backend.controller;

import com.loadly.backend.database.config.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para pruebas de conexión a bases de datos
 */
@RestController
@RequestMapping("/api/test")
public class DatabaseTestController {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Prueba todas las conexiones disponibles
     * GET: http://localhost:8080/api/test/conexiones
     */
    @GetMapping("/conexiones")
    public ResponseEntity<Map<String, Object>> testAllConnections() {
        Map<String, Object> resultado = new HashMap<>();
        
        // Test Primaria
        resultado.put("primary", performConnectionTest("primary"));
        
        // Test Secundaria
        resultado.put("secondary", performConnectionTest("secondary"));
        
        // Test Terciaria
        resultado.put("tertiary", performConnectionTest("tertiary"));
        
        return ResponseEntity.ok(resultado);
    }

    /**
     * Prueba una conexión específica
     * GET: http://localhost:8080/api/test/conexion/primary
     */
    @GetMapping("/conexion/{dbName}")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String dbName) {
        return ResponseEntity.ok(performConnectionTest(dbName));
    }

    /**
     * Método auxiliar para probar conexión
     */
    private Map<String, Object> performConnectionTest(String dbName) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("database", dbName);
        
        try {
            JdbcTemplate jdbcTemplate = databaseManager.getDb(dbName);
            
            // Query simple para verificar conexión
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            
            resultado.put("estado", "✓ CONECTADO");
            resultado.put("version_mysql", version);
            resultado.put("exito", true);
            
        } catch (Exception e) {
            resultado.put("estado", "✗ ERROR");
            resultado.put("error", e.getMessage());
            resultado.put("causa", e.getClass().getSimpleName());
            resultado.put("exito", false);
        }
        
        return resultado;
    }

    /**
     * Retorna información de salud general
     * GET: http://localhost:8080/api/test/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("message", "API Loadly Backend disponible");
        return ResponseEntity.ok(health);
    }
}
