package com.loadly.backend.planificador;

import com.loadly.backend.algoritmo.genetico.AlgoritmoGenetico;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class Planificador {

    private final DataService dataService;
    private final AlgoritmoGenetico algoritmoGenetico;

    public Planificador(DataService dataService, AlgoritmoGenetico algoritmoGenetico) {
        this.dataService = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
    }

    /**
     * Ejecuta una planificación con los envíos pendientes hasta la fechaHoraLimite dada.
     */
    public Individuo planificar(String fechaHoraLimite, int tamanoPoblacion, long tiempoLimiteMs) {

        // 1. Obtener datos estáticos
        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();            

        // 2. Obtener envíos pendientes (Nuevos + Rezagados del backlog)
        List<Envio> enviosPendientes = dataService.obtenerEnviosPendientes(fechaHoraLimite);

        if (enviosPendientes.isEmpty()) {
            return null; // El Main interpretará esto como "No hay pedidos nuevos en esta ventana"
        }

        // 3. 💡 CAMBIO CLAVE: Obtener las capacidades reales/dinámicas en este minuto del tiempo
        Map<String, Integer> capDinamicaVuelos = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer> capDinamicaAlmacenes = dataService.getCapacidadDinamicaAlmacenes();

        // 4. Ejecutar el GA pasándole las capacidades actuales para que no sobreescriba vuelos llenos
        Individuo mejorPlan = algoritmoGenetico.ejecutar(
                enviosPendientes, vuelos, mapaAeropuertos, mapaVuelosPorOrigen, 
                capDinamicaVuelos, capDinamicaAlmacenes, // <-- NUEVOS PARÁMETROS
                tamanoPoblacion, tiempoLimiteMs
        );

        // 5. 💡 CONFIRMAR EL PLAN: Bloquear espacios y agendar eventos de liberación
        if (mejorPlan != null) {
            dataService.confirmarPlanYActualizarCapacidades(mejorPlan, fechaHoraLimite);
        }

        return mejorPlan;
    }
}