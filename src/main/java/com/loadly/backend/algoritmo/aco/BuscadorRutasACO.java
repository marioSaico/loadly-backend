package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.model.*;

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
    private static final int MAX_INTENTOS = 20;

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
                    feromenaGrafo, capVuelosLocal, capAlmacenesLocal, random);

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
            Random random) {

        String origen  = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();

        Aeropuerto aeroOrigen  = mapaAeropuertos.get(origen);
        Aeropuerto aeroDestino = mapaAeropuertos.get(destino);

        long plazoMaximoMinutos = 48 * 60L;
        if (aeroOrigen != null && aeroDestino != null
                && aeroOrigen.getContinente().equals(aeroDestino.getContinente())) {
            plazoMaximoMinutos = 24 * 60L;
        }
        long plazoDisponible = plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO;

        int minRegistroOrigen = envio.getHoraRegistro() * 60 + envio.getMinutoRegistro();

        List<PlanVuelo> vuelosElegidos   = new ArrayList<>();
        Set<String>     visitados        = new HashSet<>();
        visitados.add(origen);

        String aeropuertoActual  = origen;
        long   tiempoAcumulado   = 0;
        String horaLlegadaActual = null;

        for (int escala = 0; escala < MAX_ESCALAS; escala++) {

            if (aeropuertoActual.equals(destino)) break;

            List<PlanVuelo> todosVuelos = mapaVuelosPorOrigen
                    .getOrDefault(aeropuertoActual, Collections.emptyList());

            List<PlanVuelo> candidatos      = new ArrayList<>();
            List<Long>      esperas         = new ArrayList<>();
            List<Long>      duraciones      = new ArrayList<>();

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
                    if (!cumpleTiempoEscala(horaLlegadaActual, vuelo.getHoraSalida())) continue;
                    espera = calcularTiempoEspera(horaLlegadaActual, vuelo.getHoraSalida());
                }

                long duracion = calcularDuracionMinutos(vuelo, mapaAeropuertos);
                long tiempoTotal = tiempoAcumulado + espera + duracion;
                if (tiempoTotal > plazoDisponible) continue;

                candidatos.add(vuelo);
                esperas.add(espera);
                duraciones.add(duracion);
            }

            if (candidatos.isEmpty()) {
                return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
            }

            // Pasamos las listas de tiempos al selector por ruleta
            int idx = seleccionarPorRuleta(candidatos, esperas, duraciones, feromenaGrafo, random);

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

            pesos[i] = tau * eta;
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
}