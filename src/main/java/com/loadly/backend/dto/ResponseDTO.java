package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta genérica
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {
    private boolean exito;
    private String mensaje;
    private T datos;

    public ResponseDTO(boolean exito, String mensaje) {
        this.exito = exito;
        this.mensaje = mensaje;
    }
}
