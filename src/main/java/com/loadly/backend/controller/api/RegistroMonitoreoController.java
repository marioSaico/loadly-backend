package com.loadly.backend.controller.api;

import com.loadly.backend.dto.RegistroMonitoreoDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.RegistroMonitoreoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador API para Registros de Monitoreo
 */
@RestController
@RequestMapping("/api/registros-monitoreo")
@CrossOrigin(origins = "*")
public class RegistroMonitoreoController {

    @Autowired
    private RegistroMonitoreoService registroMonitoreoService;

    /**
     * GET /api/registros-monitoreo - Obtiene todos los registros de monitoreo
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<RegistroMonitoreoDTO>>> obtenerTodos() {
        try {
            List<RegistroMonitoreoDTO> registros = registroMonitoreoService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Registros obtenidos exitosamente", registros));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener registros: " + e.getMessage()));
        }
    }

    /**
     * GET /api/registros-monitoreo/{id} - Obtiene un registro de monitoreo por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<RegistroMonitoreoDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            RegistroMonitoreoDTO registro = registroMonitoreoService.obtenerPorId(id);
            if (registro != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Registro encontrado", registro));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Registro no encontrado"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener registro: " + e.getMessage()));
        }
    }

    /**
     * GET /api/registros-monitoreo/envio/{idEnvio} - Obtiene registros de monitoreo por ID de envío
     */
    @GetMapping("/envio/{idEnvio}")
    public ResponseEntity<ResponseDTO<List<RegistroMonitoreoDTO>>> obtenerPorEnvio(@PathVariable String idEnvio) {
        try {
            List<RegistroMonitoreoDTO> registros = registroMonitoreoService.obtenerPorEnvio(idEnvio);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Registros encontrados", registros));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener registros: " + e.getMessage()));
        }
    }

    /**
     * GET /api/registros-monitoreo/aeropuerto/{idAeropuerto} - Obtiene registros de monitoreo por aeropuerto
     */
    @GetMapping("/aeropuerto/{idAeropuerto}")
    public ResponseEntity<ResponseDTO<List<RegistroMonitoreoDTO>>> obtenerPorAeropuerto(@PathVariable Integer idAeropuerto) {
        try {
            List<RegistroMonitoreoDTO> registros = registroMonitoreoService.obtenerPorAeropuerto(idAeropuerto);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Registros encontrados", registros));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener registros: " + e.getMessage()));
        }
    }

    /**
     * GET /api/registros-monitoreo/stats/total - Obtiene el total de registros
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = registroMonitoreoService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }
}
