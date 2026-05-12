package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para Registro de Monitoreo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroMonitoreoDTO {
    private Integer idRegistro;
    private String envioIdEnvio;
    private Integer aeropuertoIdAeropuerto;
    private String estado;
    private LocalDateTime fechaHora;
}
