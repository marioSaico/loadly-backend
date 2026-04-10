package com.loadly.backend.model;

import lombok.Data;

@Data
public class Envio {

    private String idEnvio;
    private String fechaRegistro;
    private String horaRegistro;
    private String minutoRegistro;
    private String aeropuertoOrigen;
    private String aeropuertoDestino;
    private int cantidadMaletas;
    private String idCliente;
    private boolean planificado;

}