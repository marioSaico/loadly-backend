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
    // Campos para replanificación (null en envíos normales)
    private Integer slaRestanteMinutos;            // SLA que le queda al momento de replanificar
    private String aeropuertoReplanificacionDesde; // null = desde origen, valor = desde escala intermedia
    private LocalDateTime horaDisponibleReplanificacion;// momento GMT en que el envío estará disponible en aeropuertoReplanificacionDesde | null para envíos normales

}