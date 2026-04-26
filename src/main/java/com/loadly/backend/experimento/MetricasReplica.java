package com.loadly.backend.experimento;
 
import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Ruta;
 
import java.util.List;
import java.util.Map;
 
/**
 * Almacena y calcula las métricas de una sola réplica del experimento.
 *
 * Función Objetivo (FO) — más alto = mejor:
 *
 *   FO = (planificados / total) * 100    <- cobertura         (0-100)
 *      - (inalcanzable   * 10.0)         <- sin ruta posible
 *      - (sinRuta        *  5.0)         <- almacén origen lleno
 *      - tiempoNormProm                  <- calidad de ruta   (0-10)
 *        donde tiempoNormProm = promedio de (tiempoRuta/slaPropio)*10
 *        mismo continente    -> SLA = 1440 min (24h)
 *        distinto continente -> SLA = 2880 min (48h)
 *
 * Métricas descriptivas (en CSV, fuera de FO):
 *   - violacionesSLA : siempre 0 en nuestro sistema porque el A* y el
 *                      constructor ACO ya validan SLA internamente.
 *                      Se mantiene como descriptiva para el IEN.
 *   - hopsProm       : promedio de vuelos por ruta planificada
 *   - tiempoProm     : minutos reales de viaje promedio (sin normalizar)
 *   - tiempoComputoS : ambos algoritmos corren hasta agotar el Ta,
 *                      por lo que siempre es ≈ Ta. Solo descriptivo.
 */
public class MetricasReplica {
 
    private static final int SLA_MISMO_CONTINENTE    = 1440;
    private static final int SLA_DISTINTO_CONTINENTE = 2880;
 
    public final String algoritmo;
    public final String dataset;
    public final int    replica;
    public final int    totalEnvios;
    public final int    planificados;
    public final int    inalcanzable;
    public final int    sinRuta;
    public final int    violacionesSLA;  // descriptivo — siempre 0 en nuestro sistema
    public final double tiempoNormProm;  // entra en FO
    public final double tiempoProm;      // descriptivo — minutos reales sin normalizar
    public final double hopsProm;        // descriptivo
    public final double tiempoComputoS;  // descriptivo — siempre ≈ Ta
    public final double fo;
 
    public MetricasReplica(String algoritmo, String dataset, int replica,
                           int totalEnvios,
                           List<Ruta> rutas,
                           Map<String, Aeropuerto> mapaAeropuertos,
                           double tiempoComputoS) {
        this.algoritmo      = algoritmo;
        this.dataset        = dataset;
        this.replica        = replica;
        this.totalEnvios    = totalEnvios;
        this.tiempoComputoS = tiempoComputoS;
 
        int    cPlanificados   = 0;
        int    cInalcanzable   = 0;
        int    cSinRuta        = 0;
        int    cViolacionesSLA = 0;
        long   sumHops         = 0;
        long   sumTiempo       = 0;
        double sumTiempoNorm   = 0.0;
 
        if (rutas != null) {
            for (Ruta ruta : rutas) {
                switch (ruta.getEstado()) {
                    case PLANIFICADA -> {
                        cPlanificados++;
                        int hops = ruta.getVuelos() != null ? ruta.getVuelos().size() : 1;
                        sumHops += hops;
                        long tiempoRuta = ruta.getTiempoTotalMinutos();
                        sumTiempo += tiempoRuta;
                        int sla = calcularSLA(ruta, mapaAeropuertos);
                        if (tiempoRuta > sla) cViolacionesSLA++;
                        sumTiempoNorm += (tiempoRuta / (double) sla) * 10.0;
                    }
                    case INALCANZABLE -> cInalcanzable++;
                    case SIN_RUTA     -> cSinRuta++;
                    default           -> {}
                }
            }
        } else {
            cSinRuta = totalEnvios;
        }
 
        this.planificados   = cPlanificados;
        this.inalcanzable   = cInalcanzable;
        this.sinRuta        = cSinRuta;
        this.violacionesSLA = cViolacionesSLA;
        this.hopsProm       = cPlanificados > 0 ? (double) sumHops   / cPlanificados : 0.0;
        this.tiempoProm     = cPlanificados > 0 ? (double) sumTiempo / cPlanificados : 0.0;
        // Si no hay planificados → penalización máxima (10.0)
        this.tiempoNormProm = cPlanificados > 0 ? sumTiempoNorm / cPlanificados : 10.0;
        this.fo             = calcularFO();
    }
 
    private int calcularSLA(Ruta ruta, Map<String, Aeropuerto> mapaAeropuertos) {
        Aeropuerto aOrigen  = mapaAeropuertos.get(ruta.getEnvio().getAeropuertoOrigen());
        Aeropuerto aDestino = mapaAeropuertos.get(ruta.getEnvio().getAeropuertoDestino());
        if (aOrigen == null || aDestino == null) return SLA_DISTINTO_CONTINENTE;
        String cO = aOrigen.getContinente();
        String cD = aDestino.getContinente();
        return (cO != null && cO.equals(cD)) ? SLA_MISMO_CONTINENTE : SLA_DISTINTO_CONTINENTE;
    }
 
    private double calcularFO() {
        if (totalEnvios == 0) return 0.0;
        return ((double) planificados / totalEnvios) * 100.0
             - (inalcanzable * 10.0)
             - (sinRuta      *  5.0)
             - tiempoNormProm;
    }
 
    public static String cabeceraCSV() {
        return "algoritmo,dataset,replica,fo,planificados,inalcanzable,sin_ruta,"
             + "tiempo_norm_prom,tiempo_prom_min,hops_prom,violaciones_sla,tiempo_computo_s,total_envios";
    }
 
    public String toCSV() {
        return String.format("%s,%s,%d,%.4f,%d,%d,%d,%.4f,%.2f,%.2f,%d,%.4f,%d",
                algoritmo, dataset, replica, fo,
                planificados, inalcanzable, sinRuta,
                tiempoNormProm, tiempoProm, hopsProm,
                violacionesSLA, tiempoComputoS, totalEnvios);
    }
 
    @Override
    public String toString() {
        return String.format(
            "[%s | %s | R%02d] Planif=%d/%d | Inalc=%d | SinRuta=%d | TNorm=%.2f | FO=%.4f | t=%.2fs",
            algoritmo, dataset, replica,
            planificados, totalEnvios, inalcanzable, sinRuta,
            tiempoNormProm, fo, tiempoComputoS);
    }
}
