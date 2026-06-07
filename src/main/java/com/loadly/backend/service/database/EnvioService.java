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

    public List<EnvioDTO> obtenerNoPlanificadosEnVentana(LocalDateTime desde, LocalDateTime hasta) {
        String sql = "SELECT idEnvio, fechaRegistro, fechaLimiteEntrega, idAeropuertoOrigen, idAeropuertoDestino, cantidadMaletas, cliente_idCliente, planificado " +
                "FROM envio WHERE planificado = ? AND fechaRegistro >= ? AND fechaRegistro < ? ORDER BY fechaRegistro ASC";

        return databaseManager.getPrimaryDb().query(sql, new Object[]{false, desde, hasta}, (rs, rowNum) -> new EnvioDTO(
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

    public EnvioDTO registrarEnvio(EnvioDTO envio) {
        LocalDateTime fechaRegistro = truncarAMinutos(
                envio.getFechaRegistro() != null ? envio.getFechaRegistro() : LocalDateTime.now()
        );
        LocalDateTime fechaLimiteEntrega = truncarAMinutos(
                envio.getFechaLimiteEntrega() != null ? envio.getFechaLimiteEntrega() : fechaRegistro.plusHours(48)
        );
        String idEnvio = envio.getIdEnvio() != null && !envio.getIdEnvio().isBlank()
                ? envio.getIdEnvio()
                : generarIdEnvio();

        validarDatosMinimos(envio);

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
        String sql = "UPDATE envio SET planificado = ? WHERE idEnvio = ?";
        return databaseManager.getPrimaryDb().update(sql, true, idEnvio);
    }

    private void validarDatosMinimos(EnvioDTO envio) {
        if (envio.getIdAeropuertoOrigen() == null) {
            throw new IllegalArgumentException("idAeropuertoOrigen es necesario para registrar el envio");
        }
        if (envio.getIdAeropuertoDestino() == null) {
            throw new IllegalArgumentException("idAeropuertoDestino es necesario para registrar el envio");
        }
        if (envio.getCantidadMaletas() == null || envio.getCantidadMaletas() <= 0) {
            throw new IllegalArgumentException("cantidadMaletas debe ser mayor a 0");
        }
        if (envio.getClienteIdCliente() == null) {
            throw new IllegalArgumentException("clienteIdCliente es necesario para registrar el envio");
        }
    }

    private LocalDateTime truncarAMinutos(LocalDateTime fechaHora) {
        return fechaHora.truncatedTo(ChronoUnit.MINUTES);
    }

    private String generarIdEnvio() {
        return "OD-" + System.currentTimeMillis();
    }

    /**
     * Obtiene el total de envíos
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM envio";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }
}
