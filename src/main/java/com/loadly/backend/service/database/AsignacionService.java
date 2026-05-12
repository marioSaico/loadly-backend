package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.AsignacionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para operaciones de Asignaciones (rutas de envío)
 */
@Service
public class AsignacionService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todas las asignaciones
     */
    public List<AsignacionDTO> obtenerTodas() {
        String sql = "SELECT idasignacion, plan_vuelo_id_plan_vuelo, envio_idEnvio, ordenRuta FROM asignacion";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new AsignacionDTO(
            rs.getInt("idasignacion"),
            rs.getInt("plan_vuelo_id_plan_vuelo"),
            rs.getString("envio_idEnvio"),
            rs.getInt("ordenRuta")
        ));
    }

    /**
     * Obtiene una asignación por ID
     */
    public AsignacionDTO obtenerPorId(Integer id) {
        String sql = "SELECT idasignacion, plan_vuelo_id_plan_vuelo, envio_idEnvio, ordenRuta FROM asignacion WHERE idasignacion = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new AsignacionDTO(
                rs.getInt("idasignacion"),
                rs.getInt("plan_vuelo_id_plan_vuelo"),
                rs.getString("envio_idEnvio"),
                rs.getInt("ordenRuta")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene asignaciones por ID de envío
     */
    public List<AsignacionDTO> obtenerPorEnvio(String idEnvio) {
        String sql = "SELECT idasignacion, plan_vuelo_id_plan_vuelo, envio_idEnvio, ordenRuta FROM asignacion WHERE envio_idEnvio = ? ORDER BY ordenRuta";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{idEnvio}, (rs, rowNum) -> new AsignacionDTO(
            rs.getInt("idasignacion"),
            rs.getInt("plan_vuelo_id_plan_vuelo"),
            rs.getString("envio_idEnvio"),
            rs.getInt("ordenRuta")
        ));
    }

    /**
     * Obtiene asignaciones por ID de plan de vuelo
     */
    public List<AsignacionDTO> obtenerPorPlanVuelo(Integer idPlanVuelo) {
        String sql = "SELECT idasignacion, plan_vuelo_id_plan_vuelo, envio_idEnvio, ordenRuta FROM asignacion WHERE plan_vuelo_id_plan_vuelo = ? ORDER BY ordenRuta";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{idPlanVuelo}, (rs, rowNum) -> new AsignacionDTO(
            rs.getInt("idasignacion"),
            rs.getInt("plan_vuelo_id_plan_vuelo"),
            rs.getString("envio_idEnvio"),
            rs.getInt("ordenRuta")
        ));
    }

    /**
     * Obtiene el total de asignaciones
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM asignacion";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
