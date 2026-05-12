package com.loadly.backend.controller.api;

import com.loadly.backend.dto.UsuarioDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.service.database.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador API para Usuarios/Clientes
 */
@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    /**
     * GET /api/usuarios - Obtiene todos los usuarios
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<UsuarioDTO>>> obtenerTodos() {
        try {
            List<UsuarioDTO> usuarios = usuarioService.obtenerTodos();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Usuarios obtenidos exitosamente", usuarios));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener usuarios: " + e.getMessage()));
        }
    }

    /**
     * GET /api/usuarios/{id} - Obtiene un usuario por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<UsuarioDTO>> obtenerPorId(@PathVariable Integer id) {
        try {
            UsuarioDTO usuario = usuarioService.obtenerPorId(id);
            if (usuario != null) {
                return ResponseEntity.ok(new ResponseDTO<>(true, "Usuario encontrado", usuario));
            } else {
                return ResponseEntity.ok(new ResponseDTO<>(false, "Usuario no encontrado"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener usuario: " + e.getMessage()));
        }
    }

    /**
     * GET /api/usuarios/rol/{rol} - Obtiene usuarios por rol
     */
    @GetMapping("/rol/{rol}")
    public ResponseEntity<ResponseDTO<List<UsuarioDTO>>> obtenerPorRol(@PathVariable String rol) {
        try {
            List<UsuarioDTO> usuarios = usuarioService.obtenerPorRol(rol);
            return ResponseEntity.ok(new ResponseDTO<>(true, "Usuarios encontrados", usuarios));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener usuarios: " + e.getMessage()));
        }
    }

    /**
     * GET /api/usuarios/stats/total - Obtiene el total de usuarios
     */
    @GetMapping("/stats/total")
    public ResponseEntity<ResponseDTO<Long>> obtenerTotal() {
        try {
            Long total = usuarioService.obtenerTotal();
            return ResponseEntity.ok(new ResponseDTO<>(true, "Total obtenido", total));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ResponseDTO<>(false, "Error al obtener total: " + e.getMessage()));
        }
    }
}
