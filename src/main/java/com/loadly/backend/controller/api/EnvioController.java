package com.loadly.backend.controller.api;

import com.loadly.backend.dto.EnvioDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.EnvioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador API para Envíos
 */
@RestController
@RequestMapping("/api/envios")
@CrossOrigin(origins = "*")
public class EnvioController {

    @Autowired
    private EnvioService envioService;

    /**
     * GET /api/envios - Obtiene todos los envíos
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<EnvioDTO>>> obtenerTodos() {
        try {
            List<EnvioDTO> envios = envioService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Envíos obtenidos exitosamente", envios));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener envíos: " + e.getMessage()));
        }
    }

    /**
     * GET /api/envios/{id} - Obtiene un envío por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<EnvioDTO>> obtenerPorId(@PathVariable String id) {
        try {
            EnvioDTO envio = envioService.obtenerPorId(id);
            if (envio != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Envío encontrado", envio));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Envío no encontrado"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener envío: " + e.getMessage()));
        }
    }

    /**
     * GET /api/envios/planificado/{planificado} - Obtiene envíos por estado de planificación
     */
    @GetMapping("/planificado/{planificado}")
    public ResponseEntity<ResponseDTO<List<EnvioDTO>>> obtenerPlanificados(@PathVariable Boolean planificado) {
        try {
            List<EnvioDTO> envios = envioService.obtenerPlanificados(planificado);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Envíos encontrados", envios));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener envíos: " + e.getMessage()));
        }
    }

    /**
     * GET /api/envios/stats/total - Obtiene el total de envíos
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = envioService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }
}
