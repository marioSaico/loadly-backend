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
 *   - 3 datasets: pequeño (20), mediano (100), grande (500)
 *   - 30 réplicas por algoritmo por dataset
 *   - Cada réplica = una ejecución con Ta fijo sobre el dataset completo
 *   - Genera resultados_experimento.csv con todas las métricas
 *
 * Invocado desde ExperimentoMain (main separado, no afecta BackendApplication).
 */
@Component
public class ExperimentoRunner {

    // ── Parámetros del experimento ─────────────────────────────────────────────
    private static final int    REPLICAS          = 30;
    private static final int[]  TAMANIOS_DATASET  = {20, 100, 500};

    // Parámetros GA
    private static final int    POBLACION_GA      = 50;

    // Parámetros ACO
    private static final int    HORMIGAS_ACO      = 30;

    // Fecha/hora ficticia para confirmarPlan (el experimento usa una sola iteración)
    private static final String FECHA_EXPERIMENTO = "20260101-00-00";

    // Archivo de salida
    private static final String ARCHIVO_CSV       = "resultados_experimento.csv";

    // ── Dependencias ───────────────────────────────────────────────────────────
    private final DataService      dataService;
    private final AlgoritmoGenetico algoritmoGenetico;
    private final AlgoritmoACO      algoritmoACO;

    public ExperimentoRunner(DataService dataService,
                             AlgoritmoGenetico algoritmoGenetico,
                             AlgoritmoACO algoritmoACO) {
        this.dataService       = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
        this.algoritmoACO      = algoritmoACO;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MÉTODO PRINCIPAL — llamado desde ExperimentoMain
    // ══════════════════════════════════════════════════════════════════════════

    public void ejecutar() {
        List<MetricasReplica> todasLasMetricas = new ArrayList<>();

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   EXPERIMENTO LOADLY — GA vs ACO");
        System.out.println("   Réplicas por configuración: " + REPLICAS);
        System.out.println("═══════════════════════════════════════════════════\n");

        for (int tamano : TAMANIOS_DATASET) {
            String nombreDS = GeneradorDatasets.nombreDataset(tamano);
            long   taMs     = GeneradorDatasets.taRecomendadoMs(tamano);

            System.out.printf("─── Dataset: %s (%d envíos) | Ta=%.0fs ──────────────%n",
                    nombreDS, tamano, taMs / 1000.0);

            // Generamos el dataset UNA SOLA VEZ por tamaño
            // La misma lista de envíos se usa para las 30 réplicas de GA y las 30 de ACO
            // Esto garantiza que las condiciones del problema sean idénticas
            List<Envio> dataset = GeneradorDatasets.generar(tamano, dataService.getAeropuertos());

            // ── 30 réplicas GA ──────────────────────────────────────────────
            System.out.println("  [GA] Ejecutando " + REPLICAS + " réplicas...");
            for (int r = 1; r <= REPLICAS; r++) {
                MetricasReplica m = ejecutarReplicaGA(dataset, nombreDS, r, taMs);
                todasLasMetricas.add(m);
                System.out.println("    " + m);
            }

            // ── 30 réplicas ACO ─────────────────────────────────────────────
            System.out.println("  [ACO] Ejecutando " + REPLICAS + " réplicas...");
            for (int r = 1; r <= REPLICAS; r++) {
                MetricasReplica m = ejecutarReplicaACO(dataset, nombreDS, r, taMs);
                todasLasMetricas.add(m);
                System.out.println("    " + m);
            }

            System.out.println();
        }

        // ── Exportar CSV ────────────────────────────────────────────────────
        exportarCSV(todasLasMetricas);
        imprimirResumen(todasLasMetricas);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RÉPLICA GA
    // ══════════════════════════════════════════════════════════════════════════

    private MetricasReplica ejecutarReplicaGA(List<Envio> dataset, String nombreDS,
                                               int replica, long taMs) {
        // 1. Reset del estado dinámico (capacidades, backlog, eventos)
        dataService.resetEstadoExperimento();

        // 2. Datos estáticos (aeropuertos y vuelos no cambian)
        Map<String, Aeropuerto>      mapaAeropuertos     = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();
        Map<String, Integer>         capVuelos            = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer>         capAlmacenes         = dataService.getCapacidadDinamicaAlmacenes();
        List<PlanVuelo>              vuelos               = dataService.getVuelos();

        // 3. Ejecutar GA con Ta como límite de tiempo
        long inicio = System.currentTimeMillis();

        Individuo resultado = algoritmoGenetico.ejecutar(
                dataset, vuelos, mapaAeropuertos, mapaVuelosPorOrigen,
                capVuelos, capAlmacenes,
                POBLACION_GA, taMs
        );

        double tiempoS = (System.currentTimeMillis() - inicio) / 1000.0;

        // 4. Contabilizar métricas
        return contabilizar("GA", nombreDS, replica, dataset.size(), resultado, tiempoS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RÉPLICA ACO
    // ══════════════════════════════════════════════════════════════════════════

    private MetricasReplica ejecutarReplicaACO(List<Envio> dataset, String nombreDS,
                                                int replica, long taMs) {
        // 1. Reset del estado dinámico
        dataService.resetEstadoExperimento();

        // 2. Datos estáticos
        Map<String, Aeropuerto>      mapaAeropuertos     = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();
        Map<String, Integer>         capVuelos            = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer>         capAlmacenes         = dataService.getCapacidadDinamicaAlmacenes();

        // 3. Ejecutar ACO con Ta como límite de tiempo
        long inicio = System.currentTimeMillis();

        Individuo resultado = algoritmoACO.ejecutar(
                dataset, mapaAeropuertos, mapaVuelosPorOrigen,
                capVuelos, capAlmacenes,
                HORMIGAS_ACO, taMs
        );

        double tiempoS = (System.currentTimeMillis() - inicio) / 1000.0;

        // 4. Contabilizar métricas
        return contabilizar("ACO", nombreDS, replica, dataset.size(), resultado, tiempoS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONTABILIZACIÓN DE MÉTRICAS
    // ══════════════════════════════════════════════════════════════════════════

    private MetricasReplica contabilizar(String algoritmo, String dataset,
                                          int replica, int total,
                                          Individuo resultado, double tiempoS) {
        int planificados = 0, inalcanzable = 0, sinRuta = 0;

        if (resultado != null && resultado.getRutas() != null) {
            for (Ruta ruta : resultado.getRutas()) {
                switch (ruta.getEstado()) {
                    case PLANIFICADA  -> planificados++;
                    case INALCANZABLE -> inalcanzable++;
                    case SIN_RUTA     -> sinRuta++;
                    default           -> {} // EN_TRANSITO, ENTREGADA, RETRASADA — no deberían aparecer aquí
                }
            }
        } else {
            // Si el algoritmo retornó null, todos los envíos quedaron sin ruta
            sinRuta = total;
        }

        return new MetricasReplica(algoritmo, dataset, replica,
                total, planificados, inalcanzable, sinRuta, tiempoS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EXPORTAR CSV
    // ══════════════════════════════════════════════════════════════════════════

    private void exportarCSV(List<MetricasReplica> metricas) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_CSV))) {
            pw.println(MetricasReplica.cabeceraCSV());
            for (MetricasReplica m : metricas) {
                pw.println(m.toCSV());
            }
            System.out.println("✓ CSV generado: " + ARCHIVO_CSV);
        } catch (IOException e) {
            System.err.println("✗ Error al generar CSV: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RESUMEN EN CONSOLA
    // ══════════════════════════════════════════════════════════════════════════

    private void imprimirResumen(List<MetricasReplica> metricas) {
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("   RESUMEN POR DATASET Y ALGORITMO");
        System.out.println("═══════════════════════════════════════════════════");

        String[] datasets   = {"pequeño", "mediano", "grande"};
        String[] algoritmos = {"GA", "ACO"};

        for (String ds : datasets) {
            System.out.println("\nDataset: " + ds);
            for (String alg : algoritmos) {
                OptionalDouble mediaFO = metricas.stream()
                        .filter(m -> m.dataset.equals(ds) && m.algoritmo.equals(alg))
                        .mapToDouble(m -> m.fo)
                        .average();
                OptionalDouble mediaPlanif = metricas.stream()
                        .filter(m -> m.dataset.equals(ds) && m.algoritmo.equals(alg))
                        .mapToDouble(m -> m.planificados)
                        .average();
                System.out.printf("  %-4s → FO_media=%.4f | Planif_media=%.1f%n",
                        alg,
                        mediaFO.orElse(0),
                        mediaPlanif.orElse(0));
            }
        }
    }
}
