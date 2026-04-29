package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // Parámetros de la metaheurística
    private static final double PROB_CRUCE    = 0.85;
    private static final double PROB_MUTACION = 0.15;
    private static final int    TAMANIO_TORNEO = 3;
    private static final int    ELITISMO       = 2;

    // Factor de diversidad para mutación: mayor que en init para explorar más amplio
    private static final double FACTOR_DIVERSIDAD_MUTACION = 0.50;

    private final BuscadorRutas buscadorRutas = new BuscadorRutas();

    public Individuo ejecutar(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            int tamanoPoblacion,
            long tiempoLimiteMs) {

        System.out.println("=== GA - Iniciando Optimizacion ===");
        System.out.println("    Envios a procesar: " + envios.size());

        // 1. Inicializar población
        Poblacion poblacion = new Poblacion(tamanoPoblacion);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos, capVuelos, capAlmacenes);

        Fitness evaluadorFitness = new Fitness();
        evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, capVuelos);

        Individuo mejorIndividuo = obtenerMejorIndividuo(poblacion);

        Random random = new Random();
        long tiempoInicio = System.currentTimeMillis();
        int generacionesContadas = 0;

        Individuo mejorInicial = copiarIndividuo(mejorIndividuo);
        System.out.println("Fitness inicial: " + String.format("%.6f", mejorInicial.getFitness()));

        // 2. Bucle evolutivo con criterio de parada por tiempo
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
            List<Individuo> nuevaGeneracion = new ArrayList<>();

            // --- ELITISMO: los mejores pasan intactos ---
            List<Individuo> poblacionOrdenada = new ArrayList<>(poblacion.getIndividuos());
            poblacionOrdenada.sort((i1, i2) -> Double.compare(i2.getFitness(), i1.getFitness()));
            for (int i = 0; i < ELITISMO && i < tamanoPoblacion; i++) {
                nuevaGeneracion.add(copiarIndividuo(poblacionOrdenada.get(i)));
            }

            // --- REPRODUCCIÓN ---
            while (nuevaGeneracion.size() < tamanoPoblacion) {
                Individuo padre1 = seleccionTorneo(poblacion, random);
                Individuo padre2 = seleccionTorneo(poblacion, random);

                Individuo hijo = (random.nextDouble() < PROB_CRUCE)
                        ? cruzar(padre1, padre2, random)
                        : copiarIndividuo(padre1);

                mutar(hijo, mapaVuelosPorOrigen, mapaAeropuertos, random);
                nuevaGeneracion.add(hijo);
            }

            poblacion.setIndividuos(nuevaGeneracion);
            evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, capVuelos);

            Individuo mejorActual = obtenerMejorIndividuo(poblacion);
            if (mejorActual.getFitness() > mejorIndividuo.getFitness()) {
                mejorIndividuo = copiarIndividuo(mejorActual);
            }

            generacionesContadas++;
        }

        System.out.println("Fitness final:   " + String.format("%.6f", mejorIndividuo.getFitness()));
        System.out.println("Mejora:          " + 
            String.format("%.6f", mejorIndividuo.getFitness() - mejorInicial.getFitness()));

        System.out.println("=== GA - Finalizado ===");
        System.out.println("    Generaciones procesadas: " + generacionesContadas);
        imprimirResumen(mejorIndividuo);

        return mejorIndividuo;
    }

    // =========================================================================
    // MUTACIÓN
    // =========================================================================

    /**
     * Muta un individuo re-ejecutando el A* sin validar capacidades en las rutas
     * seleccionadas. El mayor factor de diversidad (50%) amplía el espacio de
     * exploración. El Fitness penalizará si la ruta resultante viola restricciones.
     */
    private void mutar(
            Individuo individuo,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto> mapaAeropuertos,
            Random random) {

        for (int i = 0; i < individuo.getRutas().size(); i++) {
            if (random.nextDouble() < PROB_MUTACION) {
                Envio envio = individuo.getRutas().get(i).getEnvio();
                Ruta nuevaRuta = buscadorRutas.buscarRuta(
                    envio,
                    null,                    // vuelosDisponibles no se usa aquí
                    mapaVuelosPorOrigen,     // Índice para acceso eficiente
                    mapaAeropuertos,
                    null,                    // Sin validación de capacidad en mutación
                    null,
                    random,
                    FACTOR_DIVERSIDAD_MUTACION
                );
                individuo.getRutas().set(i, nuevaRuta);
            }
        }
    }

    // =========================================================================
    // OPERADORES GENÉTICOS
    // =========================================================================

    /**
     * Selección por torneo: elige al mejor entre TAMANIO_TORNEO individuos aleatorios.
     */
    private Individuo seleccionTorneo(Poblacion poblacion, Random random) {
        Individuo mejorTorneo = null;
        for (int i = 0; i < TAMANIO_TORNEO; i++) {
            int idx = random.nextInt(poblacion.getIndividuos().size());
            Individuo competidor = poblacion.getIndividuos().get(idx);
            if (mejorTorneo == null || competidor.getFitness() > mejorTorneo.getFitness()) {
                mejorTorneo = competidor;
            }
        }
        return mejorTorneo;
    }

    /**
     * Cruce uniforme gen a gen: por cada envío, elige aleatoriamente la ruta
     * del padre1 o del padre2.
     */
    private Individuo cruzar(Individuo padre1, Individuo padre2, Random random) {
        List<Ruta> rutasHijo = new ArrayList<>();
        for (int i = 0; i < padre1.getRutas().size(); i++) {
            Ruta rutaElegida = random.nextBoolean()
                    ? padre1.getRutas().get(i)
                    : padre2.getRutas().get(i);
            rutasHijo.add(copiarRuta(rutaElegida));
        }
        return new Individuo(rutasHijo);
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    private Individuo obtenerMejorIndividuo(Poblacion poblacion) {
        return poblacion.getIndividuos().stream()
                .max(Comparator.comparingDouble(Individuo::getFitness))
                .orElse(poblacion.getIndividuos().get(0));
    }

    private Individuo copiarIndividuo(Individuo original) {
        List<Ruta> rutasCopia = original.getRutas().stream()
                .map(this::copiarRuta)
                .collect(Collectors.toList());
        Individuo copia = new Individuo(rutasCopia);
        copia.setFitness(original.getFitness());
        return copia;
    }

    private Ruta copiarRuta(Ruta original) {
        Ruta copia = new Ruta();
        copia.setEnvio(original.getEnvio());
        copia.setVuelos(new ArrayList<>(original.getVuelos() != null ? original.getVuelos() : new ArrayList<>()));
        copia.setTiempoTotalMinutos(original.getTiempoTotalMinutos());
        copia.setEstado(original.getEstado());
        copia.setIndiceVueloActual(original.getIndiceVueloActual());
        return copia;
    }

    private void imprimirResumen(Individuo mejor) {
        long planificadas = mejor.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        long sinRuta = mejor.getRutas().size() - planificadas;
        System.out.println("    Planificados: " + planificadas + " | Sin ruta: " + sinRuta);
    }
}
