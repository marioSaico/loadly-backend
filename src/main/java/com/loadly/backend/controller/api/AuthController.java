package com.loadly.backend.controller.api;

import com.loadly.backend.config.security.JwtUtils;
import com.loadly.backend.dto.AeropuertoDTO;
import com.loadly.backend.dto.LoginRequestDTO;
import com.loadly.backend.dto.ResponseDTO;
import com.loadly.backend.model.UsuarioDetails;
import com.loadly.backend.service.database.AeropuertoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AeropuertoService aeropuertoService;

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<Map<String, Object>>> authenticateUser(@RequestBody LoginRequestDTO loginRequest, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getCorreo(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtUtils.generateTokenFromUsername(loginRequest.getCorreo());

            Cookie cookie = new Cookie(jwtUtils.getJwtCookie(), jwt);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(24 * 60 * 60);
            
            response.addCookie(cookie);

            UsuarioDetails userDetails = (UsuarioDetails) authentication.getPrincipal();

            Map<String, Object> datos = new HashMap<>();
            datos.put("token", jwt);
            datos.put("correo", userDetails.getUsername());
            datos.put("nombre", userDetails.getNombre());
            datos.put("rol", userDetails.getRol());
            datos.put("idCliente", userDetails.getIdCliente());
            datos.put("aeropuertoIdAeropuerto", userDetails.getAeropuertoIdAeropuerto());

            if (userDetails.getAeropuertoIdAeropuerto() != null) {
                AeropuertoDTO aeropuerto = aeropuertoService.obtenerPorId(userDetails.getAeropuertoIdAeropuerto());
                if (aeropuerto != null) {
                    Map<String, Object> aeropuertoInfo = new HashMap<>();
                    aeropuertoInfo.put("idAeropuerto", aeropuerto.getIdAeropuerto());
                    aeropuertoInfo.put("codigo", aeropuerto.getCodigo());
                    aeropuertoInfo.put("ciudad", aeropuerto.getCiudad());
                    aeropuertoInfo.put("pais", aeropuerto.getPais());
                    aeropuertoInfo.put("abreviatura", aeropuerto.getAbreviatura());
                    datos.put("aeropuerto", aeropuertoInfo);
                }
            }

            System.out.println("[AuthController] Login exitoso para: " + loginRequest.getCorreo());
            return ResponseEntity.ok(new ResponseDTO<>(true, "Login exitoso", datos));
        } catch (Exception e) {
            System.err.println("[AuthController] Error en login para " + loginRequest.getCorreo() + ": " + e.getMessage());
            e.printStackTrace();
            String msg = "Credenciales inválidas";
            if (e.getMessage() != null && e.getMessage().contains("aeropuerto")) {
                msg = "Error: " + e.getMessage();
            }
            return ResponseEntity.status(401)
                    .body(new ResponseDTO<>(false, msg));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseDTO<String>> logoutUser(HttpServletResponse response) {
        Cookie cookie = new Cookie(jwtUtils.getJwtCookie(), null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        
        response.addCookie(cookie);

        return ResponseEntity.ok(new ResponseDTO<>(true, "Logout exitoso", "Sesión cerrada"));
    }
}