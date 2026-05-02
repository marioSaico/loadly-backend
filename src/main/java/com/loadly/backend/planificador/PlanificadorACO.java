package com.loadly.backend.planificador;
 
import com.loadly.backend.algoritmo.aco.AlgoritmoACO;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.service.DataService;
import org.springframework.stereotype.Component;
 
import java.util.List;
import java.util.Map;
 
/**
 * Planificador para el algoritmo ACO.
 * Mantiene la misma estructura que Planificador.java (del GA)
 * para que sean intercambiables en el Main al ejecutar escenarios.
 */
@Component("planificadorACO")
public class PlanificadorACO {
 
    private final DataService  dataService;
    private final AlgoritmoACO algoritmoACO;
 
    public PlanificadorACO(DataService dataService, AlgoritmoACO algoritmoACO) {
        this.dataService  = dataService;
        this.algoritmoACO = algoritmoACO;
    }
 
    /**
     * Ejecuta una planificación ACO con los envíos pendientes hasta fechaHoraLimite.
     *
     * @param fechaHoraLimite Límite de lectura de datos (= limiteLecturaDatos del Main).
     * @param numHormigas     Cantidad de hormigas (análogo al tamaño de población del GA).
     * @param tiempoLimiteMs  Tiempo máximo de ejecución del ACO en ms (= Ta × 1000).
     * @return Mejor plan encontrado como Individuo, o null si no hay envíos pendientes.
     */
    public Individuo planificar(String inicioEscenario, String fechaHoraLimite, int numHormigas, long tiempoLimiteMs) {
 
        // 1. Datos estáticos
        Map<String, Aeropuerto>      mapaAeropuertos     = dataService.getMapaAeropuertos();
        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen = dataService.getMapaVuelosPorOrigen();
 
        // 2. Envíos pendientes (nuevos + backlog)
        List<Envio> enviosPendientes = dataService.obtenerEnviosPendientes(inicioEscenario, fechaHoraLimite);
        if (enviosPendientes.isEmpty()) {
            return null; // El Main interpreta null como "sin envíos en esta ventana"
        }
 
        // 3. Capacidades dinámicas actuales
        Map<String, Integer> capDinamicaVuelos    = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer> capDinamicaAlmacenes = dataService.getCapacidadDinamicaAlmacenes();
 
        // 4. Ejecutar ACO
        // NOTA: ya no se pasa la lista de vuelos porque AlgoritmoACO
        // usa mapaVuelosPorOrigen directamente (acceso O(1) por origen)
        Individuo mejorPlan = algoritmoACO.ejecutar(
                enviosPendientes,
                mapaAeropuertos,
                mapaVuelosPorOrigen,
                capDinamicaVuelos,
                capDinamicaAlmacenes,
                numHormigas,
                tiempoLimiteMs
        );
 
        // 5. Confirmar plan y actualizar capacidades (igual que el GA)
        if (mejorPlan != null) {
            // Cambiar 'inicioEscenario' por 'fechaHoraLimite'
            dataService.confirmarPlanYActualizarCapacidades(mejorPlan, fechaHoraLimite);
        }
 
        return mejorPlan;
    }
}