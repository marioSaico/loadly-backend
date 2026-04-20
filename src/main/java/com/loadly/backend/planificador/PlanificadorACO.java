package com.loadly.backend.planificador;

import com.loadly.backend.algoritmo.aco.AlgoritmoACO;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("planificadorACO")
public class PlanificadorACO {

    private final DataService dataService;
    private final AlgoritmoACO algoritmoACO;

    public PlanificadorACO(DataService dataService, AlgoritmoACO algoritmoACO) {
        this.dataService = dataService;
        this.algoritmoACO = algoritmoACO;
    }

    /**
     * Ejecuta una planificación ACO con los envíos pendientes.
     * 
     * @param fechaHoraLimite Límite de fecha/hora para cargar envíos
     * @param numHormigas Cantidad de hormigas (población)
     * @param tiempoLimiteMs Tiempo máximo de ejecución del ACO
     * @return Mejor plan encontrado
     */
    public Individuo planificar(String fechaHoraLimite, int numHormigas, long tiempoLimiteMs) {

        // 1. Obtener datos estáticos
        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();

        // 2. Obtener envíos pendientes
        List<Envio> enviosPendientes = dataService.obtenerEnviosPendientes(fechaHoraLimite);

        if (enviosPendientes.isEmpty()) {
            return null;
        }

        // 3. Obtener capacidades dinámicas
        Map<String, Integer> capDinamicaVuelos = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer> capDinamicaAlmacenes = dataService.getCapacidadDinamicaAlmacenes();

        // 4. Ejecutar ACO
        Individuo mejorPlan = algoritmoACO.ejecutar(
                enviosPendientes, vuelos, mapaAeropuertos, mapaVuelosPorOrigen,
                capDinamicaVuelos, capDinamicaAlmacenes,
                numHormigas, tiempoLimiteMs
        );

        // 5. Confirmar plan
        if (mejorPlan != null) {
            dataService.confirmarPlanYActualizarCapacidades(mejorPlan, fechaHoraLimite);
        }

        return mejorPlan;
    }
}
