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
    private static final int MAX_INTENTOS = 30;
    private static final int FALLBACK_ASTAR_UMBRAL = 10; // intentar A* tras N intentos fallidos

    // ---------- Parámetros de la fórmula ACO ----------
    private static final double ALFA = 1.0;
    private static final double BETA = 1.0; // Aumentado ligeramente para darle más peso al tiempo inicial

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
        final int RELAX_INTENTOS = 5;
        final int RELAX_ESCALAS_DELTA = 2;
        final int RELAX_TIEMPO_MIN_ESCALA = 5;
        final double RELAX_PLAZO_MULTIPLIER = 1.5;

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

                long duracion = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long tiempoTotal = tiempoAcumulado + espera + duracion;
                if (tiempoTotal > plazoDisponible) continue;

                candidatos.add(vuelo);
                esperas.add(espera);
                duraciones.add(duracion);
                factoresCap.add(calcularFactorCapacidad(
                    vuelo,
                    envio.getCantidadMaletas(),
                    capVuelosLocal,
                    capAlmacenesLocal));
                factoresHub.add(calcularFactorConexion(
                    vuelo,
                    mapaVuelosPorOrigen));
            }

            if (candidatos.isEmpty()) {
                return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
            }

            // Pasamos las listas de tiempos al selector por ruleta
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
            Map<String, Integer> capAlmacenesLocal) {

        String idVuelo = claveVuelo(vuelo);
        int capacidadVueloRestante = capVuelosLocal.getOrDefault(idVuelo, vuelo.getCapacidad()) - cantidadMaletas;
        int capacidadAlmacenRestante = capAlmacenesLocal.getOrDefault(vuelo.getDestino(), 0) - cantidadMaletas;

        double ratioVuelo = Math.min(1.0, Math.max(0.0, capacidadVueloRestante / (double) Math.max(1, cantidadMaletas)));
        double ratioAlmacen = Math.min(1.0, Math.max(0.0, capacidadAlmacenRestante / (double) Math.max(1, cantidadMaletas)));

        return 0.5 + (ratioVuelo * 0.25) + (ratioAlmacen * 0.25);
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