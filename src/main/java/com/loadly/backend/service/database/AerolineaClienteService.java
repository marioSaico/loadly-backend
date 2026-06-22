package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.AerolineaClienteDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para operaciones de AerolineaCliente
 */
@Service
public class AerolineaClienteService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todas las aerolíneas cliente
     */
    public List<AerolineaClienteDTO> obtenerTodos() {
        String sql = "SELECT id, nombre FROM aerolinea_cliente";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new AerolineaClienteDTO(
            rs.getInt("id"),
            rs.getString("nombre")
        ));
    }

    /**
     * Obtiene una aerolínea cliente por ID
     */
    public AerolineaClienteDTO obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM aerolinea_cliente WHERE id = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new AerolineaClienteDTO(
                rs.getInt("id"),
                rs.getString("nombre")
            ));
        } catch (Exception e) {
            return null;
        }
    }
}
