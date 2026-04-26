package com.loadly.backend.experimento;
 
import com.loadly.backend.algoritmo.genetico.AlgoritmoGenetico;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.algoritmo.aco.AlgoritmoACO;
import com.loadly.backend.model.*;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;
 
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
 
/**
 * Ejecuta el diseño de experimentos para comparar GA vs ACO.
 *
 * Estructura:
 *   - 3 datasets sintéticos: pequeño (20), mediano (100), grande (500)
 *   - 30 réplicas por algoritmo por dataset = 180 ejecuciones totales
 *   - Cada réplica: reset → algoritmo corre Ta segundos → métricas
 *   - Genera resultados_experimento.csv para análisis en Python
 *
 * Parámetros calibrados según tiempos observados:
 *   Dataset    Ta      GA_pob  ACO_hormigas
 *   pequeño    15s     50      30
 *   mediano    40s     50      30
 *   grande     90s     50      10  ← reducido para más iteraciones ACO
 */
@Component
public class ExperimentoRunner {
 
    private static final int    REPLICAS         = 30;
    private static final int[]  TAMANIOS_DATASET = {20, 100, 500};
    private static final int    POBLACION_GA     = 50;
    private static final String ARCHIVO_CSV      = "resultados_experimento.csv";
 
    private final DataService       dataService;
    private final AlgoritmoGenetico algoritmoGenetico;
    private final AlgoritmoACO      algoritmoACO;
 
    public ExperimentoRunner(DataService dataService,
                             AlgoritmoGenetico algoritmoGenetico,
                             AlgoritmoACO algoritmoACO) {
        this.dataService       = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
        this.algoritmoACO      = algoritmoACO;
    }
 
    public void ejecutar() {
        List<MetricasReplica> todasLasMetricas = new ArrayList<>();
 
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("   EXPERIMENTO LOADLY — GA vs ACO");
        System.out.println("   Réplicas por configuración: " + REPLICAS);
        System.out.println("   FO = (planif/total)*100 - inalcanzable*10 - sinRuta*5 - tiempoNormProm");
        System.out.println("═══════════════════════════════════════════════════════════════════\n");
 
        for (int tamano : TAMANIOS_DATASET) {
            String nombreDS    = GeneradorDatasets.nombreDataset(tamano);
            long   taMs        = GeneradorDatasets.taRecomendadoMs(tamano);
            int    hormigasACO = GeneradorDatasets.hormigasACO(tamano);
 
            System.out.printf("─── Dataset: %s (%d envíos) | Ta=%.0fs | GA_pob=%d | ACO_hormigas=%d%n",
                    nombreDS, tamano, taMs / 1000.0, POBLACION_GA, hormigasACO);
 
            // Dataset generado UNA SOLA VEZ — mismo problema para GA y ACO
            List<Envio> dataset = GeneradorDatasets.generar(tamano, dataService.getAeropuertos());
 
            // 30 réplicas GA
            System.out.println("  [GA] Ejecutando " + REPLICAS + " réplicas...");
            for (int r = 1; r <= REPLICAS; r++) {
                MetricasReplica m = ejecutarReplicaGA(dataset, nombreDS, r, taMs);
                todasLasMetricas.add(m);
                System.out.println("    " + m);
            }
 
            // 30 réplicas ACO
            System.out.println("  [ACO] Ejecutando " + REPLICAS + " réplicas...");
            for (int r = 1; r <= REPLICAS; r++) {
                MetricasReplica m = ejecutarReplicaACO(dataset, nombreDS, r, taMs, hormigasACO);
                todasLasMetricas.add(m);
                System.out.println("    " + m);
            }
 
            System.out.println();
        }
 
        exportarCSV(todasLasMetricas);
        imprimirResumen(todasLasMetricas);
    }
 
    private MetricasReplica ejecutarReplicaGA(List<Envio> dataset, String nombreDS,
                                               int replica, long taMs) {
        dataService.resetEstadoExperimento();
 
        Map<String, Aeropuerto>      mapaAeropuertos     = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();
        Map<String, Integer>         capVuelos            = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer>         capAlmacenes         = dataService.getCapacidadDinamicaAlmacenes();
        List<PlanVuelo>              vuelos               = dataService.getVuelos();
 
        long inicio = System.currentTimeMillis();
        Individuo resultado = algoritmoGenetico.ejecutar(
                dataset, vuelos, mapaAeropuertos, mapaVuelosPorOrigen,
                capVuelos, capAlmacenes, POBLACION_GA, taMs);
        double tiempoS = (System.currentTimeMillis() - inicio) / 1000.0;
 
        List<Ruta> rutas = resultado != null ? resultado.getRutas() : null;
        return new MetricasReplica("GA", nombreDS, replica,
                dataset.size(), rutas, mapaAeropuertos, tiempoS);
    }
 
    private MetricasReplica ejecutarReplicaACO(List<Envio> dataset, String nombreDS,
                                                int replica, long taMs, int hormigas) {
        dataService.resetEstadoExperimento();
 
        Map<String, Aeropuerto>      mapaAeropuertos     = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();
        Map<String, Integer>         capVuelos            = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer>         capAlmacenes         = dataService.getCapacidadDinamicaAlmacenes();
 
        long inicio = System.currentTimeMillis();
        Individuo resultado = algoritmoACO.ejecutar(
                dataset, mapaAeropuertos, mapaVuelosPorOrigen,
                capVuelos, capAlmacenes, hormigas, taMs);
        double tiempoS = (System.currentTimeMillis() - inicio) / 1000.0;
 
        List<Ruta> rutas = resultado != null ? resultado.getRutas() : null;
        return new MetricasReplica("ACO", nombreDS, replica,
                dataset.size(), rutas, mapaAeropuertos, tiempoS);
    }
 
    private void exportarCSV(List<MetricasReplica> metricas) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_CSV))) {
            pw.println(MetricasReplica.cabeceraCSV());
            metricas.forEach(m -> pw.println(m.toCSV()));
            System.out.println("✓ CSV generado: " + ARCHIVO_CSV);
        } catch (IOException e) {
            System.err.println("✗ Error al generar CSV: " + e.getMessage());
        }
    }
 
    private void imprimirResumen(List<MetricasReplica> metricas) {
        System.out.println("\n═══════════════════════════════════════════════════════════════════");
        System.out.println("   RESUMEN POR DATASET Y ALGORITMO");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("   Métricas en FO : Planif%, Inalcanzable, SinRuta, TNorm");
        System.out.println("   Solo descriptivo: ViolSLA (siempre 0), Hops, Tviaje, TComp");
 
        for (int tamano : TAMANIOS_DATASET) {
            String ds = GeneradorDatasets.nombreDataset(tamano);
            System.out.println("\nDataset: " + ds + " (" + tamano + " envíos)");
            System.out.printf("  %-5s  %-10s %-10s %-10s %-10s %-12s %-10s %-12s%n",
                    "Alg", "FO_media", "Planif%", "Inalc_med", "SinRuta_med",
                    "TNorm_med", "Hops_med", "TComp_s");
 
            for (String alg : new String[]{"GA", "ACO"}) {
                List<MetricasReplica> f = metricas.stream()
                        .filter(m -> m.dataset.equals(ds) && m.algoritmo.equals(alg))
                        .toList();
 
                double foMedia     = f.stream().mapToDouble(m -> m.fo).average().orElse(0);
                double planifMedia = f.stream().mapToDouble(m ->
                        (double) m.planificados / m.totalEnvios * 100).average().orElse(0);
                double inalcMedia  = f.stream().mapToDouble(m -> m.inalcanzable).average().orElse(0);
                double sinRutaMedia= f.stream().mapToDouble(m -> m.sinRuta).average().orElse(0);
                double tNormMedia  = f.stream().mapToDouble(m -> m.tiempoNormProm).average().orElse(0);
                double hopsMedia   = f.stream().mapToDouble(m -> m.hopsProm).average().orElse(0);
                double tCompMedia  = f.stream().mapToDouble(m -> m.tiempoComputoS).average().orElse(0);
 
                System.out.printf("  %-5s  %-10.4f %-10.1f %-10.2f %-10.2f %-12.4f %-10.2f %-12.2f%n",
                        alg, foMedia, planifMedia, inalcMedia, sinRutaMedia,
                        tNormMedia, hopsMedia, tCompMedia);
            }
        }
 
        System.out.println("\n  (*) TNorm_med: promedio de (tiempoRuta/slaPropio)*10 por ruta planificada.");
        System.out.println("  (*) TComp_s  : siempre ≈ Ta — ambos algoritmos corren hasta agotar el tiempo.");
    }
}
 