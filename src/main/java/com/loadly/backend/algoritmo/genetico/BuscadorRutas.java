package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;

import java.util.*;

/**
 * Motor de búsqueda de rutas basado en A* (A-Star).
 * Clase utilitaria compartida por Poblacion (inicialización) y AlgoritmoGenetico (mutación).
 *
 * Diferencia entre los dos modos de uso:
 *  - Con capacidades (inicialización): valida que haya espacio real en vuelos y almacenes.
 *  - Sin capacidades (mutación): ignora restricciones de capacidad para fomentar diversidad
 *    genética; el fitness penalizará si la ruta resultante es infactible.
 */
public class BuscadorRutas {

    // Tiempo mínimo de escala entre la llegada de un vuelo y la salida del siguiente (minutos)
    private static final int TIEMPO_MINIMO_ESCALA = 10;

    // Tiempo de espera en destino final antes de ser recogida (minutos)
    private static final int TIEMPO_RECOJO_DESTINO = 10;

    // Margen de exploración sobre el mejor tiempo conocido hacia un nodo.
    // Permite que el A* no descarte rutas que son ligeramente más lentas pero
    // que podrían llevar a mejores soluciones globales. Valor único y consistente
    // para ambos modos de uso.
    private static final long MARGEN_BEST_G_MINUTOS = 120;

    /**
     * Nodo interno del A*.
     */
    private static class NodoAStar implements Comparable<NodoAStar> {
        String aeropuertoCodigo;
        List<PlanVuelo> rutaAcumulada;
        Set<String> aeropuertosVisitados; // Para detección de ciclos
        long g;       // Costo real acumulado en minutos
        double f;     // g + heurística + factor de diversidad
        String horaLlegadaAnterior;

        NodoAStar(String aeropuertoCodigo,
                  List<PlanVuelo> rutaAcumulada,
                  Set<String> aeropuertosVisitados,
                  long g, double f,
                  String horaLlegadaAnterior) {
            this.aeropuertoCodigo    = aeropuertoCodigo;
            this.rutaAcumulada       = rutaAcumulada;
            this.aeropuertosVisitados = aeropuertosVisitados;
            this.g                   = g;
            this.f                   = f;
            this.horaLlegadaAnterior = horaLlegadaAnterior;
        }

        @Override
        public int compareTo(NodoAStar otro) {
            return Double.compare(this.f, otro.f);
        }
    }

    /**
     * Ejecuta el A* para encontrar la mejor ruta para un envío.
     *
     * @param envio              El envío a planificar.
     * @param vuelosDisponibles  Lista de todos los vuelos (usada en inicialización).
     *                           Puede ser null si se provee mapaVuelosPorOrigen.
     * @param mapaVuelosPorOrigen Índice de vuelos por aeropuerto origen (más eficiente, usado en mutación).
     *                           Puede ser null si se provee vuelosDisponibles.
     * @param mapaAeropuertos    Mapa de aeropuertos por código.
     * @param capVuelos          Capacidades dinámicas de vuelos. Si es null, no se valida capacidad.
     * @param capAlmacenes       Capacidades dinámicas de almacenes. Si es null, no se valida capacidad.
     * @param random             Instancia de Random para factor de diversidad.
     * @param factorDiversidadMax Factor máximo de diversidad aleatoria (ej: 0.20 para init, 0.50 para mutación).
     * @return La Ruta encontrada (PLANIFICADA, SIN_RUTA o INALCANZABLE).
     */
    public Ruta buscarRuta(
            Envio envio,
            List<PlanVuelo> vuelosDisponibles,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            Random random,
            double factorDiversidadMax) {

        boolean validarCapacidad = (capVuelos != null && capAlmacenes != null);

        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setIndiceVueloActual(0);

        // Si validamos capacidad, verificamos el almacén origen primero
        if (validarCapacidad) {
            int espacioOrigen = capAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0);
            if (espacioOrigen < envio.getCantidadMaletas()) {
                ruta.setVuelos(new ArrayList<>());
                ruta.setTiempoTotalMinutos(0);
                ruta.setEstado(EstadoRuta.SIN_RUTA);
                return ruta;
            }
        }

        Aeropuerto aeropOrigen  = mapaAeropuertos.get(envio.getAeropuertoOrigen());
        Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());

        // Plazo máximo según continente
        long plazoMaximoMinutos = 48 * 60;
        if (aeropOrigen != null && aeropDestino != null) {
            if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                plazoMaximoMinutos = 24 * 60;
            }
        }
        long plazoDisponible = plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO;

        // Cola de prioridad del A*
        PriorityQueue<NodoAStar> openSet = new PriorityQueue<>();
        Map<String, Long> bestG = new HashMap<>();

        Set<String> visitadosInicio = new HashSet<>();
        visitadosInicio.add(envio.getAeropuertoOrigen());

        openSet.add(new NodoAStar(
                envio.getAeropuertoOrigen(),
                new ArrayList<>(),
                visitadosInicio,
                0, 0.0, null
        ));
        bestG.put(envio.getAeropuertoOrigen(), 0L);

        while (!openSet.isEmpty()) {
            NodoAStar actual = openSet.poll();

            // ¿Llegamos al destino?
            if (actual.aeropuertoCodigo.equals(envio.getAeropuertoDestino())) {
                // Si se valida capacidad, descuentos permanentes al confirmar la ruta
                if (validarCapacidad) {
                    capAlmacenes.put(
                        envio.getAeropuertoOrigen(),
                        capAlmacenes.get(envio.getAeropuertoOrigen()) - envio.getCantidadMaletas()
                    );
                    for (PlanVuelo v : actual.rutaAcumulada) {
                        String claveV = claveVuelo(v);
                        capVuelos.put(claveV, capVuelos.get(claveV) - envio.getCantidadMaletas());
                        capAlmacenes.put(
                            v.getDestino(),
                            capAlmacenes.get(v.getDestino()) - envio.getCantidadMaletas()
                        );
                    }
                }
                ruta.setVuelos(actual.rutaAcumulada);
                ruta.setTiempoTotalMinutos(actual.g + TIEMPO_RECOJO_DESTINO);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }

            // Obtener vuelos desde el aeropuerto actual
            List<PlanVuelo> candidatos;
            if (mapaVuelosPorOrigen != null) {
                candidatos = mapaVuelosPorOrigen.getOrDefault(actual.aeropuertoCodigo, new ArrayList<>());
            } else {
                candidatos = new ArrayList<>();
                for (PlanVuelo v : vuelosDisponibles) {
                    if (v.getOrigen().equals(actual.aeropuertoCodigo)) candidatos.add(v);
                }
            }

            for (PlanVuelo vuelo : candidatos) {
                if (vuelo.isCancelado()) continue;

                // Detección de ciclos: no visitar un aeropuerto ya recorrido en esta ruta
                if (actual.aeropuertosVisitados.contains(vuelo.getDestino())) continue;

                // Validar capacidades solo en modo inicialización
                if (validarCapacidad) {
                    if (capVuelos.getOrDefault(claveVuelo(vuelo), vuelo.getCapacidad()) < envio.getCantidadMaletas()) continue;
                    if (capAlmacenes.getOrDefault(vuelo.getDestino(), 0) < envio.getCantidadMaletas()) continue;
                }

                // Respetar tiempo mínimo de escala
                if (!actual.rutaAcumulada.isEmpty()) {
                    if (!cumpleTiempoEscala(actual.horaLlegadaAnterior, vuelo.getHoraSalida())) continue;
                }

                // Calcular costo g del siguiente nodo
                long tiempoEspera = actual.rutaAcumulada.isEmpty()
                        ? calcularTiempoEsperaOrigen(envio, vuelo.getHoraSalida())
                        : calcularTiempoEspera(actual.horaLlegadaAnterior, vuelo.getHoraSalida());
                long duracionVuelo = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long newG = actual.g + tiempoEspera + duracionVuelo;

                // Descartar si ya superó el plazo máximo
                if (newG > plazoDisponible) continue;

                // Aceptar si es el mejor tiempo conocido o está dentro del margen
                long bestKnown = bestG.getOrDefault(vuelo.getDestino(), Long.MAX_VALUE);
                if (bestKnown == Long.MAX_VALUE || newG <= bestKnown + MARGEN_BEST_G_MINUTOS) {
                    if (newG < bestKnown) bestG.put(vuelo.getDestino(), newG);

                    List<PlanVuelo> nuevaRuta = new ArrayList<>(actual.rutaAcumulada);
                    nuevaRuta.add(vuelo);

                    Set<String> nuevosVisitados = new HashSet<>(actual.aeropuertosVisitados);
                    nuevosVisitados.add(vuelo.getDestino());

                    // Heurística de Haversine hacia el destino final
                    Aeropuerto destAeropuerto = mapaAeropuertos.get(vuelo.getDestino());
                    double h = calcularHeuristicaGeografica(destAeropuerto, aeropDestino);

                    // Factor de diversidad aleatorio (0% a factorDiversidadMax%)
                    double factorDiversidad = 1.0 + (random.nextDouble() * factorDiversidadMax);
                    double newF = (newG + h) * factorDiversidad;

                    openSet.add(new NodoAStar(
                            vuelo.getDestino(),
                            nuevaRuta,
                            nuevosVisitados,
                            newG, newF,
                            vuelo.getHoraLlegada()
                    ));
                }
            }
        }

        // A* agotó todas las posibilidades sin llegar al destino
        ruta.setVuelos(new ArrayList<>());
        ruta.setTiempoTotalMinutos(0);
        ruta.setEstado(EstadoRuta.INALCANZABLE);
        return ruta;
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    /**
     * Heurística geográfica usando la fórmula de Haversine.
     * Estima el tiempo restante asumiendo vuelo directo a 800 km/h (13.33 km/min).
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
        return (RADIO_TIERRA_KM * c) / 13.33;
    }

    private boolean cumpleTiempoEscala(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida  = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 1440;
        return (minSalida - minLlegada) >= TIEMPO_MINIMO_ESCALA;
    }

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalida   = convertirAMinutos(horaSalidaVuelo);
        if (minSalida < minRegistro) minSalida += 1440;
        return minSalida - minRegistro;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida  = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 1440;
        return minSalida - minLlegada;
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLoc  = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());
        int gmtO = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtD = mapaAeropuertos.get(vuelo.getDestino()).getGmt();
        int minSalidaGMT  = minSalidaLoc  - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;
        return minLlegadaGMT - minSalidaGMT;
    }

    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    public String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }
}
