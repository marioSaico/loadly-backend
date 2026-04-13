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

    // 🛠️ CORRECCIÓN DE PARÁMETROS DEL PROFESOR
    // Sa: intervalo entre ejecuciones del algoritmo en minutos (Vida real)
    private static final int SA = 5;
    
    // K: factor de aceleración. Si K=14, por cada 5 minutos reales procesamos 70 min.
    private static final int K = 14; 
    
    // Sc: salto de consumo de datos = K * Sa (14 * 5 = 70 minutos)
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
        System.out.println("Salto de consumo (SC): " + SC + " minutos"); // 🛠️ Nuevo log

        // Obtener datos en memoria
        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService
            .getAeropuertos().stream()
            .collect(Collectors.toMap(
                Aeropuerto::getCodigo, a -> a
            ));

        // Obtener envíos pendientes hasta el instante simulado (Ventana de 70 min)
        List<Envio> enviosPendientes = dataService
            .obtenerEnviosPendientes(fechaHoraLimite);

        System.out.println("Envíos pendientes: " + enviosPendientes.size());

        if (enviosPendientes.isEmpty()) {
            System.out.println("No hay envíos pendientes.");
            return null;
        }

        // Ejecutar el algoritmo genético pasándole los datos necesarios
        return algoritmoGenetico.ejecutar(enviosPendientes, vuelos, mapaAeropuertos);
    }
}