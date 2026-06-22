package com.loadly.backend.controller.api;

import com.loadly.backend.dto.AerolineaClienteDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.AerolineaClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador API para Aerolineas Cliente
 */
@RestController
@RequestMapping("/api/aerolineas-cliente")
@CrossOrigin(origins = "*")
public class AerolineaClienteController {

    @Autowired
    private AerolineaClienteService aerolineaClienteService;

    /**
     * GET /api/aerolineas-cliente - Obtiene todas las aerolíneas cliente
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<AerolineaClienteDTO>>> obtenerTodos() {
        try {
            List<AerolineaClienteDTO> aerolineas = aerolineaClienteService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Aerolíneas cliente obtenidas exitosamente", aerolineas));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener aerolíneas cliente: " + e.getMessage()));
        }
    }

    /**
     * GET /api/aerolineas-cliente/{id} - Obtiene una aerolínea cliente por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<AerolineaClienteDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            AerolineaClienteDTO aerolinea = aerolineaClienteService.obtenerPorId(id);
            if (aerolinea != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Aerolínea cliente encontrada", aerolinea));
            } else {
                return ResponseEntity.status(404)
                    .body(new ResponseDTO<>(false, "Aerolínea cliente no encontrada"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener aerolínea cliente: " + e.getMessage()));
        }
    }
}
