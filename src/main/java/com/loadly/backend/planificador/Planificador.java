package com.loadly.backend.planificador;

import com.loadly.backend.algoritmo.genetico.AlgoritmoGenetico;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class Planificador {

    private final DataService dataService;
    private final AlgoritmoGenetico algoritmoGenetico;

    // Parámetros de la Planificación Programada Fija
    // Sa: intervalo entre ejecuciones en minutos
    private static final int SA = 5;
    // K: factor de aceleración (K=1 día a día, K=14 simulación 3 días)
    private static final int K = 1;
    // Sc: salto de consumo de datos = K * Sa
    private static final int SC = K * SA;

    public Planificador(DataService dataService,
                         AlgoritmoGenetico algoritmoGenetico) {
        this.dataService = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
    }

    /**
     * Ejecuta una planificación con los envíos pendientes
     * hasta la fechaHoraLimite dada.
     * Este método se llamará cada Sa minutos.
     */
    public Individuo planificar(String fechaHoraLimite) {

        System.out.println("=== Ejecutando planificación ===");
        System.out.println("Consumiendo datos hasta: " + fechaHoraLimite);

        // Obtener datos en memoria
        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService
            .getAeropuertos().stream()
            .collect(Collectors.toMap(
                Aeropuerto::getCodigo, a -> a
            ));

        // Obtener envíos pendientes hasta el instante simulado
        List<Envio> enviosPendientes = dataService
            .obtenerEnviosPendientes(fechaHoraLimite);

        System.out.println("Envíos pendientes: " + enviosPendientes.size());

        if (enviosPendientes.isEmpty()) {
            System.out.println("No hay envíos pendientes.");
            return null;
        }

        // Ejecutar el algoritmo genético
        return algoritmoGenetico.ejecutar(
            enviosPendientes, vuelos, mapaAeropuertos
        );
    }
}