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
                                int tamanoPoblacion, long tiempoLimiteMs) {
        return planificar(inicioEscenario, fechaHoraActual, fechaHoraLimite, tamanoPoblacion, tiempoLimiteMs, 1);
    }

    public Individuo planificar(String inicioEscenario, String fechaHoraActual, String fechaHoraLimite,
                                int tamanoPoblacion, long tiempoLimiteMs, int k) {
        boolean operacionDiaADia = k == 0;

        if (operacionDiaADia) {
            inicializarDatosSiEsNecesario();
            esperarCierreDeBloque(fechaHoraLimite);
        }

        List<PlanVuelo> vuelos = dataService.getVuelos();
        Map<String, Aeropuerto> mapaAeropuertos = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();

        List<Envio> enviosPendientes = operacionDiaADia
                ? dataService.obtenerEnviosPendientesDesdeBD(fechaHoraActual, fechaHoraLimite)
                : dataService.obtenerEnviosPendientes(inicioEscenario, fechaHoraActual, fechaHoraLimite);

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
                dataService.marcarEnviosPlanificadosEnBD(mejorPlan);
            }
        }

        return mejorPlan;
    }

    private void inicializarDatosSiEsNecesario() {
        if (dataService.getVuelos() == null || dataService.getMapaAeropuertos() == null) {
            dataService.inicializar();
        }
    }

    private void esperarCierreDeBloque(String fechaHoraLimite) {
        LocalDateTime cierreBloque = LocalDateTime.parse(fechaHoraLimite, FORMATO_RELOJ);

        while (LocalDateTime.now().isBefore(cierreBloque)) {
            long esperaMs = Duration.between(LocalDateTime.now(), cierreBloque).toMillis();
            try {
                Thread.sleep(Math.min(Math.max(esperaMs, 1L), 500L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
