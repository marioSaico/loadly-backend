package com.loadly.backend.controller.api;

import com.loadly.backend.config.security.JwtUtils;
import com.loadly.backend.dto.LoginRequestDTO;
import com.loadly.backend.dto.ResponseDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<String>> authenticateUser(@RequestBody LoginRequestDTO loginRequest, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getContacto(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtUtils.generateTokenFromUsername(loginRequest.getContacto());

            Cookie cookie = new Cookie(jwtUtils.getJwtCookie(), jwt);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(24 * 60 * 60); // 24 hours
            // cookie.setSecure(true); // Activar en producción con HTTPS
            
            response.addCookie(cookie);

            return ResponseEntity.ok(new ResponseDTO<>(true, "Login exitoso", "Sesión iniciada"));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new ResponseDTO<>(false, "Error de autenticación: Credenciales inválidas"));
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