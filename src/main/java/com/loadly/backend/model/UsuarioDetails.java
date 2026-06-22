package com.loadly.backend.model;

import com.loadly.backend.dto.UsuarioDTO;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;

public class UsuarioDetails extends User {

    private final Integer idCliente;
    private final String nombre;
    private final String rol;
    private final Integer aeropuertoIdAeropuerto;

    public UsuarioDetails(UsuarioDTO usuario) {
        super(
            usuario.getCorreo(),
            usuario.getPassword() != null ? usuario.getPassword() : "",
            Collections.singletonList(new SimpleGrantedAuthority(
                usuario.getRol().startsWith("ROLE_") ? usuario.getRol() : "ROLE_" + usuario.getRol()
            ))
        );
        this.idCliente = usuario.getIdCliente();
        this.nombre = usuario.getNombre();
        this.rol = usuario.getRol();
        this.aeropuertoIdAeropuerto = usuario.getAeropuertoIdAeropuerto();
    }

    public Integer getIdCliente() {
        return idCliente;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public Integer getAeropuertoIdAeropuerto() {
        return aeropuertoIdAeropuerto;
    }
}
