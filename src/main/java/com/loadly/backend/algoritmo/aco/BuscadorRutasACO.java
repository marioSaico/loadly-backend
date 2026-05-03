package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.model.*;
import com.loadly.backend.algoritmo.genetico.BuscadorRutas;

import java.util.*;

/**
 * ============================================================
 * BuscadorRutasACO — Constructor de rutas para el algoritmo ACO
 * ============================================================
 */
public class BuscadorRutasACO {

    // ---------- Parámetros de tiempo ----------
    private static final int TIEMPO_MINIMO_ESCALA  = 10;
    private static final int TIEMPO_RECOJO_DESTINO = 10;
    private static final int MAX_ESCALAS           = 6;
    private static final int MAX_INTENTOS = 40;  // Compromiso: 30 original era poco, 50 era excesivo
    private static final int FALLBACK_ASTAR_UMBRAL = 5; // Invocar A* un poco más temprano

    // ---------- Parámetros de la fórmula ACO ----------
    private static final double ALFA = 1.0;
    private static final double BETA = 1.0; // Mantiene la heurística de tiempo, pero sin volverla demasiado suave
    // ---------- Optimización: Beam search width y cache ----------
    private static final int BEAM_WIDTH = 6; // limitar candidatos considerados por paso
    // ---------- Anti-colapso de almacenes (O(1) por candidato) ----------
    private static final double MAX_OCUPACION_DESTINO_DURA = 0.97; // corte duro
    private static final double UMBRAL_OCUPACION_ALTA = 0.85;      // penalización suave

    // =========================================================================
    //  MÉTODO PRINCIPAL CON REINTENTOS
    // =========================================================================

    public Ruta construirRuta(
            Envio envio,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto> mapaAeropuertos,
            FeromenaGrafo feromenaGrafo,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            Random random) {

        String origen = envio.getAeropuertoOrigen();

        int espacioOrigen = capAlmacenes.getOrDefault(origen, 0);
        if (espacioOrigen < envio.getCantidadMaletas()) {
            return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.SIN_RUTA);
        }

        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            Map<String, Integer> capVuelosLocal    = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesLocal = new HashMap<>(capAlmacenes);

            Ruta ruta = intentarConstruirRuta(
                envio, mapaVuelosPorOrigen, mapaAeropuertos,
                feromenaGrafo, capVuelosLocal, capAlmacenesLocal, random,
                MAX_ESCALAS, TIEMPO_MINIMO_ESCALA, 1.0);

            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                capVuelos.putAll(capVuelosLocal);
                capAlmacenes.putAll(capAlmacenesLocal);
                return ruta;
            }

            if (ruta.getEstado() == EstadoRuta.SIN_RUTA) {
                return ruta;
            }

            // Fallback A*: tras varios intentos estocásticos fallidos, invocar A* determinista
            if (intento >= (FALLBACK_ASTAR_UMBRAL - 1)) {
                BuscadorRutas buscador = new BuscadorRutas();
                Ruta rutaAStar = buscador.buscarRuta(
                        envio,
                        null, // usamos el índice existente (mapaVuelosPorOrigen)
                        mapaVuelosPorOrigen,
                        mapaAeropuertos,
                        capVuelosLocal,
                        capAlmacenesLocal,
                        random,
                        0.0 // sin diversidad para comportamiento determinista
                );

                if (rutaAStar.getEstado() == EstadoRuta.PLANIFICADA) {
                    capVuelos.putAll(capVuelosLocal);
                    capAlmacenes.putAll(capAlmacenesLocal);
                    return rutaAStar;
                }

                if (rutaAStar.getEstado() == EstadoRuta.SIN_RUTA) {
                    return rutaAStar;
                }
                // si A* también falla, seguimos con más intentos estocásticos
            }
        }

        // Si ni ACO ni A* funcionaron, intentos con parámetros relajados
        final int RELAX_INTENTOS = 7;  // Compromiso: 5 era poco, 10 era excesivo
        final int RELAX_ESCALAS_DELTA = 2;  // Original, suficiente
        final int RELAX_TIEMPO_MIN_ESCALA = 3;  // Reducido moderadamente de 5
        final double RELAX_PLAZO_MULTIPLIER = 1.75;  // Moderadamente aumentado de 1.5

        for (int intento = 0; intento < RELAX_INTENTOS; intento++) {
            Map<String, Integer> capVuelosLocal    = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesLocal = new HashMap<>(capAlmacenes);

            Ruta ruta = intentarConstruirRuta(
                    envio, mapaVuelosPorOrigen, mapaAeropuertos,
                    feromenaGrafo, capVuelosLocal, capAlmacenesLocal, random,
                    MAX_ESCALAS + RELAX_ESCALAS_DELTA,
                    RELAX_TIEMPO_MIN_ESCALA,
                    RELAX_PLAZO_MULTIPLIER);

            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                capVuelos.putAll(capVuelosLocal);
                capAlmacenes.putAll(capAlmacenesLocal);
                return ruta;
            }

            if (ruta.getEstado() == EstadoRuta.SIN_RUTA) {
                return ruta;
            }
        }

        return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
    }

    // =========================================================================
    //  CONSTRUCCIÓN DE UN INTENTO
    // =========================================================================

        private Ruta intentarConstruirRuta(
            Envio envio,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto> mapaAeropuertos,
            FeromenaGrafo feromenaGrafo,
            Map<String, Integer> capVuelosLocal,
            Map<String, Integer> capAlmacenesLocal,
            Random random,
            int maxEscalas,
            int tiempoMinimoEscala,
            double plazoMultiplier) {

        String origen  = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();

        Aeropuerto aeroOrigen  = mapaAeropuertos.get(origen);
        Aeropuerto aeroDestino = mapaAeropuertos.get(destino);

        long plazoMaximoMinutos = 48 * 60L;
        if (aeroOrigen != null && aeroDestino != null
                && aeroOrigen.getContinente().equals(aeroDestino.getContinente())) {
            plazoMaximoMinutos = 24 * 60L;
        }
        long plazoDisponible = (long) ((plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO) * plazoMultiplier);

        int minRegistroOrigen = envio.getHoraRegistro() * 60 + envio.getMinutoRegistro();

        List<PlanVuelo> vuelosElegidos   = new ArrayList<>();
        Set<String>     visitados        = new HashSet<>();
        visitados.add(origen);

        String aeropuertoActual  = origen;
        long   tiempoAcumulado   = 0;
        String horaLlegadaActual = null;

        // Cache local de duraciones por clave de vuelo para evitar recalcular GMT y bucles
        Map<String, Long> duracionCache = new HashMap<>();

        for (int escala = 0; escala < maxEscalas; escala++) {

            if (aeropuertoActual.equals(destino)) break;

            List<PlanVuelo> todosVuelos = mapaVuelosPorOrigen
                    .getOrDefault(aeropuertoActual, Collections.emptyList());

            List<PlanVuelo> candidatos      = new ArrayList<>();
            List<Long>      esperas         = new ArrayList<>();
            List<Long>      duraciones      = new ArrayList<>();
            List<Double>    factoresCap     = new ArrayList<>();
            List<Double>    factoresHub     = new ArrayList<>();

            for (PlanVuelo vuelo : todosVuelos) {
                if (vuelo.isCancelado()) continue;

                if (visitados.contains(vuelo.getDestino())
                        && !vuelo.getDestino().equals(destino)) {
                    continue;
                }

                if (capAlmacenesLocal.getOrDefault(vuelo.getDestino(), 0)
                        < envio.getCantidadMaletas()) {
                    continue;
                }

                double ocupacionDestinoProyectada = ocupacionDestinoProyectada(
                        vuelo,
                        envio.getCantidadMaletas(),
                        capAlmacenesLocal,
                        mapaAeropuertos);
                // Evita decisiones que dejan el destino prácticamente colapsado.
                if (ocupacionDestinoProyectada >= MAX_OCUPACION_DESTINO_DURA) {
                    continue;
                }

                String idVuelo = claveVuelo(vuelo);
                if (capVuelosLocal.getOrDefault(idVuelo, vuelo.getCapacidad())
                        < envio.getCantidadMaletas()) {
                    continue;
                }

                long espera;
                if (horaLlegadaActual == null) {
                    espera = calcularTiempoEsperaOrigen(minRegistroOrigen, vuelo.getHoraSalida());
                } else {
                    if (!cumpleTiempoEscala(horaLlegadaActual, vuelo.getHoraSalida(), tiempoMinimoEscala)) continue;
                    espera = calcularTiempoEspera(horaLlegadaActual, vuelo.getHoraSalida());
                }

                String claveDur = claveVuelo(vuelo);
                long duracion = duracionCache.computeIfAbsent(claveDur, k -> calcularDuracionMinutos(vuelo, mapaAeropuertos));
                long tiempoTotal = tiempoAcumulado + espera + duracion;
                if (tiempoTotal > plazoDisponible) continue;

                candidatos.add(vuelo);
                esperas.add(espera);
                duraciones.add(duracion);
                factoresCap.add(calcularFactorCapacidad(
                    vuelo,
                    envio.getCantidadMaletas(),
                    capVuelosLocal,
                    capAlmacenesLocal,
                    mapaAeropuertos));
                factoresHub.add(calcularFactorConexion(
                    vuelo,
                    mapaVuelosPorOrigen));
            }

                if (candidatos.isEmpty()) {
                return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
                }

                // OPTIMIZACIÓN: aplicar poda tipo beam para reducir candidatos
                int beam = Math.min(BEAM_WIDTH, candidatos.size());
                if (candidatos.size() > beam) {
                // calcular score heurístico simple para ordenar
                double[] scores = new double[candidatos.size()];
                for (int i = 0; i < candidatos.size(); i++) {
                    double tau = Math.pow(feromenaGrafo.getFeromona(candidatos.get(i)), ALFA);
                    long tiempoPaso = esperas.get(i) + duraciones.get(i);
                    double eta = Math.pow(1.0 / (tiempoPaso + 1.0), BETA);
                    scores[i] = tau * eta * factoresCap.get(i) * factoresHub.get(i);
                }
                Integer[] idxs = new Integer[candidatos.size()];
                for (int i = 0; i < idxs.length; i++) idxs[i] = i;
                Arrays.sort(idxs, (a, b) -> Double.compare(scores[b], scores[a]));

                List<PlanVuelo> candidatosBeam = new ArrayList<>(beam);
                List<Long> esperasBeam = new ArrayList<>(beam);
                List<Long> duracionesBeam = new ArrayList<>(beam);
                List<Double> factoresCapBeam = new ArrayList<>(beam);
                List<Double> factoresHubBeam = new ArrayList<>(beam);

                for (int j = 0; j < beam; j++) {
                    int i = idxs[j];
                    candidatosBeam.add(candidatos.get(i));
                    esperasBeam.add(esperas.get(i));
                    duracionesBeam.add(duraciones.get(i));
                    factoresCapBeam.add(factoresCap.get(i));
                    factoresHubBeam.add(factoresHub.get(i));
                }

                int idx = seleccionarPorRuleta(
                    candidatosBeam,
                    esperasBeam,
                    duracionesBeam,
                    factoresCapBeam,
                    factoresHubBeam,
                    feromenaGrafo,
                    random);

                PlanVuelo elegido = candidatosBeam.get(idx);
                long espera = esperasBeam.get(idx);
                long duracion = duracionesBeam.get(idx);

                String idVuelo = claveVuelo(elegido);
                capVuelosLocal.put(idVuelo,
                    capVuelosLocal.getOrDefault(idVuelo, elegido.getCapacidad())
                    - envio.getCantidadMaletas());
                capAlmacenesLocal.put(elegido.getDestino(),
                    capAlmacenesLocal.getOrDefault(elegido.getDestino(), 0)
                    - envio.getCantidadMaletas());

                tiempoAcumulado  += espera + duracion;
                horaLlegadaActual = elegido.getHoraLlegada();
                aeropuertoActual  = elegido.getDestino();
                visitados.add(elegido.getDestino());
                vuelosElegidos.add(elegido);
                continue;
                }

                // Si no hubo poda, usar selección completa por ruleta
                int idx = seleccionarPorRuleta(
                    candidatos,
                    esperas,
                    duraciones,
                    factoresCap,
                    factoresHub,
                    feromenaGrafo,
                    random);

                PlanVuelo elegido  = candidatos.get(idx);
                long      espera   = esperas.get(idx);
                long      duracion = duraciones.get(idx);

                String idVuelo = claveVuelo(elegido);
                capVuelosLocal.put(idVuelo,
                    capVuelosLocal.getOrDefault(idVuelo, elegido.getCapacidad())
                    - envio.getCantidadMaletas());
                capAlmacenesLocal.put(elegido.getDestino(),
                    capAlmacenesLocal.getOrDefault(elegido.getDestino(), 0)
                    - envio.getCantidadMaletas());

                tiempoAcumulado  += espera + duracion;
                horaLlegadaActual = elegido.getHoraLlegada();
                aeropuertoActual  = elegido.getDestino();
                visitados.add(elegido.getDestino());
                vuelosElegidos.add(elegido);
        }

        if (aeropuertoActual.equals(destino)) {
            capAlmacenesLocal.put(origen,
                    capAlmacenesLocal.getOrDefault(origen, 0) - envio.getCantidadMaletas());
            long tiempoFinal = tiempoAcumulado + TIEMPO_RECOJO_DESTINO;
            return crearRuta(envio, vuelosElegidos, tiempoFinal, EstadoRuta.PLANIFICADA);
        }

        return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
    }

    private Ruta crearRuta(Envio envio, List<PlanVuelo> vuelos,
                            long tiempoTotal, EstadoRuta estado) {
        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setVuelos(vuelos);
        ruta.setTiempoTotalMinutos(tiempoTotal);
        ruta.setEstado(estado);
        ruta.setIndiceVueloActual(0);
        return ruta;
    }

    /**
     * Selección por ruleta actualizada.
     * Heurística (eta): Inversamente proporcional al tiempo total (espera + duración) del siguiente paso.
     */
    private int seleccionarPorRuleta(
            List<PlanVuelo> candidatos,
            List<Long> esperas,
            List<Long> duraciones,
            List<Double> factoresCap,
            List<Double> factoresHub,
            FeromenaGrafo feromenaGrafo,
            Random random) {

        double[] pesos = new double[candidatos.size()];
        double   suma  = 0.0;
        double   mejorPeso = -1.0;

        for (int i = 0; i < candidatos.size(); i++) {
            PlanVuelo v = candidatos.get(i);
            
            double tau = Math.pow(feromenaGrafo.getFeromona(v), ALFA);

            // NUEVA HEURÍSTICA: Basada en el tiempo (igual que A*)
            long tiempoPaso = esperas.get(i) + duraciones.get(i);
            // Evitamos división por cero sumando 1
            double eta = Math.pow(1.0 / (tiempoPaso + 1.0), BETA); 
            double factorCap = factoresCap.get(i);
            double factorHub = factoresHub.get(i);

            pesos[i] = tau * eta * factorCap * factorHub;
            suma     += pesos[i];

            if (pesos[i] > mejorPeso) {
                mejorPeso = pesos[i];
            }
        }

        // Umbral más alto: menos selección determinista y más exploración controlada
        if (candidatos.size() <= 2 || (suma > 0.0 && (mejorPeso / suma) >= 0.92)) {
            for (int i = 0; i < pesos.length; i++) {
                if (pesos[i] == mejorPeso) {
                    return i;
                }
            }
        }

        if (suma <= 0.0) {
            return random.nextInt(candidatos.size());
        }

        double ruleta = random.nextDouble() * suma;
        double acum   = 0.0;
        for (int i = 0; i < pesos.length; i++) {
            acum += pesos[i];
            if (acum >= ruleta) return i;
        }
        return candidatos.size() - 1;
    }

    private long calcularTiempoEsperaOrigen(int minRegistroOrigen, String horaSalida) {
        int minSalida = convertirAMinutos(horaSalida);
        if (minSalida < minRegistroOrigen) minSalida += 1440;
        return minSalida - minRegistroOrigen;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida  = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 1440;
        return minSalida - minLlegada;
    }

    private boolean cumpleTiempoEscala(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida  = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 1440;
        return (minSalida - minLlegada) >= TIEMPO_MINIMO_ESCALA;
    }

    private boolean cumpleTiempoEscala(String horaLlegada, String horaSalida, int minEscala) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida  = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) minSalida += 1440;
        return (minSalida - minLlegada) >= minEscala;
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo,
                                          Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLoc  = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());

        Aeropuerto aeroO = mapaAeropuertos.get(vuelo.getOrigen());
        Aeropuerto aeroD = mapaAeropuertos.get(vuelo.getDestino());
        int gmtO = (aeroO != null) ? aeroO.getGmt() : 0;
        int gmtD = (aeroD != null) ? aeroD.getGmt() : 0;

        int minSalidaGMT  = minSalidaLoc  - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;

        return minLlegadaGMT - minSalidaGMT;
    }

    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }

    private double calcularFactorCapacidad(
            PlanVuelo vuelo,
            int cantidadMaletas,
            Map<String, Integer> capVuelosLocal,
            Map<String, Integer> capAlmacenesLocal,
            Map<String, Aeropuerto> mapaAeropuertos) {

        String idVuelo = claveVuelo(vuelo);
        int capacidadVueloRestante = capVuelosLocal.getOrDefault(idVuelo, vuelo.getCapacidad()) - cantidadMaletas;
        int capacidadAlmacenRestante = capAlmacenesLocal.getOrDefault(vuelo.getDestino(), 0) - cantidadMaletas;

        double ratioVuelo = Math.min(1.0, Math.max(0.0, capacidadVueloRestante / (double) Math.max(1, cantidadMaletas)));
        double ratioAlmacen = Math.min(1.0, Math.max(0.0, capacidadAlmacenRestante / (double) Math.max(1, cantidadMaletas)));

        double base = 0.5 + (ratioVuelo * 0.25) + (ratioAlmacen * 0.25);

        // Penalización continua para desincentivar destinos casi saturados sin bloquear todo.
        double ocupacion = ocupacionDestinoProyectada(vuelo, cantidadMaletas, capAlmacenesLocal, mapaAeropuertos);
        if (ocupacion <= UMBRAL_OCUPACION_ALTA) {
            return base;
        }

        double exceso = (ocupacion - UMBRAL_OCUPACION_ALTA) / Math.max(1e-9, 1.0 - UMBRAL_OCUPACION_ALTA);
        double factorRiesgo = Math.max(0.35, 1.0 - exceso);
        return base * factorRiesgo;
    }

    private double ocupacionDestinoProyectada(
            PlanVuelo vuelo,
            int cantidadMaletas,
            Map<String, Integer> capAlmacenesLocal,
            Map<String, Aeropuerto> mapaAeropuertos) {

        Aeropuerto destino = mapaAeropuertos.get(vuelo.getDestino());
        int capacidadTotal = (destino != null && destino.getCapacidad() > 0) ? destino.getCapacidad() : Integer.MAX_VALUE;

        int restanteActual = capAlmacenesLocal.getOrDefault(vuelo.getDestino(), capacidadTotal);
        int restantePost = restanteActual - cantidadMaletas;

        if (capacidadTotal <= 0 || capacidadTotal == Integer.MAX_VALUE) {
            return 0.0;
        }

        double ocupacion = 1.0 - (restantePost / (double) capacidadTotal);
        return Math.max(0.0, Math.min(1.5, ocupacion));
    }

    private double calcularFactorConexion(
            PlanVuelo vuelo,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen) {

        int salidasDesdeDestino = mapaVuelosPorOrigen
                .getOrDefault(vuelo.getDestino(), Collections.emptyList())
                .size();

        return 0.75 + Math.min(1.25, salidasDesdeDestino / 4.0);
    }
}