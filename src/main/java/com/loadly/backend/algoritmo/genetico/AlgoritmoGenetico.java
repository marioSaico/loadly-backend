package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // -- Parametros de la Metaheuristica --
    private static final double PROB_CRUCE = 0.85; 
    private static final double PROB_MUTACION = 0.15; 
    private static final int TAMANIO_TORNEO = 3;
    private static final int ELITISMO = 2; 
    private static final int MAX_ESCALAS = 3; 

    public Individuo ejecutar(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos,  Map<String, List<PlanVuelo>> mapaVuelosPorOrigen, Map<String, Integer> capVuelos, Map<String, Integer> capAlmacenes, int tamanoPoblacion, long tiempoLimiteMs) {

        System.out.println("=== GA - Iniciando Optimizacion ===");
        System.out.println("    Envios a procesar: " + envios.size());

        // 1. Inicializacion de la Poblacion (Usando nuestra clase estricta)
        Poblacion poblacion = new Poblacion(tamanoPoblacion);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos, capVuelos, capAlmacenes); 

        Fitness evaluadorFitness = new Fitness();
        evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, capVuelos);
        
        Individuo mejorIndividuo = obtenerMejorIndividuo(poblacion);
        
        Random random = new Random();
        long tiempoInicio = System.currentTimeMillis();
        int generacionesContadas = 0;

        // 2. Bucle Evolutivo
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
            List<Individuo> nuevaGeneracion = new ArrayList<>();

            // --- ELITISMO ---
            List<Individuo> poblacionOrdenada = new ArrayList<>(poblacion.getIndividuos());
            poblacionOrdenada.sort((i1, i2) -> Double.compare(i2.getFitness(), i1.getFitness()));
            
            for (int i = 0; i < ELITISMO && i < tamanoPoblacion; i++) {
                nuevaGeneracion.add(copiarIndividuo(poblacionOrdenada.get(i)));
            }

            // --- REPRODUCCION ---
            while (nuevaGeneracion.size() < tamanoPoblacion) {
                Individuo padre1 = seleccionTorneo(poblacion, random);
                Individuo padre2 = seleccionTorneo(poblacion, random);

                Individuo hijo;
                if (random.nextDouble() < PROB_CRUCE) {
                    hijo = cruzar(padre1, padre2, random);
                } else {
                    hijo = copiarIndividuo(padre1);
                }

                mutarOptimizado(hijo, mapaVuelosPorOrigen, mapaAeropuertos, random);
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

        System.out.println("=== GA - Finalizado ===");
        System.out.println("    Generaciones procesadas: " + generacionesContadas);
        imprimirResumen(mejorIndividuo);

        return mejorIndividuo;
    }

    // --- LÓGICA DE MUTACIÓN Y BÚSQUEDA ---
    
    private void mutarOptimizado(Individuo individuo, Map<String, List<PlanVuelo>> mapaVuelosPorOrigen, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        for (int i = 0; i < individuo.getRutas().size(); i++) {
            if (random.nextDouble() < PROB_MUTACION) {
                Envio envio = individuo.getRutas().get(i).getEnvio();
                // 💡 NUEVO: En vez de un camino aleatorio ciego, mutamos usando el motor A* relajado
                individuo.getRutas().set(i, buscarRutaAStarMutacion(envio, mapaVuelosPorOrigen, mapaAeropuertos, random));
            }
        }
    }

    // ❌ ELIMINADO: generarRutaMultiEscala y encontrarCaminoAleatorio (Eran DFS ciegas y lentas)
    // 💡 NUEVO: Motor A* exclusivo para mutación (Ignora capacidades para fomentar diversidad, el Fitness penalizará si se satura)
    private Ruta buscarRutaAStarMutacion(Envio envio, Map<String, List<PlanVuelo>> mapaVuelosPorOrigen, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setIndiceVueloActual(0);

        Aeropuerto aeropOrigen = mapaAeropuertos.get(envio.getAeropuertoOrigen());
        Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());

        long plazoMaximoMinutos = 48 * 60;
        if (aeropOrigen != null && aeropDestino != null) {
            if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) plazoMaximoMinutos = 24 * 60; 
        }
        long plazoDisponible = plazoMaximoMinutos - 10; // -10 por recojo

        class NodoAStar implements Comparable<NodoAStar> {
            String aeropuertoCodigo;
            int escalas;
            List<PlanVuelo> rutaAcumulada;
            long g; 
            double f; 
            String horaLlegadaAnterior;

            public NodoAStar(String aeropuertoCodigo, int escalas, List<PlanVuelo> rutaAcumulada, long g, double f, String horaLlegadaAnterior) {
                this.aeropuertoCodigo = aeropuertoCodigo;
                this.escalas = escalas;
                this.rutaAcumulada = rutaAcumulada;
                this.g = g;
                this.f = f;
                this.horaLlegadaAnterior = horaLlegadaAnterior;
            }

            @Override
            public int compareTo(NodoAStar otro) {
                return Double.compare(this.f, otro.f);
            }
        }

        PriorityQueue<NodoAStar> openSet = new PriorityQueue<>();
        Map<String, Long> bestG = new HashMap<>();

        openSet.add(new NodoAStar(envio.getAeropuertoOrigen(), -1, new ArrayList<>(), 0, 0.0, null));
        bestG.put(envio.getAeropuertoOrigen(), 0L);

        while (!openSet.isEmpty()) {
            NodoAStar actual = openSet.poll();

            if (actual.aeropuertoCodigo.equals(envio.getAeropuertoDestino())) {
                ruta.setVuelos(actual.rutaAcumulada);
                ruta.setTiempoTotalMinutos(actual.g + 10);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }

            if (actual.escalas >= MAX_ESCALAS) continue;

            // 💡 OPTIMIZACIÓN: Usar el HashMap de vuelos por origen en lugar de recorrer todos los vuelos
            List<PlanVuelo> vuelosDesdeAqui = mapaVuelosPorOrigen.getOrDefault(actual.aeropuertoCodigo, new ArrayList<>());
            
            for (PlanVuelo vuelo : vuelosDesdeAqui) {
                if (vuelo.isCancelado()) continue;

                if (!actual.rutaAcumulada.isEmpty()) {
                    if (!cumpleTiempoEscalaInterno(actual.horaLlegadaAnterior, vuelo.getHoraSalida())) continue;
                }

                long tiempoEspera = actual.rutaAcumulada.isEmpty() 
                        ? calcularTiempoEsperaOrigen(envio, vuelo.getHoraSalida())
                        : calcularTiempoEspera(actual.horaLlegadaAnterior, vuelo.getHoraSalida());
                long duracionVuelo = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long newG = actual.g + tiempoEspera + duracionVuelo;

                if (newG > plazoDisponible) continue;

                long bestKnown = bestG.getOrDefault(vuelo.getDestino(), Long.MAX_VALUE);

                if (bestKnown == Long.MAX_VALUE || newG <= bestKnown + 300) { 
                    if (newG < bestKnown) bestG.put(vuelo.getDestino(), newG);
                    
                    List<PlanVuelo> nuevaRuta = new ArrayList<>(actual.rutaAcumulada);
                    nuevaRuta.add(vuelo);
                    
                    Aeropuerto destAeropuerto = mapaAeropuertos.get(vuelo.getDestino());
                    double h = calcularHeuristicaGeografica(destAeropuerto, aeropDestino);
                    
                    // Alta mutación de factor diversidad (0% a 50%)
                    double factorDiversidad = 1.0 + (random.nextDouble() * 0.50); 
                    double newF = (newG + h) * factorDiversidad;

                    openSet.add(new NodoAStar(vuelo.getDestino(), actual.escalas + 1, nuevaRuta, newG, newF, vuelo.getHoraLlegada()));
                }
            }
        }

        // Si muta y no encuentra camino, lo marca como inalcanzable
        ruta.setVuelos(new ArrayList<>());
        ruta.setTiempoTotalMinutos(0);
        ruta.setEstado(EstadoRuta.INALCANZABLE);
        return ruta;
    }

    // --- MÉTODOS DE APOYO DE TIEMPOS Y GEOGRAFÍA ---

    // 💡 NUEVO: Heurística Haversine para que la mutación sepa hacia dónde volar
    private double calcularHeuristicaGeografica(Aeropuerto origen, Aeropuerto destino) {
        if (origen == null || destino == null) return 0.0;
        final int RADIO_TIERRA_KM = 6371; 
        
        double latDist = Math.toRadians(destino.getLatitud() - origen.getLatitud());
        double lonDist = Math.toRadians(destino.getLongitud() - origen.getLongitud());
        
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2)
                 + Math.cos(Math.toRadians(origen.getLatitud())) 
                 * Math.cos(Math.toRadians(destino.getLatitud()))
                 * Math.sin(lonDist / 2) * Math.sin(lonDist / 2);
                 
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanciaKm = RADIO_TIERRA_KM * c;
        
        return distanciaKm / 13.33; 
    }

    private boolean cumpleTiempoEscalaInterno(String horaLlegadaAnterior, String horaSalidaSiguiente) {
        int minLlegada = convertirAMinutos(horaLlegadaAnterior);
        int minSalida = convertirAMinutos(horaSalidaSiguiente);
        if (minSalida < minLlegada) minSalida += 1440;
        return (minSalida - minLlegada) >= 10;
    }

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalida = convertirAMinutos(horaSalidaVuelo);
        if (minSalida < minRegistro) minSalida += 1440; 
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

    // --- OPERADORES GENÉTICOS ---

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
            rutasHijo.add(copiarRuta(random.nextBoolean() ? padre1.getRutas().get(i) : padre2.getRutas().get(i)));
        }
        return new Individuo(rutasHijo);
    }
    
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
        System.out.println("    Planificados: " + planificadas + " |    Sin ruta: " + (mejor.getRutas().size() - planificadas));
    }
}