package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para Asignación (ruta de envío)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionDTO {
    private Integer idasignacion;
    private Integer planVueloIdPlanVuelo;
    private String envioIdEnvio;
    private Integer ordenRuta;
}
