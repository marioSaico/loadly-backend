package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.Ruta;
import lombok.Data;
import java.util.List;

@Data
public class Individuo {

    private List<Ruta> rutas;
    private double fitness;

    public Individuo(List<Ruta> rutas) {
        this.rutas = rutas;
        this.fitness = 0.0;
    }
}