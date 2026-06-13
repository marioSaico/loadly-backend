package com.loadly.backend.planificador;

import com.loadly.backend.algoritmo.genetico.AlgoritmoGenetico;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
import com.loadly.backend.model.PlanVuelo;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class Planificador {

    private static final DateTimeFormatter FORMATO_RELOJ = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");

    private final DataService dataService;
    private final AlgoritmoGenetico algoritmoGenetico;

    public Planificador(DataService dataService, AlgoritmoGenetico algoritmoGenetico) {
        this.dataService = dataService;
        this.algoritmoGenetico = algoritmoGenetico;
    }

    

    public Individuo planificar(String inicioEscenario, String fechaHoraActual, String fechaHoraLimite,
                                int tamanoPoblacion, long tiempoLimiteMs, int k) {
        boolean operacionDiaADia = k == 1;

        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();

        List<Envio> enviosPendientes = dataService.obtenerEnviosPendientes(inicioEscenario, fechaHoraActual, fechaHoraLimite,k);

        if (enviosPendientes.isEmpty()) {
            return null;
        }

        Map<String, Integer> capDinamicaVuelos = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer> capDinamicaAlmacenes = dataService.getCapacidadDinamicaAlmacenes();

        Individuo mejorPlan = algoritmoGenetico.ejecutar(
                enviosPendientes,
                vuelos,
                mapaAeropuertos,
                mapaVuelosPorOrigen,
                capDinamicaVuelos,
                capDinamicaAlmacenes,
                tamanoPoblacion,
                tiempoLimiteMs
        );

        if (mejorPlan != null) {
            dataService.confirmarPlanYActualizarCapacidades(mejorPlan, fechaHoraLimite);
            if (operacionDiaADia) {
                dataService.confirmarPlanYActualizarCapacidades(mejorPlan, fechaHoraActual);
                dataService.marcarEnviosPlanificadosEnBD(mejorPlan);
            }
        }

        return mejorPlan;
    }

   

}
