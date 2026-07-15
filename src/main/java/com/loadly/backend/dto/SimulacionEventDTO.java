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
    
    // Marca temporal del servidor para sincronizar el reloj visual entre clientes.
    private Long inicioVisualEpochMs;

    private List<RutaPlanificadaDTO> rutasPlanificadas;
    private ColapsoDTO colapso;
    private ResumenFinalDTO resumenFinal;

    // Para tipo "CANCELACION"
    private List<EnvioAfectadoDTO> enviosAfectadosCancelacion;
    private String vueloCancelado;

    // ── Inner classes ────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EnvioAfectadoDTO {
        private String idEnvio;
        private Integer numeroLote;
        private String idCliente;
        private String origen;
        private String destino;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RutaPlanificadaDTO {
        private String idEnvio;
        private String idCliente;
        private Integer numeroLote; // null si no fue dividido, 0, 1, 2... si fue dividido
        private String origen;
        private String destino;
        private int maletas;
        private String fechaRegistro;
        private String fechaRecojo;
        private int ocupacionAlmacenRegistro;
        private int capacidadAlmacenRegistro;
        private int ocupacionAlmacenRecojo;
        private int capacidadAlmacenRecojo;
        private String duracion;
        private String sla;
        private boolean esReplanificacion;
        private String replanificadoDesde; // null si es normal, "SCL" si viene de intermedio
        private List<VueloPlanificadoDTO> tramos;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ColapsoDTO {
        private String tipoError;
        private String idEnvioCausante;
        private String rutaCausante;
        private int maletasCausantes;
        private String ubicacionConflicto;
        private String detalle;
        private String relojColapso;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
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
