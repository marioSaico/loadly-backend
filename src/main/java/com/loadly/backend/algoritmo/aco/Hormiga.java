package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.model.Ruta;
import lombok.Data;
import java.util.List;

@Data
public class Hormiga {
    
    private List<Ruta> rutas;           // Solución construida por la hormiga
    private double fitness;              // Calidad de la solución
    private double feromonaDepositada;   // Cantidad a depositar en feromonas
    
    public Hormiga(List<Ruta> rutas) {
        this.rutas = rutas;
        this.fitness = 0.0;
        this.feromonaDepositada = 0.0;
    }
}
