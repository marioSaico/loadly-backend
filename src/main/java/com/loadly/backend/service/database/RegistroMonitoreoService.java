package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.RegistroMonitoreoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para operaciones de Registros de Monitoreo
 */
@Service
public class RegistroMonitoreoService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todos los registros de monitoreo
     */
    public List<RegistroMonitoreoDTO> obtenerTodos() {
        String sql = "SELECT idRegistro, envio_idEnvio, aeropuerto_IdAeropuerto, estado, fechaHora FROM registro_monitoreo ORDER BY fechaHora DESC";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new RegistroMonitoreoDTO(
            rs.getInt("idRegistro"),
            rs.getString("envio_idEnvio"),
            rs.getInt("aeropuerto_IdAeropuerto"),
            rs.getString("estado"),
            rs.getTimestamp("fechaHora") != null ? rs.getTimestamp("fechaHora").toLocalDateTime() : null
        ));
    }

    /**
     * Obtiene un registro de monitoreo por ID
     */
    public RegistroMonitoreoDTO obtenerPorId(Integer id) {
        String sql = "SELECT idRegistro, envio_idEnvio, aeropuerto_IdAeropuerto, estado, fechaHora FROM registro_monitoreo WHERE idRegistro = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new RegistroMonitoreoDTO(
                rs.getInt("idRegistro"),
                rs.getString("envio_idEnvio"),
                rs.getInt("aeropuerto_IdAeropuerto"),
                rs.getString("estado"),
                rs.getTimestamp("fechaHora") != null ? rs.getTimestamp("fechaHora").toLocalDateTime() : null
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene registros por ID de envío
     */
    public List<RegistroMonitoreoDTO> obtenerPorEnvio(String idEnvio) {
        String sql = "SELECT idRegistro, envio_idEnvio, aeropuerto_IdAeropuerto, estado, fechaHora FROM registro_monitoreo WHERE envio_idEnvio = ? ORDER BY fechaHora DESC";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{idEnvio}, (rs, rowNum) -> new RegistroMonitoreoDTO(
            rs.getInt("idRegistro"),
            rs.getString("envio_idEnvio"),
            rs.getInt("aeropuerto_IdAeropuerto"),
            rs.getString("estado"),
            rs.getTimestamp("fechaHora") != null ? rs.getTimestamp("fechaHora").toLocalDateTime() : null
        ));
    }

    /**
     * Obtiene registros por aeropuerto
     */
    public List<RegistroMonitoreoDTO> obtenerPorAeropuerto(Integer idAeropuerto) {
        String sql = "SELECT idRegistro, envio_idEnvio, aeropuerto_IdAeropuerto, estado, fechaHora FROM registro_monitoreo WHERE aeropuerto_IdAeropuerto = ? ORDER BY fechaHora DESC";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{idAeropuerto}, (rs, rowNum) -> new RegistroMonitoreoDTO(
            rs.getInt("idRegistro"),
            rs.getString("envio_idEnvio"),
            rs.getInt("aeropuerto_IdAeropuerto"),
            rs.getString("estado"),
            rs.getTimestamp("fechaHora") != null ? rs.getTimestamp("fechaHora").toLocalDateTime() : null
        ));
    }

    /**
     * Obtiene el total de registros de monitoreo
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM registro_monitoreo";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
