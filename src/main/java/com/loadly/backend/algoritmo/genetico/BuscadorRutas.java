package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;

import java.util.*;

/**
 * Motor de búsqueda de rutas basado en A* (A-Star).
 * Clase utilitaria compartida por Poblacion (inicialización) y AlgoritmoGenetico (mutación).
 *
 * Optimizaciones aplicadas:
 *  1. Índice local: si se recibe lista plana de vuelos, se indexa UNA SOLA VEZ por aeropuerto
 *     origen al inicio del método, en lugar de filtrar los 2866 vuelos en cada expansión de nodo.
 *  2. Puntero a padre: el NodoAStar ya no copia la lista de vuelos ni el Set de visitados
 *     en cada expansión. Solo guarda referencia al nodo anterior y el vuelo usado.
 *     La ruta se reconstruye remontando padres solo cuando se llega al destino.
 *  3. Detección de ciclos sin HashSet: se verifica remontando la cadena de padres,
 *     trivialmente rápido para rutas de 3-5 vuelos.
 */
public class BuscadorRutas {

    private static final int  TIEMPO_MINIMO_ESCALA  = 10;  // minutos
    private static final int  TIEMPO_RECOJO_DESTINO = 10;  // minutos
    private static final long MARGEN_BEST_G_MINUTOS = 120; // margen de exploración

    // =========================================================================
    // NODO INTERNO DEL A*
    // =========================================================================

    private static class NodoAStar implements Comparable<NodoAStar> {
        final String    aeropuertoCodigo;
        final NodoAStar padre;       // nodo anterior (null en el origen)
        final PlanVuelo vueloUsado;  // vuelo que llevó hasta aquí (null en el origen)
        final long      g;           // costo real acumulado en minutos
        final double    f;           // g + heurística + factor diversidad
        final String    horaLlegada; // hora de llegada a este nodo (null en el origen)

        NodoAStar(String aeropuertoCodigo, NodoAStar padre, PlanVuelo vueloUsado,
                  long g, double f, String horaLlegada) {
            this.aeropuertoCodigo = aeropuertoCodigo;
            this.padre            = padre;
            this.vueloUsado       = vueloUsado;
            this.g                = g;
            this.f                = f;
            this.horaLlegada      = horaLlegada;
        }

        @Override
        public int compareTo(NodoAStar otro) {
            return Double.compare(this.f, otro.f);
        }
    }

    // =========================================================================
    // MÉTODO PRINCIPAL
    // =========================================================================

    /**
     * Ejecuta el A* para encontrar la mejor ruta para un envío.
     *
     * @param envio               El envío a planificar.
     * @param vuelosDisponibles   Lista plana de vuelos (modo inicialización). Puede ser null.
     * @param mapaVuelosPorOrigen Índice de vuelos por aeropuerto (modo mutación). Puede ser null.
     * @param mapaAeropuertos     Mapa de aeropuertos por código.
     * @param capVuelos           Capacidades dinámicas de vuelos. Si null, no se valida capacidad.
     * @param capAlmacenes        Capacidades dinámicas de almacenes. Si null, no se valida capacidad.
     * @param random              Instancia de Random para factor de diversidad.
     * @param factorDiversidadMax Factor máximo de aleatoriedad (0.20 para init, 0.50 para mutación).
     * @return Ruta con estado PLANIFICADA, SIN_RUTA o INALCANZABLE.
     */
    public Ruta buscarRuta(
            Envio envio,
            List<PlanVuelo>              vuelosDisponibles,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto>      mapaAeropuertos,
            Map<String, Integer>         capVuelos,
            Map<String, Integer>         capAlmacenes,
            Random random,
            double factorDiversidadMax) {

        boolean validarCapacidad = (capVuelos != null && capAlmacenes != null);

        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setIndiceVueloActual(0);

        // Validar almacén origen si aplica
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

        long plazoMaximoMinutos = 48 * 60;
        if (aeropOrigen != null && aeropDestino != null) {
            if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                plazoMaximoMinutos = 24 * 60;
            }
        }
        long plazoDisponible = plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO;

        // ── OPTIMIZACIÓN 1: Índice construido UNA SOLA VEZ por llamada ──────
        // En modo inicialización la lista plana se indexa aquí, evitando filtrar
        // los 2866 vuelos en cada expansión de nodo del A*.
        final Map<String, List<PlanVuelo>> indice;
        if (mapaVuelosPorOrigen != null) {
            indice = mapaVuelosPorOrigen; // ya indexado, acceso O(1)
        } else {
            indice = new HashMap<>();
            for (PlanVuelo v : vuelosDisponibles) {
                indice.computeIfAbsent(v.getOrigen(), k -> new ArrayList<>()).add(v);
            }
        }

        // ── A* ───────────────────────────────────────────────────────────────
        PriorityQueue<NodoAStar> openSet = new PriorityQueue<>();
        Map<String, Long> bestG = new HashMap<>();

        // Nodo inicial: sin padre, sin vuelo usado
        openSet.add(new NodoAStar(envio.getAeropuertoOrigen(), null, null, 0, 0.0, null));
        bestG.put(envio.getAeropuertoOrigen(), 0L);

        while (!openSet.isEmpty()) {
            NodoAStar actual = openSet.poll();

            // ¿Llegamos al destino?
            if (actual.aeropuertoCodigo.equals(envio.getAeropuertoDestino())) {

                // ── OPTIMIZACIÓN 2: Reconstruir ruta remontando padres ───────
                // Evita copiar la lista de vuelos en cada expansión de nodo.
                List<PlanVuelo> vuelos = reconstruirRuta(actual);

                if (validarCapacidad) {
                    capAlmacenes.put(
                        envio.getAeropuertoOrigen(),
                        capAlmacenes.get(envio.getAeropuertoOrigen()) - envio.getCantidadMaletas()
                    );
                    for (PlanVuelo v : vuelos) {
                        String claveV = claveVuelo(v);
                        capVuelos.put(claveV, capVuelos.get(claveV) - envio.getCantidadMaletas());
                        capAlmacenes.put(
                            v.getDestino(),
                            capAlmacenes.get(v.getDestino()) - envio.getCantidadMaletas()
                        );
                    }
                }

                ruta.setVuelos(vuelos);
                ruta.setTiempoTotalMinutos(actual.g + TIEMPO_RECOJO_DESTINO);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }

            List<PlanVuelo> candidatos = indice.getOrDefault(
                    actual.aeropuertoCodigo, new ArrayList<>());

            for (PlanVuelo vuelo : candidatos) {
                if (vuelo.isCancelado()) continue;

                // ── OPTIMIZACIÓN 3: Detección de ciclos sin HashSet ─────────
                // Remontar la cadena de padres es O(profundidad), trivial para
                // rutas de 3-5 vuelos, y evita copiar un HashSet por nodo.
                if (yaVisitado(actual, vuelo.getDestino())) continue;

                if (validarCapacidad) {
                    if (capVuelos.getOrDefault(claveVuelo(vuelo), vuelo.getCapacidad()) < envio.getCantidadMaletas()) continue;
                    if (capAlmacenes.getOrDefault(vuelo.getDestino(), 0) < envio.getCantidadMaletas()) continue;
                }

                // Tiempo mínimo de escala (solo aplica si no es el primer vuelo)
                if (actual.horaLlegada != null) {
                    if (!cumpleTiempoEscala(actual.horaLlegada, vuelo.getHoraSalida())) continue;
                }

                long tiempoEspera = (actual.horaLlegada == null)
                        ? calcularTiempoEsperaOrigen(envio, vuelo.getHoraSalida())
                        : calcularTiempoEspera(actual.horaLlegada, vuelo.getHoraSalida());
                long duracionVuelo = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long newG = actual.g + tiempoEspera + duracionVuelo;

                if (newG > plazoDisponible) continue;

                long bestKnown = bestG.getOrDefault(vuelo.getDestino(), Long.MAX_VALUE);
                if (bestKnown == Long.MAX_VALUE || newG <= bestKnown + MARGEN_BEST_G_MINUTOS) {
                    if (newG < bestKnown) bestG.put(vuelo.getDestino(), newG);

                    Aeropuerto destAeropuerto = mapaAeropuertos.get(vuelo.getDestino());
                    double h = calcularHeuristicaGeografica(destAeropuerto, aeropDestino);
                    double factorDiversidad = 1.0 + (random.nextDouble() * factorDiversidadMax);
                    double newF = (newG + h) * factorDiversidad;

                    // Nuevo nodo con puntero al padre — sin copiar listas ni sets
                    openSet.add(new NodoAStar(
                            vuelo.getDestino(),
                            actual,
                            vuelo,
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
     * Reconstruye la lista de vuelos remontando la cadena de punteros a padre.
     */
    private List<PlanVuelo> reconstruirRuta(NodoAStar nodoDestino) {
        LinkedList<PlanVuelo> vuelos = new LinkedList<>();
        NodoAStar cursor = nodoDestino;
        while (cursor.vueloUsado != null) {
            vuelos.addFirst(cursor.vueloUsado);
            cursor = cursor.padre;
        }
        return new ArrayList<>(vuelos);
    }

    /**
     * Verifica si un aeropuerto ya fue visitado en la ruta actual
     * remontando la cadena de padres. Evita copiar HashSet por nodo.
     */
    private boolean yaVisitado(NodoAStar nodo, String codigoAeropuerto) {
        NodoAStar cursor = nodo;
        while (cursor != null) {
            if (cursor.aeropuertoCodigo.equals(codigoAeropuerto)) return true;
            cursor = cursor.padre;
        }
        return false;
    }

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
