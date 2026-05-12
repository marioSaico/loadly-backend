package com.loadly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO para cada evento SSE de la simulación de período.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulacionEventDTO {

    private String tipo; // "INICIO", "ITERACION", "COLAPSO", "RESUMEN_FINAL"
    private String relojSimulado;
    private String limiteLectura;
    private int iteracionActual;
    private int totalIteracionesEstimadas;

    // Datos de aeropuertos (solo en el primer evento "INICIO")
    private List<AeropuertoSimDTO> aeropuertos;

    // Rutas planificadas en esta iteración
    private List<RutaPlanificadaDTO> rutasPlanificadas;

    // Estadísticas de la iteración
    private EstadisticasIteracionDTO estadisticas;

    // Datos de colapso (solo si tipo = "COLAPSO")
    private ColapsoDTO colapso;

    // Estadísticas finales (solo si tipo = "RESUMEN_FINAL")
    private ResumenFinalDTO resumenFinal;

    // =========================================================================
    // SUB-DTOs
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AeropuertoSimDTO {
        private String codigo;
        private String ciudad;
        private String pais;
        private double latitud;
        private double longitud;
        private String continente;
        private int capacidad;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RutaPlanificadaDTO {
        private String idEnvio;
        private String aeropuertoOrigen;
        private String aeropuertoDestino;
        private int cantidadMaletas;
        private long tiempoTotalMinutos;
        private String estado; // PLANIFICADA, SIN_RUTA, INALCANZABLE
        private double fitness;
        private List<VueloPlanificadoDTO> vuelos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VueloPlanificadoDTO {
        private String origen;
        private String destino;
        private String horaSalida;
        private String horaLlegada;
        private int capacidad;
        // Coordenadas para la visualización
        private double latOrigen;
        private double lngOrigen;
        private double latDestino;
        private double lngDestino;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EstadisticasIteracionDTO {
        private int enviosProcesados;
        private int planificados;
        private int sinRuta;
        private int inalcanzables;
        private int enviosEnEspera;
        private double fitnessPromedio;
        private int totalMaletasPlanificadas;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColapsoDTO {
        private String tipoError;
        private String idEnvioCausante;
        private String rutaCausante;
        private int maletasCausantes;
        private String ubicacionConflicto;
        private String detalle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumenFinalDTO {
        private int totalEnviosPlanificados;
        private int totalMaletasPlanificadas;
        private double consumoPromedioSLA;
        private double ocupacionPromedioVuelos;
        private double ocupacionPromedioAlmacenes;
        private double funcionObjetivo;
        private double tiempoEjecucionRealSegundos;
        private boolean colapsoDetectado;
    }
}
