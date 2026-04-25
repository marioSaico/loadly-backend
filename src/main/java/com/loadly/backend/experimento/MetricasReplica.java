package com.loadly.backend.experimento;

/**
 * Almacena las métricas de una sola réplica del experimento.
 * Una réplica = una ejecución del algoritmo (GA o ACO) sobre un dataset completo.
 *
 * Función Objetivo (FO):
 *   FO = (planificados / total) * 100   ← % envíos resueltos (0-100, más es mejor)
 *      - (inalcanzable * 10)             ← penalización fuerte por ruta imposible
 *      - (sinRuta * 5)                   ← penalización media por almacén lleno
 *      - (tiempoComputoS * 0.01)         ← desempate leve por velocidad
 *
 * Rango esperado: 0 (todo falla) a ~100 (todo se planifica rápido).
 * Un FO más alto = algoritmo más efectivo en esa réplica.
 */
public class MetricasReplica {

    public final String algoritmo;       // "GA" o "ACO"
    public final String dataset;         // "pequeño", "mediano" o "grande"
    public final int    replica;         // 1 al 30
    public final int    totalEnvios;     // tamaño del dataset
    public final int    planificados;    // envíos con estado PLANIFICADA
    public final int    inalcanzable;    // envíos con estado INALCANZABLE
    public final int    sinRuta;         // envíos con estado SIN_RUTA
    public final double tiempoComputoS;  // segundos reales que tardó el algoritmo
    public final double fo;              // Función Objetivo compuesta (ver fórmula arriba)

    public MetricasReplica(String algoritmo, String dataset, int replica,
                           int totalEnvios, int planificados, int inalcanzable,
                           int sinRuta, double tiempoComputoS) {
        this.algoritmo      = algoritmo;
        this.dataset        = dataset;
        this.replica        = replica;
        this.totalEnvios    = totalEnvios;
        this.planificados   = planificados;
        this.inalcanzable   = inalcanzable;
        this.sinRuta        = sinRuta;
        this.tiempoComputoS = tiempoComputoS;
        this.fo             = calcularFO();
    }

    private double calcularFO() {
        if (totalEnvios == 0) return 0.0;
        return ((double) planificados / totalEnvios) * 100.0
             - (inalcanzable * 10.0)
             - (sinRuta      *  5.0)
             - (tiempoComputoS * 0.01);
    }

    /** Cabecera CSV para el archivo de resultados */
    public static String cabeceraCSV() {
        return "algoritmo,dataset,replica,fo,planificados,inalcanzable,sin_ruta,tiempo_computo_s,total_envios";
    }

    /** Línea CSV de esta réplica */
    public String toCSV() {
        return String.format("%s,%s,%d,%.4f,%d,%d,%d,%.4f,%d",
                algoritmo, dataset, replica, fo,
                planificados, inalcanzable, sinRuta, tiempoComputoS, totalEnvios);
    }

    @Override
    public String toString() {
        return String.format(
            "[%s | %s | R%02d] Planif=%d/%d | Inalc=%d | SinRuta=%d | FO=%.4f | t=%.2fs",
            algoritmo, dataset, replica,
            planificados, totalEnvios, inalcanzable, sinRuta, fo, tiempoComputoS);
    }
}
