package com.loadly.backend.algoritmo.aco;
 
import com.loadly.backend.algoritmo.genetico.Fitness;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;
 
import java.util.*;
 
/**
 * ============================================================
 * AlgoritmoACO — Motor principal del Ant Colony Optimization
 * ============================================================
 *
 * Analogía con el GA para entender el paralelo:
 *   GA:  Población → Individuos → Evolución (cruce + mutación) → Fitness
 *   ACO: Colonia   → Hormigas   → Construcción guiada por feromonas → Fitness
 *
 * Flujo de cada iteración ACO:
 *   1. Evaporación:   las feromonas se reducen (rastro se desvanece).
 *   2. Depósito:      las hormigas élite refuerzan los vuelos que usaron.
 *   3. Construcción:  cada hormiga construye su solución guiada por feromonas + heurística.
 *   4. Evaluación:    se calcula el fitness con el mismo Fitness.java del GA.
 *   5. Actualización: se guarda el mejor global si hay mejora.
 *
 * CRITERIO DE PARADA — solo por tiempo (Ta), igual que el GA:
 *   El bucle corre durante exactamente tiempoLimiteMs milisegundos.
 *   Esto es intencional: garantiza que GA y ACO se ejecuten bajo las mismas
 *   condiciones de tiempo, haciendo que la comparación del IEN sea válida.
 *   (Versiones anteriores paraban también por "iteraciones sin mejora",
 *   lo que hacía que el ACO terminara antes que el GA y no fueran comparables.)
 */
@Component
public class AlgoritmoACO {
 
    /** Porcentaje de hormigas élite que depositan feromona por iteración (top 20%). */
    private static final double PORCENTAJE_ELITE = 0.20;
 
    private final Fitness evaluadorFitness;
 
    public AlgoritmoACO(Fitness evaluadorFitness) {
        this.evaluadorFitness = evaluadorFitness;
    }
 
    // =========================================================================
    //  MÉTODO PRINCIPAL
    // =========================================================================
 
    /**
     * Ejecuta el ACO durante exactamente tiempoLimiteMs milisegundos.
     *
     * @param envios               Envíos a planificar en esta ventana temporal.
     * @param mapaAeropuertos      Mapa de aeropuertos con datos completos.
     * @param mapaVuelosPorOrigen  Índice de vuelos por aeropuerto de origen (O(1)).
     * @param capVuelos            Capacidades dinámicas actuales de los vuelos.
     * @param capAlmacenes         Capacidades dinámicas actuales de los almacenes.
     * @param numHormigas          Número de hormigas (análogo al tamaño de población del GA).
     * @param tiempoLimiteMs       Tiempo máximo de ejecución en ms (= Ta × 1000).
     * @return Mejor Individuo encontrado, compatible con Planificador y DataService.
     */
    public Individuo ejecutar(
            List<Envio> envios,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            int numHormigas,
            long tiempoLimiteMs) {
 
        System.out.println("=== ACO - Iniciando Optimización ===");
        System.out.println("    Envíos a procesar: " + envios.size());
        System.out.println("    Hormigas:          " + numHormigas);
 
        // --- Crear la colonia (inicializa feromonas uniformes en todos los vuelos) ---
        Colonia colonia = new Colonia(numHormigas, mapaAeropuertos, mapaVuelosPorOrigen);
 
        // --- Construcción inicial ---
        // Primera iteración: feromonas iguales → selección guiada principalmente por heurística.
        colonia.construirSoluciones(envios, capVuelos, capAlmacenes);
        evaluarColonia(colonia, mapaAeropuertos, capVuelos);
 
        Hormiga mejorHormiga = colonia.getMejorHormiga();
        Hormiga mejorGlobal  = copiarHormiga(mejorHormiga);
        System.out.println("    Fitness inicial: " + String.format("%.6f", mejorGlobal.getFitness()));
 
        // --- Bucle principal ACO — controlado SOLO por tiempo (igual que el GA) ---
        long tiempoInicio   = System.currentTimeMillis();
        int  iteracionesTotal = 0;
 
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
 
            // Paso 1 — Evaporación: reducir todas las feromonas
            // El rastro se desvanece para no quedar atrapado en soluciones antiguas
            colonia.getFeromenaGrafo().evaporar();
 
            // Paso 2 — Depósito: solo las hormigas élite refuerzan sus rutas
            // Se ordenan de mayor a menor fitness y solo el top 20% deposita feromona
            List<Hormiga> ordenadas = new ArrayList<>(colonia.getHormigas());
            ordenadas.sort((h1, h2) -> Double.compare(h2.getFitness(), h1.getFitness()));
            int numElite = Math.max(1, (int)(numHormigas * PORCENTAJE_ELITE));
            for (int i = 0; i < numElite; i++) {
                colonia.getFeromenaGrafo().depositarFeromona(ordenadas.get(i));
            }
 
            // Paso 3 — Construcción: nuevas soluciones guiadas por feromonas actualizadas
            colonia.construirSoluciones(envios, capVuelos, capAlmacenes);
            evaluarColonia(colonia, mapaAeropuertos, capVuelos);
 
            // Paso 4 — Actualizar mejor global si hay mejora
            Hormiga mejorActual = colonia.getMejorHormiga();
            if (mejorActual != null && mejorActual.getFitness() > mejorGlobal.getFitness()) {
                mejorGlobal = copiarHormiga(mejorActual);
                System.out.println("    [ACO] Iter " + iteracionesTotal
                        + " → Nuevo mejor fitness: "
                        + String.format("%.6f", mejorGlobal.getFitness()));
            }
 
            iteracionesTotal++;
        }
 
        System.out.println("=== ACO - Finalizado ===");
        System.out.println("    Iteraciones ejecutadas: " + iteracionesTotal);
        System.out.println("    Mejor Fitness:          "
                + String.format("%.6f", mejorGlobal.getFitness()));
        imprimirResumen(mejorGlobal);
 
        // Convertir la mejor Hormiga a Individuo para compatibilidad con el sistema
        return new Individuo(mejorGlobal.getRutas());
    }
 
    // =========================================================================
    //  MÉTODOS PRIVADOS DE APOYO
    // =========================================================================
 
    /**
     * Evalúa el fitness de todas las hormigas usando Fitness.java del GA.
     * Usar la misma función objetivo garantiza que la comparación GA vs ACO
     * en el IEN sea válida (no se comparan métricas distintas).
     */
    private void evaluarColonia(Colonia colonia,
                                 Map<String, Aeropuerto> mapaAeropuertos,
                                 Map<String, Integer> capVuelos) {
        for (Hormiga hormiga : colonia.getHormigas()) {
            Individuo indv = new Individuo(hormiga.getRutas());
            evaluadorFitness.evaluar(indv, mapaAeropuertos, capVuelos);
            hormiga.setFitness(indv.getFitness());
            hormiga.setFeromonaDepositada(indv.getFitness());
        }
    }
 
    /**
     * Crea una copia de una hormiga para preservar el mejor global entre iteraciones.
     * Sin esta copia, la referencia se sobreescribiría en la siguiente construcción.
     */
    private Hormiga copiarHormiga(Hormiga original) {
        Hormiga copia = new Hormiga(new ArrayList<>(original.getRutas()));
        copia.setFitness(original.getFitness());
        copia.setFeromonaDepositada(original.getFeromonaDepositada());
        return copia;
    }
 
    /**
     * Imprime un resumen de los estados de las rutas en la mejor solución encontrada.
     */
    private void imprimirResumen(Hormiga mejorHormiga) {
        long planificadas  = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        long inalcanzables = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.INALCANZABLE).count();
        long sinRuta       = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA).count();
 
        System.out.println("    Rutas PLANIFICADAS:  " + planificadas);
        System.out.println("    Rutas INALCANZABLES: " + inalcanzables);
        System.out.println("    Rutas SIN_RUTA:      " + sinRuta);
    }
}
