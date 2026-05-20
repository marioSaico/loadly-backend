package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulacionEventDTO {
    private String tipo; // Puede ser: "ITERACION", "COLAPSO", "RESUMEN_FINAL"
    private String relojSimulado; // El reloj base (Sa)
    private String limiteLectura; // Hasta donde leyó el algoritmo (Sc)
    
    private List<RutaPlanificadaDTO> rutasPlanificadas;
    private ColapsoDTO colapso;
    private ResumenFinalDTO resumenFinal;

    @Data @Builder
    public static class RutaPlanificadaDTO {
        private String idEnvio;
        private String origen;
        private String destino;
        private int maletas;
        private String fechaRegistro;
        private String fechaLlegada;
        private String duracion;
        private String sla;
        private List<VueloPlanificadoDTO> tramos;
    }

    @Data @Builder
    public static class VueloPlanificadoDTO {
        private int orden;
        private String origen;
        private String destino;
        private String sale;
        private String llega;
        private int maletasVuelo;
        private int capacidadVuelo;
        private int ocupacionAlmacenOrigen;
        private int capacidadAlmacenOrigen;
        private int ocupacionAlmacenDestino; 
        private int capacidadAlmacenDestino;
    }

    @Data @Builder
    public static class ColapsoDTO {
        private String tipoError;
        private String idEnvioCausante;
        private String rutaCausante;
        private int maletasCausantes;
        private String ubicacionConflicto;
        private String detalle;
        private String relojColapso;
    }

    @Data @Builder
    public static class ResumenFinalDTO {
        private int totalEnviosPlanificados;
        private int totalMaletasPlanificadas;
        private double consumoPromedioSLA;
        private double ocupacionPromedioVuelos;
        private double ocupacionPromedioAlmacenes;
        private double funcionObjetivo;
        private double tiempoEjecucionSegundos;
        private String estadoFinal;
    }
}