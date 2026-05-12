package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.EnvioDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para operaciones de Envíos
 */
@Service
public class EnvioService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todos los envíos
     */
    public List<EnvioDTO> obtenerTodos() {
        String sql = "SELECT idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado FROM envio";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new EnvioDTO(
            rs.getString("idEnvio"),
            rs.getTimestamp("fechaRegistro") != null ? rs.getTimestamp("fechaRegistro").toLocalDateTime() : null,
            rs.getTimestamp("fechaLimiteEntrega") != null ? rs.getTimestamp("fechaLimiteEntrega").toLocalDateTime() : null,
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getInt("cantidadMaletas"),
            rs.getInt("cliente_idCliente"),
            rs.getBoolean("planificado")
        ));
    }

    /**
     * Obtiene un envío por ID
     */
    public EnvioDTO obtenerPorId(String idEnvio) {
        String sql = "SELECT idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado FROM envio WHERE idEnvio = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{idEnvio}, (rs, rowNum) -> new EnvioDTO(
                rs.getString("idEnvio"),
                rs.getTimestamp("fechaRegistro") != null ? rs.getTimestamp("fechaRegistro").toLocalDateTime() : null,
                rs.getTimestamp("fechaLimiteEntrega") != null ? rs.getTimestamp("fechaLimiteEntrega").toLocalDateTime() : null,
                rs.getInt("idAeropuertoOrigen"),
                rs.getInt("idAeropuertoDestino"),
                rs.getInt("cantidadMaletas"),
                rs.getInt("cliente_idCliente"),
                rs.getBoolean("planificado")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene envíos por estado de planificación
     */
    public List<EnvioDTO> obtenerPlanificados(Boolean planificado) {
        String sql = "SELECT idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado FROM envio WHERE planificado = ?";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{planificado}, (rs, rowNum) -> new EnvioDTO(
            rs.getString("idEnvio"),
            rs.getTimestamp("fechaRegistro") != null ? rs.getTimestamp("fechaRegistro").toLocalDateTime() : null,
            rs.getTimestamp("fechaLimiteEntrega") != null ? rs.getTimestamp("fechaLimiteEntrega").toLocalDateTime() : null,
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getInt("cantidadMaletas"),
            rs.getInt("cliente_idCliente"),
            rs.getBoolean("planificado")
        ));
    }

    /**
     * Obtiene el total de envíos
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM envio";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
