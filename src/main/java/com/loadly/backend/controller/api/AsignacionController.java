package com.loadly.backend.controller.api;

import com.loadly.backend.dto.AsignacionDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.AsignacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador API para Asignaciones (rutas de envío)
 */
@RestController
@RequestMapping("/api/asignaciones")
@CrossOrigin(origins = "*")
public class AsignacionController {

    @Autowired
    private AsignacionService asignacionService;

    /**
     * GET /api/asignaciones - Obtiene todas las asignaciones
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<AsignacionDTO>>> obtenerTodas() {
        try {
            List<AsignacionDTO> asignaciones = asignacionService.obtenerTodas();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Asignaciones obtenidas exitosamente", asignaciones));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener asignaciones: " + e.getMessage()));
        }
    }

    /**
     * GET /api/asignaciones/{id} - Obtiene una asignación por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<AsignacionDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            AsignacionDTO asignacion = asignacionService.obtenerPorId(id);
            if (asignacion != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Asignación encontrada", asignacion));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Asignación no encontrada"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener asignación: " + e.getMessage()));
        }
    }

    /**
     * GET /api/asignaciones/envio/{idEnvio} - Obtiene asignaciones por ID de envío
     */
    @GetMapping("/envio/{idEnvio}")
    public ResponseEntity<ResponseDTO<List<AsignacionDTO>>> obtenerPorEnvio(@PathVariable String idEnvio) {
        try {
            List<AsignacionDTO> asignaciones = asignacionService.obtenerPorEnvio(idEnvio);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Asignaciones encontradas", asignaciones));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener asignaciones: " + e.getMessage()));
        }
    }

    /**
     * GET /api/asignaciones/plan-vuelo/{idPlanVuelo} - Obtiene asignaciones por ID de plan de vuelo
     */
    @GetMapping("/plan-vuelo/{idPlanVuelo}")
    public ResponseEntity<ResponseDTO<List<AsignacionDTO>>> obtenerPorPlanVuelo(@PathVariable Integer idPlanVuelo) {
        try {
            List<AsignacionDTO> asignaciones = asignacionService.obtenerPorPlanVuelo(idPlanVuelo);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Asignaciones encontradas", asignaciones));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener asignaciones: " + e.getMessage()));
        }
    }

    /**
     * GET /api/asignaciones/stats/total - Obtiene el total de asignaciones
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = asignacionService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }
}
