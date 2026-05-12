package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.UsuarioDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para operaciones de Usuarios/Clientes
 */
@Service
public class UsuarioService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todos los usuarios
     */
    public List<UsuarioDTO> obtenerTodos() {
        String sql = "SELECT idCliente, nombre, rol, contacto FROM usuario";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new UsuarioDTO(
            rs.getInt("idCliente"),
            rs.getString("nombre"),
            rs.getString("rol"),
            rs.getString("contacto")
        ));
    }

    /**
     * Obtiene un usuario por ID
     */
    public UsuarioDTO obtenerPorId(Integer id) {
        String sql = "SELECT idCliente, nombre, rol, contacto FROM usuario WHERE idCliente = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new UsuarioDTO(
                rs.getInt("idCliente"),
                rs.getString("nombre"),
                rs.getString("rol"),
                rs.getString("contacto")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene usuarios por rol
     */
    public List<UsuarioDTO> obtenerPorRol(String rol) {
        String sql = "SELECT idCliente, nombre, rol, contacto FROM usuario WHERE rol = ?";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{rol}, (rs, rowNum) -> new UsuarioDTO(
            rs.getInt("idCliente"),
            rs.getString("nombre"),
            rs.getString("rol"),
            rs.getString("contacto")
        ));
    }

    /**
     * Obtiene el total de usuarios
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM usuario";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
