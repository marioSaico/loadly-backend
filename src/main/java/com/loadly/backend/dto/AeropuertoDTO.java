package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para Aeropuerto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AeropuertoDTO {
    private Integer idAeropuerto;
    private String codigo;
    private String ciudad;
    private String pais;
    private String abreviatura;
    private Integer gmt;
    private Integer capacidad;
    private Double latitud;
    private Double longitud;
    private String continente;
}
