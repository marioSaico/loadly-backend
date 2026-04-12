package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // ── Parámetros del GA ─────────────────────────────────────────────────
    // Tamaño de la población (cantidad de individuos por generación)
    private static final int TAMANIO_POBLACION = 50;

    // Número máximo de generaciones que ejecutará el GA
    private static final int MAX_GENERACIONES = 100;

    // Probabilidad de cruce entre dos padres (80%)
    private static final double PROB_CRUCE = 0.8;

    // Probabilidad de mutación de un individuo (10%)
    private static final double PROB_MUTACION = 0.1;

    // Tamaño del torneo para selección (cuántos individuos compiten)
    private static final int TAMANIO_TORNEO = 3;

    // Cantidad de mejores individuos que pasan directamente a la
    // siguiente generación sin modificarse (elitismo)
    private static final int ELITISMO = 2;

    // Tiempo mínimo de escala en minutos (parámetro del problema)
    private static final int TIEMPO_MINIMO_ESCALA = 10;

    // Tiempo de recojo en destino final en minutos (parámetro)
    private static final int TIEMPO_RECOJO_DESTINO = 10;

    private final Fitness fitness;
    private final Random random = new Random();

    public AlgoritmoGenetico(Fitness fitness) {
        this.fitness = fitness;
    }

    /**
     * Ejecuta el algoritmo genético completo.
     * Recibe los envíos pendientes, vuelos y aeropuertos disponibles.
     * Devuelve el mejor individuo encontrado (mejor plan de rutas).
     *
     * @param envios          lista de envíos pendientes a planificar
     * @param vuelos          lista de todos los vuelos disponibles
     * @param mapaAeropuertos mapa de aeropuertos por código
     * @return el mejor individuo encontrado con su plan de rutas
     */
    public Individuo ejecutar(List<Envio> envios,
                               List<PlanVuelo> vuelos,
                               Map<String, Aeropuerto> mapaAeropuertos) {

        System.out.println("=== Iniciando Algoritmo Genético ===");
        System.out.println("Envíos a planificar: " + envios.size());

        // ── Paso 1: Generar población inicial ─────────────────────────────
        Poblacion poblacion = new Poblacion(TAMANIO_POBLACION);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos);

        // ── Paso 2: Evaluar fitness de la población inicial ───────────────
        fitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);

        // Guardar el mejor individuo encontrado hasta ahora
        Individuo mejorGlobal = copiarIndividuo(
            poblacion.getMejorIndividuo()
        );

        System.out.println("Fitness inicial mejor: "
            + String.format("%.4f", mejorGlobal.getFitness()));

        // ── Paso 3: Ciclo evolutivo ───────────────────────────────────────
        for (int generacion = 0; generacion < MAX_GENERACIONES; 
                generacion++) {

            // Nueva población para esta generación
            List<Individuo> nuevaPoblacion = new ArrayList<>();

            // Elitismo: los mejores individuos pasan directamente
            List<Individuo> elite = obtenerElite(
                poblacion.getIndividuos(), ELITISMO
            );
            nuevaPoblacion.addAll(elite);

            // Generar el resto de la nueva población
            while (nuevaPoblacion.size() < TAMANIO_POBLACION) {

                // ── Selección por torneo ──────────────────────────────────
                Individuo padre1 = seleccionTorneo(
                    poblacion.getIndividuos()
                );
                Individuo padre2 = seleccionTorneo(
                    poblacion.getIndividuos()
                );

                // ── Cruce ─────────────────────────────────────────────────
                Individuo hijo;
                if (random.nextDouble() < PROB_CRUCE) {
                    hijo = cruzar(padre1, padre2, vuelos, mapaAeropuertos);
                } else {
                    // Sin cruce, el hijo es copia del padre1
                    hijo = copiarIndividuo(padre1);
                }

                // ── Mutación ──────────────────────────────────────────────
                if (random.nextDouble() < PROB_MUTACION) {
                    mutar(hijo, vuelos, mapaAeropuertos);
                }

                // ── Evaluar fitness del hijo ──────────────────────────────
                fitness.evaluar(hijo, mapaAeropuertos, vuelos);

                nuevaPoblacion.add(hijo);
            }

            // Reemplazar la población con la nueva generación
            poblacion.setIndividuos(nuevaPoblacion);

            // Actualizar el mejor global si encontramos uno mejor
            Individuo mejorActual = poblacion.getMejorIndividuo();
            if (mejorActual != null
                    && mejorActual.getFitness() > mejorGlobal.getFitness()) {
                mejorGlobal = copiarIndividuo(mejorActual);
            }

            // Mostrar progreso cada 10 generaciones
            if (generacion % 10 == 0) {
                System.out.println("Generación " + generacion
                    + " → Mejor fitness: "
                    + String.format("%.4f", mejorGlobal.getFitness()));
            }
        }

        System.out.println("=== GA finalizado ===");
        System.out.println("Mejor fitness final: "
            + String.format("%.4f", mejorGlobal.getFitness()));
        imprimirResumen(mejorGlobal);

        return mejorGlobal;
    }

    /**
     * Selección por torneo.
     * Elige TAMANIO_TORNEO individuos al azar y devuelve el mejor.
     * Así los mejores tienen más probabilidad de reproducirse.
     */
    private Individuo seleccionTorneo(List<Individuo> individuos) {
        Individuo mejor = null;
        for (int i = 0; i < TAMANIO_TORNEO; i++) {
            Individuo candidato = individuos.get(
                random.nextInt(individuos.size())
            );
            if (mejor == null
                    || candidato.getFitness() > mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    /**
     * Cruce de un punto entre dos padres.
     * Toma las primeras rutas del padre1 y el resto del padre2.
     * Si alguna ruta del padre2 ya existe en el hijo se mantiene
     * la del padre1 para evitar duplicados.
     */
    private Individuo cruzar(Individuo padre1,
                              Individuo padre2,
                              List<PlanVuelo> vuelos,
                              Map<String, Aeropuerto> mapaAeropuertos) {

        List<Ruta> rutasPadre1 = padre1.getRutas();
        List<Ruta> rutasPadre2 = padre2.getRutas();

        // Punto de cruce aleatorio
        int puntoCruce = random.nextInt(rutasPadre1.size());

        List<Ruta> rutasHijo = new ArrayList<>();

        // Tomar las primeras rutas del padre1
        for (int i = 0; i < puntoCruce; i++) {
            rutasHijo.add(copiarRuta(rutasPadre1.get(i)));
        }

        // Completar con rutas del padre2 para los envíos restantes
        // Cada envío debe aparecer exactamente una vez
        Set<String> enviosYaAsignados = rutasHijo.stream()
            .map(r -> r.getEnvio().getIdEnvio())
            .collect(Collectors.toSet());

        for (Ruta rutaPadre2 : rutasPadre2) {
            String idEnvio = rutaPadre2.getEnvio().getIdEnvio();
            if (!enviosYaAsignados.contains(idEnvio)) {
                rutasHijo.add(copiarRuta(rutaPadre2));
                enviosYaAsignados.add(idEnvio);
            }
        }

        return new Individuo(rutasHijo);
    }

    /**
     * Mutación de un individuo.
     * Elige una ruta al azar y la reemplaza por una nueva ruta
     * generada aleatoriamente para ese mismo envío.
     * Esto introduce variedad y evita convergencia prematura.
     */
    private void mutar(Individuo individuo,
                        List<PlanVuelo> vuelos,
                        Map<String, Aeropuerto> mapaAeropuertos) {

        List<Ruta> rutas = individuo.getRutas();
        if (rutas.isEmpty()) return;

        // Elegir una ruta al azar para mutar
        int indice = random.nextInt(rutas.size());
        Ruta rutaOriginal = rutas.get(indice);

        // Generar una nueva ruta para ese mismo envío
        // usando una nueva instancia de Población para aprovechar
        // la lógica de generación ya implementada
        Poblacion poblacionTemporal = new Poblacion(1);
        Map<String, Integer> capacidadesVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadesVuelos.put(claveVuelo(v), v.getCapacidad());
        }
        Map<String, Integer> capacidadesAlmacenes = new HashMap<>();
        for (Aeropuerto a : mapaAeropuertos.values()) {
            capacidadesAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }

        // Inicializar solo con el envío de la ruta a mutar
        poblacionTemporal.inicializar(
            List.of(rutaOriginal.getEnvio()),
            vuelos,
            mapaAeropuertos
        );

        // Reemplazar la ruta mutada si se generó una válida
        if (!poblacionTemporal.getIndividuos().isEmpty()) {
            Individuo individTemporal = poblacionTemporal
                .getIndividuos().get(0);
            if (!individTemporal.getRutas().isEmpty()) {
                rutas.set(indice, individTemporal.getRutas().get(0));
            }
        }
    }

    /**
     * Obtiene los mejores K individuos de la población (elitismo).
     * Estos pasan directamente a la siguiente generación sin cambios.
     */
    private List<Individuo> obtenerElite(List<Individuo> individuos,
                                          int k) {
        return individuos.stream()
            .sorted(Comparator.comparingDouble(
                Individuo::getFitness).reversed()
            )
            .limit(k)
            .map(this::copiarIndividuo)
            .collect(Collectors.toList());
    }

    /**
     * Crea una copia profunda de un individuo para evitar
     * que los cambios afecten al original.
     */
    private Individuo copiarIndividuo(Individuo original) {
        List<Ruta> rutasCopia = original.getRutas().stream()
            .map(this::copiarRuta)
            .collect(Collectors.toList());
        Individuo copia = new Individuo(rutasCopia);
        copia.setFitness(original.getFitness());
        return copia;
    }

    /**
     * Crea una copia de una ruta.
     */
    private Ruta copiarRuta(Ruta original) {
        Ruta copia = new Ruta();
        copia.setEnvio(original.getEnvio());
        copia.setVuelos(new ArrayList<>(original.getVuelos() != null
            ? original.getVuelos() : new ArrayList<>()));
        copia.setTiempoTotalMinutos(original.getTiempoTotalMinutos());
        copia.setEstado(original.getEstado());
        copia.setIndiceVueloActual(original.getIndiceVueloActual());
        return copia;
    }

    /**
     * Imprime un resumen del mejor individuo encontrado.
     */
    private void imprimirResumen(Individuo mejor) {
        long sinRuta = mejor.getRutas().stream()
            .filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA)
            .count();
        long planificadas = mejor.getRutas().stream()
            .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA)
            .count();

        System.out.println("Total envíos: " + mejor.getRutas().size());
        System.out.println("Planificados: " + planificadas);
        System.out.println("Sin ruta: " + sinRuta);
    }

    /**
     * Genera clave única para identificar un vuelo.
     */
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino()
             + "-" + vuelo.getHoraSalida();
    }
}