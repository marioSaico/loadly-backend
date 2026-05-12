package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para Envío
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvioDTO {
    private String idEnvio;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaLimiteEntrega;
    private Integer idAeropuertoOrigen;
    private Integer idAeropuertoDestino;
    private Integer cantidadMaletas;
    private Integer clienteIdCliente;
    private Boolean planificado;
}
