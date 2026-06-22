package com.loadly.backend.config.security;

import com.loadly.backend.dto.UsuarioDTO;
import com.loadly.backend.model.UsuarioDetails;
import com.loadly.backend.service.database.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UsuarioService usuarioService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsuarioDTO usuario = usuarioService.obtenerPorCorreo(username);
        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado con correo: " + username);
        }

        return new UsuarioDetails(usuario);
    }
}