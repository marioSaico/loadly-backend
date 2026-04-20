package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;

@Data
public class FeromenaGrafo {
    
    // Matriz de feromonas: (origen-destino-horaSalida) → cantidad de feromona
    private Map<String, Double> feromona;
    
    // Parámetros ACO
    private static final double FEROMONA_INICIAL = 0.1;
    private static final double RHO = 0.1;           // Evaporación (10% por iteración)
    private static final double ALFA = 1.0;          // Importancia de feromona en probabilidad
    private static final double BETA = 2.0;          // Importancia de heurística en probabilidad
    
    private Map<String, Aeropuerto> mapaAeropuertos;
    private Map<String, List<PlanVuelo>> mapaVuelosPorOrigen;
    
    public FeromenaGrafo(Map<String, Aeropuerto> mapaAeropuertos,
                         Map<String, List<PlanVuelo>> mapaVuelosPorOrigen) {
        this.mapaAeropuertos = mapaAeropuertos;
        this.mapaVuelosPorOrigen = mapaVuelosPorOrigen;
        this.feromona = new HashMap<>();
        
        // Inicializar feromonas en todos los vuelos disponibles
        if (mapaVuelosPorOrigen != null) {
            for (List<PlanVuelo> vuelosPorOrigen : mapaVuelosPorOrigen.values()) {
                for (PlanVuelo vuelo : vuelosPorOrigen) {
                    String clave = claveVuelo(vuelo);
                    feromona.put(clave, FEROMONA_INICIAL);
                }
            }
        }
    }
    
    /**
     * Evaporación global de feromonas (10% de todas)
     */
    public void evaporar() {
        for (String clave : feromona.keySet()) {
            double valorActual = feromona.get(clave);
            feromona.put(clave, valorActual * (1.0 - RHO));
        }
    }
    
    /**
     * Depósito de feromona proporcional al fitness de la hormiga.
     * Mejor fitness = más feromona depositada.
     */
    public void depositarFeromona(Hormiga hormiga) {
        // Cantidad de feromona = función del fitness
        double cantidadFeromona = hormiga.getFitness();
        
        for (Ruta ruta : hormiga.getRutas()) {
            if (ruta.getVuelos() != null) {
                for (PlanVuelo vuelo : ruta.getVuelos()) {
                    String clave = claveVuelo(vuelo);
                    double valorActual = feromona.getOrDefault(clave, FEROMONA_INICIAL);
                    feromona.put(clave, valorActual + cantidadFeromona);
                }
            }
        }
    }
    
    /**
     * Calcula probabilidad de seleccionar un vuelo basado en:
     * - Feromona acumulada (ALFA)
     * - Heurística (distancia, tiempo) (BETA)
     */
    public double calcularProbabilidad(PlanVuelo vuelo) {
        String clave = claveVuelo(vuelo);
        double fer = Math.pow(feromona.getOrDefault(clave, FEROMONA_INICIAL), ALFA);
        
        // Heurística: inversa del tiempo (vuelos más rápidos tienen más "atractivo")
        // Asumimos vuelos de ~2 horas = 120 min como referencia
        double tiempoMinutos = extraerMinutosVuelo(vuelo);
        double heuristica = Math.pow(1.0 / (1.0 + tiempoMinutos / 120.0), BETA);
        
        return fer * heuristica;
    }
    
    /**
     * Reinicia feromonas (útil entre iteraciones principales)
     */
    public void reiniciarFeromonas() {
        for (String clave : feromona.keySet()) {
            feromona.put(clave, FEROMONA_INICIAL);
        }
    }
    
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }
    
    private double extraerMinutosVuelo(PlanVuelo vuelo) {
        try {
            String[] partesS = vuelo.getHoraSalida().split(":");
            String[] partesL = vuelo.getHoraLlegada().split(":");
            
            int minSalida = Integer.parseInt(partesS[0]) * 60 + Integer.parseInt(partesS[1]);
            int minLlegada = Integer.parseInt(partesL[0]) * 60 + Integer.parseInt(partesL[1]);
            
            return Math.abs(minLlegada - minSalida);
        } catch (Exception e) {
            return 120.0; // Valor por defecto
        }
    }
}
