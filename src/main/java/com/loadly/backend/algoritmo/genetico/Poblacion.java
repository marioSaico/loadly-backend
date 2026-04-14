package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;

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
    public void inicializar(List<Envio> envios,
                            List<PlanVuelo> vuelos,
                            Map<String, Aeropuerto> mapaAeropuertos) {
        for (int i = 0; i < tamanio; i++) {
            List<Ruta> rutas = generarRutasAleatorias(
                envios, vuelos, mapaAeropuertos
            );
            individuos.add(new Individuo(rutas));
        }
    }

    private List<Ruta> generarRutasAleatorias(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos) {

        List<Ruta> rutas = new ArrayList<>();
        Random random = new Random();

        // Inicializar capacidades disponibles de vuelos
        Map<String, Integer> capacidadesVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadesVuelos.put(claveVuelo(v), v.getCapacidad());
        }

        // Inicializar capacidades disponibles de almacenes
        Map<String, Integer> capacidadesAlmacenes = new HashMap<>();
        for (Aeropuerto a : mapaAeropuertos.values()) {
            capacidadesAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }

        // Generar una ruta válida para cada envío
        for (Envio envio : envios) {
            Ruta ruta = generarRutaAleatoria(
                envio, vuelos, mapaAeropuertos,
                capacidadesVuelos, capacidadesAlmacenes, random
            );
            rutas.add(ruta);
        }

        return rutas;
    }

    private Ruta generarRutaAleatoria(
            Envio envio,
            List<PlanVuelo> todosLosVuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capacidadesVuelos,
            Map<String, Integer> capacidadesAlmacenes,
            Random random) {

        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setEstado(EstadoRuta.SIN_RUTA);
        ruta.setIndiceVueloActual(0);

        // Verificar si el almacén de origen tiene capacidad al registrar el envío
        if (capacidadesAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0) < envio.getCantidadMaletas()) {
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
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

        // ── INTENTO 1: Vuelo directo (100% DE PRIORIDAD) ─────────────────
        List<PlanVuelo> vuelosDirectos = todosLosVuelos.stream()
            .filter(v ->
                v.getOrigen().equals(envio.getAeropuertoOrigen())
                && v.getDestino().equals(envio.getAeropuertoDestino())
                && !v.isCancelado()
                && capacidadesVuelos.getOrDefault(claveVuelo(v), v.getCapacidad()) >= envio.getCantidadMaletas()
                && capacidadesAlmacenes.getOrDefault(v.getDestino(), 0) >= envio.getCantidadMaletas()
            )
            .collect(Collectors.toList());

        // Si hay vuelos directos válidos, USAMOS UNO INMEDIATAMENTE
        if (!vuelosDirectos.isEmpty()) {
            PlanVuelo vueloDirecto = vuelosDirectos.get(random.nextInt(vuelosDirectos.size()));

            long duracion = calcularDuracionMinutos(vueloDirecto, mapaAeropuertos);
            long tiempoEsperaOrigen = calcularTiempoEsperaOrigen(envio, vueloDirecto.getHoraSalida());

            if ((duracion + tiempoEsperaOrigen) <= plazoDisponible) {
                String claveV = claveVuelo(vueloDirecto);
                capacidadesVuelos.put(claveV, capacidadesVuelos.get(claveV) - envio.getCantidadMaletas());
                capacidadesAlmacenes.put(envio.getAeropuertoOrigen(), capacidadesAlmacenes.get(envio.getAeropuertoOrigen()) - envio.getCantidadMaletas());
                capacidadesAlmacenes.put(vueloDirecto.getDestino(), capacidadesAlmacenes.get(vueloDirecto.getDestino()) - envio.getCantidadMaletas());

                long tiempoTotal = tiempoEsperaOrigen + duracion + TIEMPO_RECOJO_DESTINO;

                ruta.setVuelos(new ArrayList<>(List.of(vueloDirecto)));
                ruta.setTiempoTotalMinutos(tiempoTotal);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }
        }

        // ── INTENTO 2: Ruta con escalas ───────────────────────────────────
        List<PlanVuelo> vuelosRuta = new ArrayList<>();
        String aeropuertoActual = envio.getAeropuertoOrigen();
        int escalas = 0;
        long tiempoAcumulado = 0;
        Set<String> aeropuertosVisitados = new HashSet<>();
        aeropuertosVisitados.add(aeropuertoActual);

        while (!aeropuertoActual.equals(envio.getAeropuertoDestino()) && escalas < MAX_ESCALAS) {
            final String actual = aeropuertoActual;
            final List<PlanVuelo> vuelosActuales = vuelosRuta;

            List<PlanVuelo> vuelosDisponibles = todosLosVuelos.stream()
                .filter(v ->
                    v.getOrigen().equals(actual)
                    && !v.isCancelado()
                    && !aeropuertosVisitados.contains(v.getDestino())
                    && capacidadesVuelos.getOrDefault(claveVuelo(v), v.getCapacidad()) >= envio.getCantidadMaletas()
                    && capacidadesAlmacenes.getOrDefault(v.getDestino(), 0) >= envio.getCantidadMaletas()
                    && (vuelosActuales.isEmpty() || cumpleTiempoEscala(vuelosActuales.get(vuelosActuales.size() - 1), v))
                )
                .collect(Collectors.toList());

            if (vuelosDisponibles.isEmpty()) break;

            PlanVuelo vueloElegido = vuelosDisponibles.get(random.nextInt(vuelosDisponibles.size()));
            long duracionVuelo = calcularDuracionMinutos(vueloElegido, mapaAeropuertos);
            long tiempoEspera = vuelosRuta.isEmpty() 
                    ? calcularTiempoEsperaOrigen(envio, vueloElegido.getHoraSalida())
                    : calcularTiempoEspera(vuelosRuta.get(vuelosRuta.size() - 1).getHoraLlegada(), vueloElegido.getHoraSalida());

            long tiempoSegmento = duracionVuelo + tiempoEspera;
            if (tiempoAcumulado + tiempoSegmento > plazoDisponible) break;

            vuelosRuta.add(vueloElegido);
            aeropuertosVisitados.add(vueloElegido.getDestino());
            aeropuertoActual = vueloElegido.getDestino();
            tiempoAcumulado += tiempoSegmento;
            escalas++;
        }

        if (aeropuertoActual.equals(envio.getAeropuertoDestino()) && !vuelosRuta.isEmpty()) {
            capacidadesAlmacenes.put(envio.getAeropuertoOrigen(), capacidadesAlmacenes.get(envio.getAeropuertoOrigen()) - envio.getCantidadMaletas());

            for (PlanVuelo v : vuelosRuta) {
                String claveV = claveVuelo(v);
                capacidadesVuelos.put(claveV, capacidadesVuelos.get(claveV) - envio.getCantidadMaletas());
                capacidadesAlmacenes.put(v.getDestino(), capacidadesAlmacenes.get(v.getDestino()) - envio.getCantidadMaletas());
            }

            ruta.setVuelos(vuelosRuta);
            ruta.setTiempoTotalMinutos(tiempoAcumulado + TIEMPO_RECOJO_DESTINO);
            ruta.setEstado(EstadoRuta.PLANIFICADA);
        } else {
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
            ruta.setEstado(EstadoRuta.SIN_RUTA);
        }

        return ruta;
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalidaVuelo = convertirAMinutos(horaSalidaVuelo);
        if (minSalidaVuelo < minRegistro) minSalidaVuelo += 24 * 60;
        return minSalidaVuelo - minRegistro;
    }

    private boolean cumpleTiempoEscala(PlanVuelo vueloAnterior, PlanVuelo vueloSiguiente) {
        int minLlegada = convertirAMinutos(vueloAnterior.getHoraLlegada());
        int minSalida = convertirAMinutos(vueloSiguiente.getHoraSalida());
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