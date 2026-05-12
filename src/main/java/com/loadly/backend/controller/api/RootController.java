package com.loadly.backend.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * Controlador raíz para mostrar información sobre los endpoints disponibles
 */
@RestController
public class RootController {

    /**
     * GET / - Devuelve información sobre los endpoints disponibles
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "Bienvenido a Loadly Backend API");
        response.put("version", "1.0.0");
        response.put("estado", "operacional");
        response.put("endpoints", new HashMap<String, Object>() {{
            put("Aeropuertos", new HashMap<String, String>() {{
                put("GET /api/aeropuertos", "Obtener todos los aeropuertos");
                put("GET /api/aeropuertos/{id}", "Obtener aeropuerto por ID");
                put("GET /api/aeropuertos/stats/total", "Obtener total de aeropuertos");
            }});
            put("Envíos", new HashMap<String, String>() {{
                put("GET /api/envios", "Obtener todos los envíos");
                put("GET /api/envios/{id}", "Obtener envío por ID (idEnvio)");
                put("GET /api/envios/planificado/{true|false}", "Obtener envíos por estado planificado");
                put("GET /api/envios/stats/total", "Obtener total de envíos");
            }});
            put("Planes de Vuelo", new HashMap<String, String>() {{
                put("GET /api/planes-vuelo", "Obtener todos los planes de vuelo");
                put("GET /api/planes-vuelo/{id}", "Obtener plan de vuelo por ID");
                put("GET /api/planes-vuelo/stats/total", "Obtener total de planes de vuelo");
            }});
            put("Asignaciones", new HashMap<String, String>() {{
                put("GET /api/asignaciones", "Obtener todas las asignaciones");
                put("GET /api/asignaciones/{id}", "Obtener asignación por ID");
                put("GET /api/asignaciones/envio/{idEnvio}", "Obtener asignaciones por ID de envío");
                put("GET /api/asignaciones/plan-vuelo/{idPlanVuelo}", "Obtener asignaciones por plan de vuelo");
                put("GET /api/asignaciones/stats/total", "Obtener total de asignaciones");
            }});
            put("Registros de Monitoreo", new HashMap<String, String>() {{
                put("GET /api/registros-monitoreo", "Obtener todos los registros");
                put("GET /api/registros-monitoreo/{id}", "Obtener registro por ID");
                put("GET /api/registros-monitoreo/envio/{idEnvio}", "Obtener registros por ID de envío");
                put("GET /api/registros-monitoreo/aeropuerto/{idAeropuerto}", "Obtener registros por aeropuerto");
                put("GET /api/registros-monitoreo/stats/total", "Obtener total de registros");
            }});
            put("Usuarios", new HashMap<String, String>() {{
                put("GET /api/usuarios", "Obtener todos los usuarios");
                put("GET /api/usuarios/{id}", "Obtener usuario por ID");
                put("GET /api/usuarios/rol/{rol}", "Obtener usuarios por rol");
                put("GET /api/usuarios/stats/total", "Obtener total de usuarios");
            }});
        }});
        response.put("base_url", "http://localhost:8080");
        
        return ResponseEntity.ok(response);
    }
}
