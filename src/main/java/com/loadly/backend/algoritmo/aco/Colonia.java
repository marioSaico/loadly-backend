package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.algoritmo.genetico.BuscadorRutas;
import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;

@Data
public class Colonia {
    
    private List<Hormiga> hormigas;
    private int numHormigas;
    private FeromenaGrafo feromenaGrafo;
    
    private final BuscadorRutas buscadorRutas = new BuscadorRutas();
    
    // Parámetro ACO: factor de diversidad para construcción de ruta
    private static final double FACTOR_DIVERSIDAD_ACO = 0.15;
    
    public Colonia(int numHormigas, Map<String, Aeropuerto> mapaAeropuertos, 
                   Map<String, List<PlanVuelo>> mapaVuelosPorOrigen) {
        this.numHormigas = numHormigas;
        this.hormigas = new ArrayList<>();
        this.feromenaGrafo = new FeromenaGrafo(mapaAeropuertos, mapaVuelosPorOrigen);
    }
    
    /**
     * Construye soluciones iniciales para cada hormiga usando A*.
     */
    public void construirSoluciones(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes) {
        
        Random random = new Random();
        hormigas.clear();
        
        for (int i = 0; i < numHormigas; i++) {
            // Cada hormiga clona las capacidades para explorar independientemente
            Map<String, Integer> capVuelosClonada = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesClonada = new HashMap<>(capAlmacenes);
            
            List<Ruta> rutasHormiga = new ArrayList<>();
            for (Envio envio : envios) {
                Ruta ruta = buscadorRutas.buscarRuta(
                    envio,
                    vuelos,
                    null,
                    mapaAeropuertos,
                    capVuelosClonada,
                    capAlmacenesClonada,
                    random,
                    FACTOR_DIVERSIDAD_ACO
                );
                rutasHormiga.add(ruta);
            }
            hormigas.add(new Hormiga(rutasHormiga));
        }
    }
    
    public Hormiga getMejorHormiga() {
        return hormigas.stream()
            .max(Comparator.comparingDouble(Hormiga::getFitness))
            .orElse(null);
    }
}
