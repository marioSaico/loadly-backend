package com.loadly.backend.controller.api;

import com.loadly.backend.dto.PlanVueloDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.PlanVueloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Controlador API para Planes de Vuelo
 */
@RestController
@RequestMapping("/api/planes-vuelo")
@CrossOrigin(origins = "*")
public class PlanVueloController {

    @Autowired
    private PlanVueloService planVueloService;

    /**
     * GET /api/planes-vuelo - Obtiene todos los planes de vuelo
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<PlanVueloDTO>>> obtenerTodos() {
        try {
            List<PlanVueloDTO> planes = planVueloService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Planes de vuelo obtenidos exitosamente", planes));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener planes de vuelo: " + e.getMessage()));
        }
    }

    /**
     * GET /api/planes-vuelo/{id} - Obtiene un plan de vuelo por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<PlanVueloDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            PlanVueloDTO plan = planVueloService.obtenerPorId(id);
            if (plan != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Plan de vuelo encontrado", plan));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Plan de vuelo no encontrado"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener plan de vuelo: " + e.getMessage()));
        }
    }

    /**
     * GET /api/planes-vuelo/stats/total - Obtiene el total de planes de vuelo
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = planVueloService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }

    /**
     * POST /api/planes-vuelo/cargar-masiva - Carga planes de vuelo desde archivo TXT
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

            // Leer el contenido del archivo (UTF-8 para planes de vuelo)
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), "UTF-8")
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
            int insertados = planVueloService.cargarDesdeArchivo(contenido.toString());
            System.out.println("Planes insertados: " + insertados);

            String mensaje = "Se cargaron exitosamente " + insertados + " planes de vuelo de " + conteoLineas + " líneas procesadas";
            return ResponseEntity.ok(new ResponseDTO<>(true, mensaje, "Registros insertados: " + insertados));

        } catch (Exception e) {
            System.err.println("Error en endpoint cargar-masiva: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al procesar archivo: " + e.getMessage()));
        }
    }
}
