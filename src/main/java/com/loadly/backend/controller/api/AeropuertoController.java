package com.loadly.backend.controller.api;

import com.loadly.backend.dto.AeropuertoDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.AeropuertoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controlador API para Aeropuertos
 */
@RestController
@RequestMapping("/api/aeropuertos")
@CrossOrigin(origins = "*")
public class AeropuertoController {

    @Autowired
    private AeropuertoService aeropuertoService;

    /**
     * GET /api/aeropuertos - Obtiene todos los aeropuertos
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<AeropuertoDTO>>> obtenerTodos() {
        try {
            List<AeropuertoDTO> aeropuertos = aeropuertoService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Aeropuertos obtenidos exitosamente", aeropuertos));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener aeropuertos: " + e.getMessage()));
        }
    }

    /**
     * GET /api/aeropuertos/{id} - Obtiene un aeropuerto por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<AeropuertoDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            AeropuertoDTO aeropuerto = aeropuertoService.obtenerPorId(id);
            if (aeropuerto != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Aeropuerto encontrado", aeropuerto));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Aeropuerto no encontrado"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener aeropuerto: " + e.getMessage()));
        }
    }

    /**
     * GET /api/aeropuertos/total - Obtiene el total de aeropuertos
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = aeropuertoService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }
    /**
     * POST /api/aeropuertos/cargar-masiva - Carga aeropuertos desde archivo TXT
     */
    @PostMapping("/cargar-masiva")
    public ResponseEntity<ResponseDTO<String>> cargarMasiva(@RequestParam("file") MultipartFile archivo) {
        try {
            // Validar que el archivo no esté vacío
            if (archivo.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ResponseDTO<>(false, "El archivo está vacío"));
            }

            // Validar extensión del archivo
            String nombreArchivo = archivo.getOriginalFilename();
            if (nombreArchivo == null || (!nombreArchivo.endsWith(".txt"))) {
                return ResponseEntity.badRequest()
                    .body(new ResponseDTO<>(false, "El archivo debe ser de tipo .txt"));
            }

            System.out.println("Iniciando carga de archivo: " + nombreArchivo + ", tamaño: " + archivo.getSize());

            // Leer el contenido del archivo (UTF-16 o UTF-8)
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), "UTF-16")
            );
            StringBuilder contenido = new StringBuilder();
            String linea;
            int conteoLineas = 0;
            while ((linea = reader.readLine()) != null) {
                contenido.append(linea).append("\n");
                conteoLineas++;
            }
            reader.close();

            System.out.println("Archivo leído: " + conteoLineas + " líneas");

            // Procesar el archivo
            int insertados = aeropuertoService.cargarDesdeArchivo(contenido.toString());
            System.out.println("Aeropuertos insertados: " + insertados);

            String mensaje = "Se cargaron exitosamente " + insertados + " aeropuertos de " + conteoLineas + " líneas procesadas";
            return ResponseEntity.ok(new ResponseDTO<>(true, mensaje, "Registros insertados: " + insertados));

        } catch (Exception e) {
            System.err.println("Error en endpoint cargar-masiva: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al procesar archivo: " + e.getMessage()));
        }
    }}
