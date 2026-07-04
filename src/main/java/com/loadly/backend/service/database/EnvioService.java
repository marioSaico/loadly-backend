package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.EnvioDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        String sql = "SELECT idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado, idArchivo FROM envio";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new EnvioDTO(
            rs.getString("idEnvio"),
            rs.getObject("fechaRegistro", LocalDateTime.class),
            rs.getObject("fechaLimiteEntrega", LocalDateTime.class),
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getInt("cantidadMaletas"),
            rs.getInt("cliente_idCliente"),
            rs.getBoolean("planificado"),
             rs.getInt("idArchivo")
        ));
    }

    /**
     * Obtiene un envío por ID
     */
    public EnvioDTO obtenerPorId(String idEnvio) {
        String sql = "SELECT idArchivo, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado FROM envio WHERE idArchivo = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{Integer.parseInt(idEnvio)}, (rs, rowNum) -> new EnvioDTO(
                String.valueOf(rs.getInt("idArchivo")),
                rs.getObject("fechaRegistro", LocalDateTime.class),
                rs.getObject("fechaLimiteEntrega", LocalDateTime.class),
                rs.getInt("idAeropuertoOrigen"),
                rs.getInt("idAeropuertoDestino"),
                rs.getInt("cantidadMaletas"),
                rs.getInt("cliente_idCliente"),
                rs.getBoolean("planificado"),
                rs.getInt("idArchivo")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene envíos por estado de planificación
     */
    public List<EnvioDTO> obtenerPlanificados(Boolean planificado) {
        String sql = "SELECT idArchivo, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado FROM envio WHERE planificado = ?";
        
        return databaseManager.getPrimaryDb().query(sql, new Object[]{planificado}, (rs, rowNum) -> new EnvioDTO(
            String.valueOf(rs.getInt("idArchivo")),
            rs.getObject("fechaRegistro", LocalDateTime.class),
            rs.getObject("fechaLimiteEntrega", LocalDateTime.class),
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getInt("cantidadMaletas"),
            rs.getInt("cliente_idCliente"),
            rs.getBoolean("planificado"),
            rs.getInt("idArchivo")
            
        ));
    }

    public List<EnvioDTO> obtenerNoPlanificadosHasta(LocalDateTime hasta) {
        String sql = "SELECT e.idArchivo, e.fechaRegistro, e.fechaLimiteEntrega, e.idAeropuertoOrigen, e.idAeropuertoDestino, e.cantidadMaletas, e.cliente_idCliente, e.planificado " +
                "FROM envio e " +
                "JOIN aeropuerto a ON e.idAeropuertoOrigen = a.IdAeropuerto " +
                "WHERE e.planificado = 0 AND DATE_SUB(e.fechaRegistro, INTERVAL a.gmt HOUR) < ? " +
                "ORDER BY e.fechaRegistro ASC";

        return databaseManager.getPrimaryDb().query(sql, new Object[]{ hasta}, (rs, rowNum) -> new EnvioDTO(
            String.valueOf(rs.getInt("idArchivo")),
            rs.getObject("fechaRegistro", LocalDateTime.class),
            rs.getObject("fechaLimiteEntrega", LocalDateTime.class),
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getInt("cantidadMaletas"),
            rs.getInt("cliente_idCliente"),
            rs.getBoolean("planificado"),
            rs.getInt("idArchivo")
        ));
    }

    public EnvioDTO registrarEnvio(EnvioDTO envio) {
        validarDatosMinimos(envio);

        LocalDateTime fechaRegistro = truncarAMinutos(
                envio.getFechaRegistro() != null ? envio.getFechaRegistro() : LocalDateTime.now()
        );
        LocalDateTime fechaLimiteEntrega = truncarAMinutos(
                envio.getFechaLimiteEntrega() != null ? envio.getFechaLimiteEntrega() : fechaRegistro.plusHours(48)
        );
        String idEnvio = envio.getIdEnvio() != null && !envio.getIdEnvio().isBlank()
                ? envio.getIdEnvio()
                : "OD-" + System.currentTimeMillis();

        String sql = "INSERT INTO envio (idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        databaseManager.getPrimaryDb().update(sql,
                idEnvio,
                fechaRegistro,
                fechaLimiteEntrega,
                envio.getIdAeropuertoOrigen(),
                envio.getIdAeropuertoDestino(),
                envio.getCantidadMaletas(),
                envio.getClienteIdCliente(),
                false
        );

        envio.setIdEnvio(idEnvio);
        envio.setFechaRegistro(fechaRegistro);
        envio.setFechaLimiteEntrega(fechaLimiteEntrega);
        envio.setPlanificado(false);
        return envio;
    }

    public int marcarPlanificado(String idEnvio) {
        return databaseManager.getPrimaryDb().update(
                "UPDATE envio SET planificado = ? WHERE idArchivo = ?",
                true,
                idEnvio
        );
    }

    private LocalDateTime truncarAMinutos(LocalDateTime fechaHora) {
        return fechaHora.truncatedTo(ChronoUnit.MINUTES);
    }

    private void validarDatosMinimos(EnvioDTO envio) {
        if (envio.getIdAeropuertoOrigen() == null) {
            throw new IllegalArgumentException("idAeropuertoOrigen es obligatorio");
        }
        if (envio.getIdAeropuertoDestino() == null) {
            throw new IllegalArgumentException("idAeropuertoDestino es obligatorio");
        }
        if (envio.getCantidadMaletas() == null || envio.getCantidadMaletas() <= 0) {
            throw new IllegalArgumentException("cantidadMaletas debe ser mayor a cero");
        }
        if (envio.getClienteIdCliente() == null) {
            throw new IllegalArgumentException("clienteIdCliente es obligatorio");
        }
    }

    /**
     * Obtiene el total de envíos
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM envio";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
