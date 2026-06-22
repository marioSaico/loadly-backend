package com.loadly.backend.config.security;

import com.loadly.backend.dto.UsuarioDTO;
import com.loadly.backend.service.database.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UsuarioService usuarioService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsuarioDTO usuario = usuarioService.obtenerPorContacto(username);
        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado con contacto: " + username);
        }

        // Se asume que el rol en la DB es algo como "ADMIN", "USER", etc.
        // Spring Security espera "ROLE_ADMIN" por convención si se usa hasRole(), 
        // pero aquí usaremos roles simples o prefijados.
        String rol = usuario.getRol().startsWith("ROLE_") ? usuario.getRol() : "ROLE_" + usuario.getRol();

        return new User(
                usuario.getContacto(),
                usuario.getPassword() != null ? usuario.getPassword() : "", // Password debería estar hasheado en DB
                Collections.singletonList(new SimpleGrantedAuthority(rol))
        );
    }
}