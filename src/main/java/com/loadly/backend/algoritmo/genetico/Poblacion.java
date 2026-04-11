package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class Poblacion {

    private List<Individuo> individuos;
    private int tamanio;
    // Probabilidad de usar vuelo directo si existe (70%)
    private static final double PROB_VUELO_DIRECTO = 0.7;
    // Máximo de escalas permitidas
    private static final int MAX_ESCALAS = 3;
    private static final int TIEMPO_MINIMO_ESCALA = 10;

    public Poblacion(int tamanio) {
        this.tamanio = tamanio;
        this.individuos = new ArrayList<>();
    }

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

        Map<String, Integer> capacidadesDisponibles = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadesDisponibles.put(claveVuelo(v), v.getCapacidad());
        }

        for (Envio envio : envios) {
            Ruta ruta = generarRutaAleatoria(
                envio, vuelos, mapaAeropuertos,
                capacidadesDisponibles, random
            );
            rutas.add(ruta);
        }

        return rutas;
    }

    private Ruta generarRutaAleatoria(
            Envio envio,
            List<PlanVuelo> todosLosVuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capacidadesDisponibles,
            Random random) {

        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setEstado(EstadoRuta.SIN_RUTA);
        ruta.setIndiceVueloActual(0);

        Aeropuerto origen = mapaAeropuertos.get(envio.getAeropuertoOrigen());
        Aeropuerto destino = mapaAeropuertos.get(envio.getAeropuertoDestino());
        long plazoMaximoMinutos = 48 * 60;

        if (origen != null && destino != null) {
            if (origen.getContinente().equals(destino.getContinente())) {
                plazoMaximoMinutos = 24 * 60;
            }
        }

        // Buscar vuelos directos disponibles
        List<PlanVuelo> vuelosDirectos = todosLosVuelos.stream()
            .filter(v -> v.getOrigen().equals(envio.getAeropuertoOrigen())
                && v.getDestino().equals(envio.getAeropuertoDestino())
                && !v.isCancelado()
                && capacidadesDisponibles.getOrDefault(
                    claveVuelo(v), v.getCapacidad()
                ) >= envio.getCantidadMaletas())
            .collect(Collectors.toList());

        // Con probabilidad PROB_VUELO_DIRECTO usar vuelo directo si existe
        if (!vuelosDirectos.isEmpty()
                && random.nextDouble() < PROB_VUELO_DIRECTO) {

            PlanVuelo vueloDirecto = vuelosDirectos
                .get(random.nextInt(vuelosDirectos.size()));

            long duracion = calcularDuracionMinutos(
                vueloDirecto.getHoraSalida(),
                vueloDirecto.getHoraLlegada()
            );

            if (duracion <= plazoMaximoMinutos) {
                String clave = claveVuelo(vueloDirecto);
                capacidadesDisponibles.put(
                    clave,
                    capacidadesDisponibles.getOrDefault(
                        clave, vueloDirecto.getCapacidad()
                    ) - envio.getCantidadMaletas()
                );
                ruta.setVuelos(List.of(vueloDirecto));
                ruta.setTiempoTotalMinutos(duracion);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }
        }

        // Buscar con escalas
        List<PlanVuelo> vuelosRuta = new ArrayList<>();
        String aeropuertoActual = envio.getAeropuertoOrigen();
        int escalas = 0;
        long tiempoAcumulado = 0;
        Set<String> aeropuertosVisitados = new HashSet<>();
        aeropuertosVisitados.add(aeropuertoActual);

        while (!aeropuertoActual.equals(envio.getAeropuertoDestino())
                && escalas < MAX_ESCALAS) {

            final String actual = aeropuertoActual;
            final List<PlanVuelo> vuelosRutaFinal = vuelosRuta;

            List<PlanVuelo> vuelosDisponibles = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(actual)
                    && !v.isCancelado()
                    && capacidadesDisponibles.getOrDefault(
                        claveVuelo(v), v.getCapacidad()
                    ) >= envio.getCantidadMaletas()
                    && !aeropuertosVisitados.contains(v.getDestino())
                    // Verificar tiempo mínimo de escala con el vuelo anterior
                    && (vuelosRutaFinal.isEmpty() || cumpleTiempoEscala(
                        vuelosRutaFinal.get(vuelosRutaFinal.size() - 1), v
                    )))
                .collect(Collectors.toList());

            if (vuelosDisponibles.isEmpty()) break;

            PlanVuelo vueloElegido = vuelosDisponibles
                .get(random.nextInt(vuelosDisponibles.size()));

            long duracion = calcularDuracionMinutos(
                vueloElegido.getHoraSalida(),
                vueloElegido.getHoraLlegada()
            );
            tiempoAcumulado += duracion;

            if (tiempoAcumulado > plazoMaximoMinutos) break;

            vuelosRuta.add(vueloElegido);
            aeropuertosVisitados.add(vueloElegido.getDestino());
            aeropuertoActual = vueloElegido.getDestino();
            escalas++;
        }

        if (aeropuertoActual.equals(envio.getAeropuertoDestino())
                && !vuelosRuta.isEmpty()) {

            for (PlanVuelo v : vuelosRuta) {
                String clave = claveVuelo(v);
                capacidadesDisponibles.put(
                    clave,
                    capacidadesDisponibles.getOrDefault(
                        clave, v.getCapacidad()
                    ) - envio.getCantidadMaletas()
                );
            }

            ruta.setVuelos(vuelosRuta);
            ruta.setTiempoTotalMinutos(tiempoAcumulado);
            ruta.setEstado(EstadoRuta.PLANIFICADA);
        } else {
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
            ruta.setEstado(EstadoRuta.SIN_RUTA);
        }

        return ruta;
    }

    private boolean cumpleTiempoEscala(PlanVuelo vueloAnterior,
                                        PlanVuelo vueloSiguiente) {
        int minLlegada = convertirAMinutos(vueloAnterior.getHoraLlegada());
        int minSalida = convertirAMinutos(vueloSiguiente.getHoraSalida());

        if (minSalida < minLlegada) {
            minSalida += 24 * 60;
        }

        return (minSalida - minLlegada) >= TIEMPO_MINIMO_ESCALA;
    }

    private int convertirAMinutos(String hora) {
        String[] partes = hora.split(":");
        return Integer.parseInt(partes[0]) * 60
             + Integer.parseInt(partes[1]);
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino()
             + "-" + vuelo.getHoraSalida();
    }

    private long calcularDuracionMinutos(String horaSalida,
                                          String horaLlegada) {
        String[] salida = horaSalida.split(":");
        String[] llegada = horaLlegada.split(":");

        int minSalida = Integer.parseInt(salida[0]) * 60
                      + Integer.parseInt(salida[1]);
        int minLlegada = Integer.parseInt(llegada[0]) * 60
                       + Integer.parseInt(llegada[1]);

        if (minLlegada < minSalida) {
            minLlegada += 24 * 60;
        }

        return minLlegada - minSalida;
    }

    public Individuo getMejorIndividuo() {
        return individuos.stream()
            .max(Comparator.comparingDouble(Individuo::getFitness))
            .orElse(null);
    }
}