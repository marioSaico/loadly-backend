package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * DTO para Plan de Vuelo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanVueloDTO {
    private Integer idPlanVuelo;
    private Integer idAeropuertoOrigen;
    private Integer idAeropuertoDestino;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private Integer capacidad;
    private Boolean cancelado;
}
