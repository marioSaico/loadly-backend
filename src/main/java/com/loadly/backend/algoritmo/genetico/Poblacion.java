package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;

@Data
public class Poblacion {

    private List<Individuo> individuos;
    private int tamanio;

    public Poblacion(int tamanio) {
        this.tamanio = tamanio;
        this.individuos = new ArrayList<>();
    }

    // Genera la población inicial aleatoriamente
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

        // Inicializar capacidades disponibles con la capacidad original
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

        // Plazo máximo según continente
        Aeropuerto origen = mapaAeropuertos.get(envio.getAeropuertoOrigen());
        Aeropuerto destino = mapaAeropuertos.get(envio.getAeropuertoDestino());
        long plazoMaximoMinutos = 48 * 60; // por defecto 2 días

        if (origen != null && destino != null) {
            if (origen.getContinente().equals(destino.getContinente())) {
                plazoMaximoMinutos = 24 * 60; // 1 día mismo continente
            }
        }

        // Primero intentar vuelo directo
        List<PlanVuelo> vuelosDirectos = todosLosVuelos.stream()
            .filter(v -> v.getOrigen().equals(envio.getAeropuertoOrigen())
                && v.getDestino().equals(envio.getAeropuertoDestino())
                && !v.isCancelado()
                && capacidadesDisponibles.getOrDefault(
                    claveVuelo(v), v.getCapacidad()
                ) >= envio.getCantidadMaletas())
            .collect(java.util.stream.Collectors.toList());

        if (!vuelosDirectos.isEmpty()) {
            // Hay vuelo directo, elegir uno al azar
            PlanVuelo vueloDirecto = vuelosDirectos
                .get(random.nextInt(vuelosDirectos.size()));

            long duracion = calcularDuracionMinutos(
                vueloDirecto.getHoraSalida(),
                vueloDirecto.getHoraLlegada()
            );

            if (duracion <= plazoMaximoMinutos) {
                // Descontar capacidad
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

        // No hay vuelo directo, buscar con escalas
        List<PlanVuelo> vuelosRuta = new ArrayList<>();
        String aeropuertoActual = envio.getAeropuertoOrigen();
        int maxEscalas = 3;
        int escalas = 0;
        long tiempoAcumulado = 0;
        Set<String> aeropuertosVisitados = new HashSet<>();
        aeropuertosVisitados.add(aeropuertoActual);

        while (!aeropuertoActual.equals(envio.getAeropuertoDestino())
                && escalas < maxEscalas) {

            final String actual = aeropuertoActual;
            List<PlanVuelo> vuelosDisponibles = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(actual)
                    && !v.isCancelado()
                    && capacidadesDisponibles.getOrDefault(
                        claveVuelo(v), v.getCapacidad()
                    ) >= envio.getCantidadMaletas()
                    && !aeropuertosVisitados.contains(v.getDestino()))
                .collect(java.util.stream.Collectors.toList());

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

            // Descontar capacidad de cada vuelo usado
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

    // Clave única para identificar un vuelo
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

        // Si la hora de llegada es menor que la de salida
        // significa que cruza la medianoche
        if (minLlegada < minSalida) {
            minLlegada += 24 * 60;
        }

        return minLlegada - minSalida;
    }

    // Obtener el mejor individuo de la población
    public Individuo getMejorIndividuo() {
        return individuos.stream()
            .max(Comparator.comparingDouble(Individuo::getFitness))
            .orElse(null);
    }
}