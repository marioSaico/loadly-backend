package com.loadly.backend.controller.api;

import com.loadly.backend.dto.EnvioDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.DataService;
import com.loadly.backend.service.database.EnvioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador API para Envíos
 */
@RestController
@RequestMapping("/api/envios")
@CrossOrigin(origins = "*")
public class EnvioController {

    @Autowired
    private EnvioService envioService;

    @Autowired
    private DataService dataService;

    /**
     * POST /api/envios/cargar-carpeta - Carga múltiples archivos de envío en memoria
     */
    @PostMapping("/cargar-carpeta")
    public ResponseEntity<ResponseDTO<String>> cargarCarpeta(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("horaInicio") int horaInicio,
            @RequestParam("minutoInicio") int minutoInicio) {
        try {
            // Inicializar datos maestros si no están cargados
            dataService.inicializar();
            dataService.cargarEnviosFiltrados(files, fechaInicio, horaInicio, minutoInicio);

            return ResponseEntity.ok(new ResponseDTO<>(true, "Carpeta cargada exitosamente: " + files.length + " archivos", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al cargar carpeta: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/envios/limpiar - Libera toda la memoria de envíos cargados
     */
    @DeleteMapping("/limpiar")
    public ResponseEntity<ResponseDTO<String>> limpiarMemoria() {
        try {
            dataService.resetEstado();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Memoria de envíos liberada correctamente", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al liberar memoria: " + e.getMessage()));
        }
    }

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
    @PostMapping("/registrar")
    public ResponseEntity<ResponseDTO<EnvioDTO>> registrarEnvio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaRegistro,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaLimiteEntrega,
            @RequestParam(required = false) Integer idAeropuertoOrigen,
            @RequestParam(required = false) Integer idAeropuertoDestino,
            @RequestParam(required = false) Integer cantidadMaletas,
            @RequestParam(required = false) Integer clienteIdCliente)
             {
        try {
            EnvioDTO envio = new EnvioDTO(
                    null,
                    fechaRegistro,
                    fechaLimiteEntrega,
                    idAeropuertoOrigen,
                    idAeropuertoDestino,
                    cantidadMaletas,
                    clienteIdCliente,
                    false,
                    null
            );
            return ResponseEntity.ok(new ResponseDTO<>(
                    true,
                    "Envio registrado correctamente",
                    envioService.registrarEnvio(envio)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDTO<>(false, "Error al registrar envio: " + e.getMessage()));
        }
    }

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
