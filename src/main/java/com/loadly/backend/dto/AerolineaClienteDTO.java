package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para AerolineaCliente
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AerolineaClienteDTO {
    private Integer id;
    private String nombre;
}
