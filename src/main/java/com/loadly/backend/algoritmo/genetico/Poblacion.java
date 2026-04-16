package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;

@Data
public class Poblacion {

    private List<Individuo> individuos;
    private int tamanio;

    // Máximo de escalas permitidas por ruta
    private static final int MAX_ESCALAS = 3;

    // Tiempo mínimo de escala en minutos (parámetro del problema)
    private static final int TIEMPO_MINIMO_ESCALA = 10;

    // Tiempo de espera en destino final antes de ser recogida (parámetro)
    private static final int TIEMPO_RECOJO_DESTINO = 10;

    public Poblacion(int tamanio) {
        this.tamanio = tamanio;
        this.individuos = new ArrayList<>();
    }

    /**
     * Genera la población inicial con N individuos.
     * Cada individuo es un conjunto de rutas válidas para todos los envíos.
     */
    public void inicializar(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos, Map<String, Integer> capVuelos, Map<String, Integer> capAlmacenes) {
        for (int i = 0; i < tamanio; i++) {
            List<Ruta> rutas = generarRutasInteligentes(envios, vuelos, mapaAeropuertos, capVuelos, capAlmacenes);
            individuos.add(new Individuo(rutas));
        }
    }
    private List<Ruta> generarRutasInteligentes(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos, Map<String, Integer> capVuelos, Map<String, Integer> capAlmacenes) {

        List<Ruta> rutas = new ArrayList<>();
        Random random = new Random();

        // 💡 USAMOS LAS CAPACIDADES DINÁMICAS (Clonadas para que cada individuo juegue con las suyas)
        Map<String, Integer> capacidadesVuelos = new HashMap<>(capVuelos);
        Map<String, Integer> capacidadesAlmacenes = new HashMap<>(capAlmacenes);

        // 💡 NUEVO: Generar una ruta óptima con A* para cada envío
        for (Envio envio : envios) {
            Ruta ruta = buscarRutaAStar(
                envio, vuelos, mapaAeropuertos,
                capacidadesVuelos, capacidadesAlmacenes, random
            );
            rutas.add(ruta);
        }

        return rutas;
    }

    // =========================================================================
    // 🚀 NUEVO MOTOR DE BÚSQUEDA A* (A-STAR)
    // =========================================================================
    
    private Ruta buscarRutaAStar(
            Envio envio,
            List<PlanVuelo> todosLosVuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capacidadesVuelos,
            Map<String, Integer> capacidadesAlmacenes,
            Random random) {

        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setIndiceVueloActual(0);

        // 1. Validar almacén origen: Si está lleno desde el principio, es un Colapso de Espacio
        if (capacidadesAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0) < envio.getCantidadMaletas()) {
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
            ruta.setEstado(EstadoRuta.SIN_RUTA);
            return ruta; 
        }

        Aeropuerto aeropOrigen = mapaAeropuertos.get(envio.getAeropuertoOrigen());
        Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());

        long plazoMaximoMinutos = 48 * 60;
        if (aeropOrigen != null && aeropDestino != null) {
            if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                plazoMaximoMinutos = 24 * 60; 
            }
        }
        long plazoDisponible = plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO;

        // Clase Nodo interna para rastrear los estados en el A*
        class NodoAStar implements Comparable<NodoAStar> {
            String aeropuertoCodigo;
            int escalas;
            List<PlanVuelo> rutaAcumulada;
            long g; // Costo real (Minutos transcurridos)
            double f; // g + heurística + factorAleatorio
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
        Map<String, Long> bestG = new HashMap<>(); // Registro de los mejores tiempos hacia cada nodo

        // Iniciar en el aeropuerto de origen
        openSet.add(new NodoAStar(envio.getAeropuertoOrigen(), -1, new ArrayList<>(), 0, 0.0, null));
        bestG.put(envio.getAeropuertoOrigen(), 0L);

        while (!openSet.isEmpty()) {
            NodoAStar actual = openSet.poll();

            // 🎯 ¿Llegamos al destino?
            if (actual.aeropuertoCodigo.equals(envio.getAeropuertoDestino())) {
                // Descontar capacidades temporales (Solo cuando ya confirmamos la ruta completa)
                capacidadesAlmacenes.put(envio.getAeropuertoOrigen(), capacidadesAlmacenes.get(envio.getAeropuertoOrigen()) - envio.getCantidadMaletas());
                for (PlanVuelo v : actual.rutaAcumulada) {
                    String claveV = claveVuelo(v);
                    capacidadesVuelos.put(claveV, capacidadesVuelos.get(claveV) - envio.getCantidadMaletas());
                    capacidadesAlmacenes.put(v.getDestino(), capacidadesAlmacenes.get(v.getDestino()) - envio.getCantidadMaletas());
                }

                ruta.setVuelos(actual.rutaAcumulada);
                ruta.setTiempoTotalMinutos(actual.g + TIEMPO_RECOJO_DESTINO);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }

            // Límite de escalas físico del avión
            if (actual.escalas >= MAX_ESCALAS) continue;

            // Explorar conexiones desde el aeropuerto actual
            for (PlanVuelo vuelo : todosLosVuelos) {
                if (!vuelo.getOrigen().equals(actual.aeropuertoCodigo) || vuelo.isCancelado()) continue;

                // Restricción de Capacidades Dinámicas
                if (capacidadesVuelos.getOrDefault(claveVuelo(vuelo), vuelo.getCapacidad()) < envio.getCantidadMaletas()) continue;
                if (capacidadesAlmacenes.getOrDefault(vuelo.getDestino(), 0) < envio.getCantidadMaletas()) continue;

                // Restricción de Tiempo Mínimo de Escala
                if (!actual.rutaAcumulada.isEmpty()) {
                    if (!cumpleTiempoEscalaInterno(actual.horaLlegadaAnterior, vuelo.getHoraSalida())) continue;
                }

                // Matemáticas del tiempo real (Cálculo del G)
                long tiempoEspera = actual.rutaAcumulada.isEmpty() 
                        ? calcularTiempoEsperaOrigen(envio, vuelo.getHoraSalida())
                        : calcularTiempoEspera(actual.horaLlegadaAnterior, vuelo.getHoraSalida());
                long duracionVuelo = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long newG = actual.g + tiempoEspera + duracionVuelo;

                // Restricción: Si ya se pasó del plazo máximo de entrega, descartar camino
                if (newG > plazoDisponible) continue;

                // Aceptar la ruta si es la más rápida conocida o si está cerca (Margen para generar diversidad genética)
                long bestKnown = bestG.getOrDefault(vuelo.getDestino(), Long.MAX_VALUE);
                if (bestKnown == Long.MAX_VALUE || newG <= bestKnown + 60) {
                    if (newG < bestKnown) bestG.put(vuelo.getDestino(), newG);
                    
                    List<PlanVuelo> nuevaRuta = new ArrayList<>(actual.rutaAcumulada);
                    nuevaRuta.add(vuelo);
                    
                    // Cálculo Matemático de la Heurística (Fórmula de Haversine)
                    Aeropuerto destAeropuerto = mapaAeropuertos.get(vuelo.getDestino());
                    double h = calcularHeuristicaGeografica(destAeropuerto, aeropDestino);
                    
                    // Factor aleatorio leve para mutación (0% a 20%)
                    double factorDiversidad = 1.0 + (random.nextDouble() * 0.20); 
                    double newF = (newG + h) * factorDiversidad;

                    openSet.add(new NodoAStar(vuelo.getDestino(), actual.escalas + 1, nuevaRuta, newG, newF, vuelo.getHoraLlegada()));
                }
            }
        }

        // 💥 NUEVO: Si la cola de A* se vacía y no encontró ruta, es un Colapso Topológico
        ruta.setVuelos(new ArrayList<>());
        ruta.setTiempoTotalMinutos(0);
        ruta.setEstado(EstadoRuta.INALCANZABLE);
        return ruta;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES Y MATEMÁTICOS
    // =========================================================================

    /**
     * 💡 NUEVO: Calcula el tiempo estimado (Heurística) usando la distancia geográfica real.
     * Fórmula de Haversine para coordenadas terrestres.
     */
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
        
        // Asume un avión volando optimísticamente a 800 km/h (13.33 km por minuto)
        return distanciaKm / 13.33; 
    }

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalidaVuelo = convertirAMinutos(horaSalidaVuelo);
        if (minSalidaVuelo < minRegistro) minSalidaVuelo += 24 * 60;
        return minSalidaVuelo - minRegistro;
    }

    // 💡 NUEVO: Método sobrecargado para comparar directamente dos horas en String en el A*
    private boolean cumpleTiempoEscalaInterno(String horaLlegadaAnterior, String horaSalidaSiguiente) {
        int minLlegada = convertirAMinutos(horaLlegadaAnterior);
        int minSalida = convertirAMinutos(horaSalidaSiguiente);
        if (minSalida < minLlegada) minSalida += 24 * 60;
        return (minSalida - minLlegada) >= TIEMPO_MINIMO_ESCALA;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 24 * 60;
        return minSalida - minLlegada;
    }

    private int convertirAMinutos(String hora) {
        String[] partes = hora.split(":");
        return Integer.parseInt(partes[0]) * 60 + Integer.parseInt(partes[1]);
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLocal = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLocal = convertirAMinutos(vuelo.getHoraLlegada());
        int gmtOrigen = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtDestino = mapaAeropuertos.get(vuelo.getDestino()).getGmt();
        
        int minSalidaGMT = minSalidaLocal - (gmtOrigen * 60);
        int minLlegadaGMT = minLlegadaLocal - (gmtDestino * 60);
        
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 24 * 60;
        return minLlegadaGMT - minSalidaGMT;
    }

    public Individuo getMejorIndividuo() {
        return individuos.stream()
            .max(Comparator.comparingDouble(Individuo::getFitness))
            .orElse(null);
    }
}