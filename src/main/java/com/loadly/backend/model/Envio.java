package com.loadly.backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Envio {

    private String idEnvio;
    private String fechaRegistro;
    private int horaRegistro;
    private int minutoRegistro;
    private String aeropuertoOrigen;
    private String aeropuertoDestino;
    private int cantidadMaletas;
    private String idCliente;
    private boolean planificado;

    // Campo optimizado para filtrado rápido en memoria
    private LocalDateTime tiempoRegistroGMT;

}