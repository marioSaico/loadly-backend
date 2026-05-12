package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para Usuario/Cliente
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private Integer idCliente;
    private String nombre;
    private String rol;
    private String contacto;
}
