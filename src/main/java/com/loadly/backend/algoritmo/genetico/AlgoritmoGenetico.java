package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // ── Parámetros de la Metaheurística ─────────────────────────────────
    private static final double PROB_CRUCE = 0.85; // Un poco más alto para explorar más
    private static final double PROB_MUTACION = 0.15; 
    private static final int TAMANIO_TORNEO = 3;
    private static final int ELITISMO = 2; // Mantenemos a los 2 mejores de cada generación

    /**
     * Ejecuta el Algoritmo Genético basándose en un presupuesto de tiempo real (Ta).
     * * @param envios           Lista de envíos que necesitan ser planificados.
     * @param vuelos           Catálogo completo de vuelos disponibles.
     * @param mapaAeropuertos  Mapa para acceso rápido a datos de aeropuertos (capacidad, GMT).
     * @param tamanoPoblacion  Número de individuos (soluciones) en cada generación.
     * @param tiempoLimiteMs   Tiempo máximo de ejecución en MILISEGUNDOS (El Ta real).
     * @return El mejor Individuo (solución) encontrado en el tiempo dado.
     */
    public Individuo ejecutar(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos, int tamanoPoblacion, long tiempoLimiteMs) {
        System.out.println("=== [GA] Iniciando Optimización ===");
        System.out.println("📦 Envíos a procesar: " + envios.size());
        System.out.println("⚙️  Configuración: Población=" + tamanoPoblacion + " | Ta Máximo=" + tiempoLimiteMs + "ms");

        // 1. Inicialización de la Población
        Poblacion poblacion = new Poblacion(tamanoPoblacion);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos);

        Fitness evaluadorFitness = new Fitness();
        evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);
        
        Individuo mejorIndividuo = obtenerMejorIndividuo(poblacion);
        
        Random random = new Random();
        long tiempoInicio = System.currentTimeMillis();
        int generacionesContadas = 0;

        // 2. Bucle Evolutivo: Se detiene cuando se agota el tiempo real (Ta)
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
            List<Individuo> nuevaGeneracion = new ArrayList<>();

            // --- ELITISMO ---
            List<Individuo> poblacionOrdenada = new ArrayList<>(poblacion.getIndividuos());
            poblacionOrdenada.sort((i1, i2) -> Double.compare(i2.getFitness(), i1.getFitness()));
            
            for (int i = 0; i < ELITISMO && i < tamanoPoblacion; i++) {
                nuevaGeneracion.add(copiarIndividuo(poblacionOrdenada.get(i)));
            }

            // --- REPRODUCCIÓN (Selección, Cruce y Mutación) ---
            while (nuevaGeneracion.size() < tamanoPoblacion) {
                // Selección
                Individuo padre1 = seleccionTorneo(poblacion, random);
                Individuo padre2 = seleccionTorneo(poblacion, random);

                // Cruce (Si no ocurre, se clona al padre 1)
                Individuo hijo;
                if (random.nextDouble() < PROB_CRUCE) {
                    hijo = cruzar(padre1, padre2, random);
                } else {
                    hijo = copiarIndividuo(padre1);
                }

                // Mutación
                mutar(hijo, vuelos, mapaAeropuertos, random);

                nuevaGeneracion.add(hijo);
            }

            // Actualizar población y evaluar
            poblacion.setIndividuos(nuevaGeneracion);
            evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);

            // Guardar el mejor histórico
            Individuo mejorActual = obtenerMejorIndividuo(poblacion);
            if (mejorActual.getFitness() > mejorIndividuo.getFitness()) {
                mejorIndividuo = copiarIndividuo(mejorActual);
            }

            generacionesContadas++;
            
            // Log de progreso cada 50 generaciones para no saturar la consola
            if (generacionesContadas % 50 == 0) {
                System.out.printf("Gen %d | Mejor Fitness: %.6f\n", generacionesContadas, mejorIndividuo.getFitness());
            }
        }

        System.out.println("=== [GA] Finalizado ===");
        System.out.println("⏱️  Tiempo real agotado. Generaciones procesadas: " + generacionesContadas);
        imprimirResumen(mejorIndividuo);

        return mejorIndividuo;
    }

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

    private Individuo cruzar(Individuo padre1, Individuo padre2, Random random) {
        List<Ruta> rutasHijo = new ArrayList<>();
        for (int i = 0; i < padre1.getRutas().size(); i++) {
            // Uniform Crossover: elige gen del padre 1 o padre 2 al azar
            if (random.nextBoolean()) {
                rutasHijo.add(copiarRuta(padre1.getRutas().get(i)));
            } else {
                rutasHijo.add(copiarRuta(padre2.getRutas().get(i)));
            }
        }
        return new Individuo(rutasHijo);
    }

    private void mutar(Individuo individuo, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        // Mapa temporal para no exceder capacidades de almacén durante la mutación
        Map<String, Integer> capacidadesMomento = new HashMap<>();
        mapaAeropuertos.forEach((k, v) -> capacidadesMomento.put(k, v.getCapacidad()));

        // Restar lo que ya ocupan las rutas actuales del individuo
        for (Ruta r : individuo.getRutas()) {
            if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Envio e = r.getEnvio();
                capacidadesMomento.merge(e.getAeropuertoOrigen(), -e.getCantidadMaletas(), Integer::sum);
                capacidadesMomento.merge(e.getAeropuertoDestino(), -e.getCantidadMaletas(), Integer::sum);
            }
        }

        for (int i = 0; i < individuo.getRutas().size(); i++) {
            if (random.nextDouble() < PROB_MUTACION) {
                Ruta rutaActual = individuo.getRutas().get(i);
                Envio envio = rutaActual.getEnvio();
                
                // Liberar capacidad de la ruta vieja antes de generar la nueva
                if (rutaActual.getEstado() == EstadoRuta.PLANIFICADA) {
                    capacidadesMomento.merge(envio.getAeropuertoOrigen(), envio.getCantidadMaletas(), Integer::sum);
                    capacidadesMomento.merge(envio.getAeropuertoDestino(), envio.getCantidadMaletas(), Integer::sum);
                }

                // Generar nueva ruta aleatoria
                Ruta rutaMutada = generarRutaAleatoriaValida(envio, vuelos, mapaAeropuertos, capacidadesMomento, random);
                
                // Ocupar capacidad de la nueva ruta
                if (rutaMutada.getEstado() == EstadoRuta.PLANIFICADA) {
                    capacidadesMomento.merge(envio.getAeropuertoOrigen(), -envio.getCantidadMaletas(), Integer::sum);
                    capacidadesMomento.merge(envio.getAeropuertoDestino(), -envio.getCantidadMaletas(), Integer::sum);
                }

                individuo.getRutas().set(i, rutaMutada); 
            }
        }
    }

    private Ruta generarRutaAleatoriaValida(Envio envio, List<PlanVuelo> todosLosVuelos, Map<String, Aeropuerto> mapaAeropuertos, Map<String, Integer> caps, Random random) {
        Ruta nuevaRuta = new Ruta();
        nuevaRuta.setEnvio(envio);
        nuevaRuta.setEstado(EstadoRuta.SIN_RUTA);
        
        String origen = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();

        // Si el almacén de origen está lleno, ni siquiera intentamos buscar vuelos
        if (caps.getOrDefault(origen, 0) < envio.getCantidadMaletas()) return nuevaRuta;

        // Intentar Vuelo Directo
        List<PlanVuelo> directos = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(origen) && v.getDestino().equals(destino) && !v.isCancelado())
                .collect(Collectors.toList());

        if (!directos.isEmpty()) {
            PlanVuelo v = directos.get(random.nextInt(directos.size()));
            nuevaRuta.setVuelos(Collections.singletonList(v));
            nuevaRuta.setEstado(EstadoRuta.PLANIFICADA);
            nuevaRuta.setTiempoTotalMinutos(calcularTiempoEsperaOrigen(envio, v.getHoraSalida()) + calcularDuracionMinutos(v, mapaAeropuertos) + 10);
            return nuevaRuta;
        }

        // Si no, intentar 1 escala (Simplificado para eficiencia)
        List<PlanVuelo> desdeOrigen = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(origen) && !v.isCancelado())
                .limit(20).collect(Collectors.toList()); // Limitar búsqueda para velocidad

        Collections.shuffle(desdeOrigen);
        for (PlanVuelo v1 : desdeOrigen) {
            Optional<PlanVuelo> v2Opt = todosLosVuelos.stream()
                    .filter(v -> v.getOrigen().equals(v1.getDestino()) && v.getDestino().equals(destino) && !v.isCancelado())
                    .findAny();
            
            if (v2Opt.isPresent()) {
                PlanVuelo v2 = v2Opt.get();
                nuevaRuta.setVuelos(Arrays.asList(v1, v2));
                nuevaRuta.setEstado(EstadoRuta.PLANIFICADA);
                long total = calcularTiempoEsperaOrigen(envio, v1.getHoraSalida()) + 
                             calcularDuracionMinutos(v1, mapaAeropuertos) + 
                             calcularTiempoEspera(v1.getHoraLlegada(), v2.getHoraSalida()) + 
                             calcularDuracionMinutos(v2, mapaAeropuertos) + 10;
                nuevaRuta.setTiempoTotalMinutos(total);
                return nuevaRuta;
            }
        }

        return nuevaRuta;
    }

    // ── Cálculos de Tiempo con GMT ──────────────────────────────────────

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalida = convertirAMinutos(horaSalidaVuelo);
        if (minSalida < minRegistro) minSalida += 1440; // 24h * 60m
        return minSalida - minRegistro;
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLoc = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());
        int gmtO = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtD = mapaAeropuertos.get(vuelo.getDestino()).getGmt();

        int minSalidaGMT = minSalidaLoc - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);

        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;
        return minLlegadaGMT - minSalidaGMT;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLleg = convertirAMinutos(horaLlegada);
        int minSal = convertirAMinutos(horaSalida);
        if (minSal < minLleg) minSal += 1440;
        return minSal - minLleg;
    }

    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
    
    // ── Utilidades de Población e Individuo ─────────────────────────────

    private Individuo obtenerMejorIndividuo(Poblacion poblacion) {
        return poblacion.getIndividuos().stream()
                .max(Comparator.comparingDouble(Individuo::getFitness))
                .orElse(poblacion.getIndividuos().get(0));
    }

    private Individuo copiarIndividuo(Individuo original) {
        List<Ruta> rutasCopia = original.getRutas().stream()
                .map(this::copiarRuta).collect(Collectors.toList());
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
        long planificadas = mejor.getRutas().stream().filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        System.out.println("✅ Planificados: " + planificadas + " | ❌ Sin ruta: " + (mejor.getRutas().size() - planificadas));
    }
}