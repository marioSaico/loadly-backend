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

    // 💡 Ya no necesitamos SA, K, SC fijos aquí porque el Orquestador (Main) los controla dinámicamente.

    public Planificador(DataService dataService, AlgoritmoGenetico algoritmoGenetico) {
        this.dataService = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
    }

    /**
     * Ejecuta una planificación con los envíos pendientes hasta la fechaHoraLimite dada.
     * Ahora recibe el tiempo límite de ejecución real (Ta) en milisegundos.
     */
    public Individuo planificar(String fechaHoraLimite, int tamanoPoblacion, long tiempoLimiteMs) {

        // Obtener datos en memoria
        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService
            .getAeropuertos().stream()
            .collect(Collectors.toMap(
                Aeropuerto::getCodigo, a -> a
            ));

        // Obtener envíos pendientes hasta el instante simulado (El Sc dictado por el Orquestador)
        List<Envio> enviosPendientes = dataService.obtenerEnviosPendientes(fechaHoraLimite);

        if (enviosPendientes.isEmpty()) {
            return null; // El Main interpretará esto como "No hay pedidos nuevos en esta ventana de tiempo"
        }

        // 💡 CAMBIO CRUCIAL: Pasamos 'tiempoLimiteMs' en lugar de 'maxGeneraciones'
        return algoritmoGenetico.ejecutar(enviosPendientes, vuelos, mapaAeropuertos, tamanoPoblacion, tiempoLimiteMs);
    }
}