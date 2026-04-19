package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;

@Data
public class Poblacion {

    private List<Individuo> individuos;
    private int tamanio;

    // Factor de diversidad para la inicialización (variación leve: 0% a 20%)
    // Permite que cada individuo de la población inicial tenga rutas ligeramente distintas
    // aunque el A* encuentre el mismo camino óptimo base.
    private static final double FACTOR_DIVERSIDAD_INIT = 0.20;

    private final BuscadorRutas buscadorRutas = new BuscadorRutas();

    public Poblacion(int tamanio) {
        this.tamanio = tamanio;
        this.individuos = new ArrayList<>();
    }

    /**
     * Genera la población inicial con N individuos.
     * Cada individuo contiene una ruta A* por envío, con capacidades respetadas
     * y clonadas por individuo para que cada uno explore su propio espacio.
     */
    public void inicializar(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes) {

        Random random = new Random();

        for (int i = 0; i < tamanio; i++) {
            // Clonar las capacidades para que cada individuo tenga su propia reserva
            Map<String, Integer> capVuelosClonada    = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesClonada = new HashMap<>(capAlmacenes);

            List<Ruta> rutas = new ArrayList<>();
            for (Envio envio : envios) {
                Ruta ruta = buscadorRutas.buscarRuta(
                    envio,
                    vuelos,          // Lista completa de vuelos (modo inicialización)
                    null,            // mapaVuelosPorOrigen no se usa aquí
                    mapaAeropuertos,
                    capVuelosClonada,
                    capAlmacenesClonada,
                    random,
                    FACTOR_DIVERSIDAD_INIT
                );
                rutas.add(ruta);
            }
            individuos.add(new Individuo(rutas));
        }
    }

    public Individuo getMejorIndividuo() {
        return individuos.stream()
            .max(Comparator.comparingDouble(Individuo::getFitness))
            .orElse(null);
    }
}
